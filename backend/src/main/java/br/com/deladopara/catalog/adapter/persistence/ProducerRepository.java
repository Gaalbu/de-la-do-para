package br.com.deladopara.catalog.adapter.persistence;

import br.com.deladopara.catalog.domain.Producer;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

public interface ProducerRepository extends Repository<Producer, UUID> {

    <S extends Producer> S save(S producer);

    <S extends Producer> S saveAndFlush(S producer);

    Optional<Producer> findById(UUID id);

    Optional<Producer> findBySlug(String slug);

    boolean existsBySlug(String slug);

    Page<Producer> findAll(Pageable pageable);
}
