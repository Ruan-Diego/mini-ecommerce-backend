package com.ruandiego.mini.catalogo.adapters.out.persistencia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Repositório Spring Data. Detalhe interno do adapter: os use cases só conhecem o CategoriaRepository. */
interface CategoriaJpaRepository extends JpaRepository<CategoriaJpa, String> {

    Optional<CategoriaJpa> findBySlug(String slug);
}
