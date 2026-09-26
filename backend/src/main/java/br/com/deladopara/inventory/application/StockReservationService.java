package br.com.deladopara.inventory.application;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reserves stock for a purchase for 15 minutes (D11), all SKUs or nothing. Lots are locked in a stable (sku, lot)
 * order to avoid deadlocks, then allocated first-expiry-first (D69). Commit and release are idempotent and never
 * repeat an inventory movement.
 */
@Service
public class StockReservationService {

    static final Duration HOLD = Duration.ofMinutes(15);
    private static final ZoneId BELEM = ZoneId.of("America/Belem");

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public StockReservationService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Idempotent by reference: a replay returns the existing reservation and moves nothing. */
    @Transactional
    public Reservation reserve(String reference, LocalDate arrivalDate, List<Line> lines) {
        validate(reference, arrivalDate, lines);
        var existing = find(reference);
        if (existing.isPresent()) {
            return existing.get();
        }
        var skuIds = lines.stream().map(Line::skuId).sorted().toList();
        var lots = jdbc.query(
                "SELECT id, sku_id, physical_units - reserved_units AS free_units, expires_on,"
                        + " minimum_shelf_life_days, received_at FROM inventory_lots"
                        + " WHERE sku_id = ANY (?) AND NOT blocked ORDER BY sku_id, id FOR UPDATE",
                (rs, row) -> new Lot(
                        rs.getObject("id", UUID.class),
                        rs.getObject("sku_id", UUID.class),
                        rs.getInt("free_units"),
                        rs.getObject("expires_on", LocalDate.class),
                        (Integer) rs.getObject("minimum_shelf_life_days"),
                        rs.getTimestamp("received_at").toInstant()),
                (Object) skuIds.toArray(UUID[]::new));
        var today = LocalDate.ofInstant(clock.instant(), BELEM);
        Map<UUID, List<Lot>> eligible = lots.stream()
                .filter(lot -> lot.freeUnits() > 0 && lot.eligible(today, arrivalDate))
                .sorted(FEFO)
                .collect(Collectors.groupingBy(Lot::skuId));
        var allocations = new ArrayList<Allocation>();
        for (var line : lines.stream().sorted(Comparator.comparing(Line::skuId)).toList()) {
            var missing = line.quantity();
            for (var lot : eligible.getOrDefault(line.skuId(), List.of())) {
                if (missing == 0) {
                    break;
                }
                var units = Math.min(missing, lot.freeUnits());
                allocations.add(new Allocation(lot.id(), line.skuId(), units));
                missing -= units;
            }
            if (missing > 0) {
                throw new InsufficientStockException(line.skuId());
            }
        }
        var now = clock.instant();
        var id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO inventory_reservation (id, reference, status, created_at, expires_at, updated_at)"
                        + " VALUES (?, ?, 'ACTIVE', ?, ?, ?)",
                id,
                reference,
                Timestamp.from(now),
                Timestamp.from(now.plus(HOLD)),
                Timestamp.from(now));
        for (var allocation : allocations) {
            jdbc.update(
                    "UPDATE inventory_lots SET reserved_units = reserved_units + ?, version = version + 1,"
                            + " updated_at = ? WHERE id = ?",
                    allocation.units(),
                    Timestamp.from(now),
                    allocation.lotId());
            jdbc.update(
                    "INSERT INTO inventory_reservation_line (reservation_id, lot_id, sku_id, units) VALUES (?, ?, ?, ?)",
                    id,
                    allocation.lotId(),
                    allocation.skuId(),
                    allocation.units());
        }
        movements(id, "RESERVATION", -1, allocations, now);
        return new Reservation(id, reference, Status.ACTIVE, now.plus(HOLD), List.copyOf(allocations));
    }

    /** Turns an active, unexpired reservation into committed stock; repeating it is a no-op. */
    @Transactional
    public Reservation commit(String reference) {
        var reservation = lock(reference);
        if (reservation.status() == Status.COMMITTED) {
            return reservation;
        }
        if (reservation.status() == Status.RELEASED || !clock.instant().isBefore(reservation.expiresAt())) {
            throw new ReservationNotActiveException(reference);
        }
        setStatus(reservation.id(), Status.COMMITTED);
        return reservation.with(Status.COMMITTED);
    }

    /** Gives an active reservation's units back once; committed and released reservations are left untouched. */
    @Transactional
    public Reservation release(String reference) {
        var reservation = lock(reference);
        if (reservation.status() != Status.ACTIVE) {
            return reservation;
        }
        var now = clock.instant();
        var ordered = reservation.allocations().stream()
                .sorted(Comparator.comparing(Allocation::skuId).thenComparing(Allocation::lotId))
                .toList();
        for (var allocation : ordered) {
            jdbc.update(
                    "UPDATE inventory_lots SET reserved_units = reserved_units - ?, version = version + 1,"
                            + " updated_at = ? WHERE id = ?",
                    allocation.units(),
                    Timestamp.from(now),
                    allocation.lotId());
        }
        movements(reservation.id(), "RELEASE", 1, ordered, now);
        setStatus(reservation.id(), Status.RELEASED);
        return reservation.with(Status.RELEASED);
    }

    public Optional<Reservation> find(String reference) {
        return load("SELECT * FROM inventory_reservation WHERE reference = ?", reference);
    }

    private Reservation lock(String reference) {
        return load("SELECT * FROM inventory_reservation WHERE reference = ? FOR UPDATE", reference)
                .orElseThrow(() -> new ReservationNotActiveException(reference));
    }

    private Optional<Reservation> load(String sql, String reference) {
        return jdbc
                .query(
                        sql,
                        (rs, row) -> new Reservation(
                                rs.getObject("id", UUID.class),
                                rs.getString("reference"),
                                Status.valueOf(rs.getString("status")),
                                rs.getTimestamp("expires_at").toInstant(),
                                List.of()),
                        reference)
                .stream()
                .findFirst()
                .map(reservation -> new Reservation(
                        reservation.id(),
                        reservation.reference(),
                        reservation.status(),
                        reservation.expiresAt(),
                        jdbc.query(
                                "SELECT lot_id, sku_id, units FROM inventory_reservation_line WHERE reservation_id = ?",
                                (rs, row) -> new Allocation(
                                        rs.getObject("lot_id", UUID.class),
                                        rs.getObject("sku_id", UUID.class),
                                        rs.getInt("units")),
                                reservation.id())));
    }

    private void setStatus(UUID id, Status status) {
        jdbc.update(
                "UPDATE inventory_reservation SET status = ?, updated_at = ? WHERE id = ?",
                status.name(),
                Timestamp.from(clock.instant()),
                id);
    }

    /** One movement per SKU, keyed so that the same step for the same reservation can never be written twice. */
    private void movements(UUID reservationId, String type, int sign, List<Allocation> allocations, Instant now) {
        allocations.stream()
                .collect(Collectors.groupingBy(
                        Allocation::skuId, TreeMap::new, Collectors.summingInt(Allocation::units)))
                .forEach((skuId, units) -> jdbc.update(
                        "INSERT INTO inventory_movements (id, sku_id, movement_type, units_delta, idempotency_key,"
                                + " reason, occurred_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                        UUID.randomUUID(),
                        skuId,
                        type,
                        sign * units,
                        type.toLowerCase(Locale.ROOT) + ":" + reservationId + ":" + skuId,
                        "reserva de compra",
                        Timestamp.from(now)));
    }

    private static void validate(String reference, LocalDate arrivalDate, List<Line> lines) {
        if (reference == null || reference.isBlank() || arrivalDate == null || lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("Incomplete reservation request");
        }
        if (lines.stream().anyMatch(line -> line.quantity() <= 0)
                || lines.stream().map(Line::skuId).distinct().count() != lines.size()) {
            throw new IllegalArgumentException("Reservation lines must be positive and unique per SKU");
        }
    }

    private static final Comparator<Lot> FEFO = Comparator.comparing(
                    Lot::expiresOn, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Lot::receivedAt)
            .thenComparing(Lot::id);

    private record Lot(
            UUID id, UUID skuId, int freeUnits, LocalDate expiresOn, Integer minimumShelfLifeDays, Instant receivedAt) {

        boolean eligible(LocalDate today, LocalDate arrivalDate) {
            if (expiresOn == null) {
                return true;
            }
            return !expiresOn.isBefore(today)
                    && ChronoUnit.DAYS.between(arrivalDate, expiresOn) >= minimumShelfLifeDays;
        }
    }

    public record Line(UUID skuId, int quantity) {}

    public record Allocation(UUID lotId, UUID skuId, int units) {}

    public record Reservation(
            UUID id, String reference, Status status, Instant expiresAt, List<Allocation> allocations) {

        Reservation with(Status newStatus) {
            return new Reservation(id, reference, newStatus, expiresAt, allocations);
        }
    }

    public enum Status {
        ACTIVE,
        COMMITTED,
        RELEASED
    }

    public static class InsufficientStockException extends RuntimeException {

        private final UUID skuId;

        public InsufficientStockException(UUID skuId) {
            super("Insufficient stock for SKU " + skuId);
            this.skuId = skuId;
        }

        public UUID skuId() {
            return skuId;
        }
    }

    public static class ReservationNotActiveException extends RuntimeException {

        public ReservationNotActiveException(String reference) {
            super("Reservation is not active: " + reference);
        }
    }
}
