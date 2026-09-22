package br.com.deladopara.catalog.adapter.persistence;

import br.com.deladopara.catalog.domain.Product;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

public interface ProductRepository extends Repository<Product, UUID> {

    <S extends Product> S save(S product);

    <S extends Product> S saveAndFlush(S product);

    Optional<Product> findById(UUID id);

    Optional<Product> findBySlug(String slug);

    boolean existsBySlug(String slug);

    Page<Product> findAll(Pageable pageable);
}
