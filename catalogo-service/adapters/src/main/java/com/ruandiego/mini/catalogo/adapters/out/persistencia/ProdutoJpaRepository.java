package com.ruandiego.mini.catalogo.adapters.out.persistencia;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/** Repositório Spring Data. Detalhe interno do adapter: os use cases só conhecem o ProdutoRepository. */
interface ProdutoJpaRepository extends JpaRepository<ProdutoJpa, String>, JpaSpecificationExecutor<ProdutoJpa> {

    Optional<ProdutoJpa> findBySlug(String slug);

    boolean existsBySku(String sku);

    boolean existsBySlug(String slug);
}
