package br.com.deladopara.checkout.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.deladopara.cart.adapter.persistence.CartRepository;
import br.com.deladopara.checkout.adapter.persistence.CheckoutSnapshotRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CheckoutSnapshotServiceTest {

    @Test
    void refusesCheckoutWhenGuestCartDoesNotExist() {
        var service = new CheckoutSnapshotService(
                Mockito.mock(CartRepository.class),
                Mockito.mock(CheckoutSnapshotRepository.class),
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-09-22T12:00:00Z"), ZoneOffset.UTC));

        assertThatThrownBy(() -> service.start("guest-session")).isInstanceOf(java.util.NoSuchElementException.class);
    }
}
