package com.ruandiego.mini.pedido.adapters.out.persistencia;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface OutboxJpaRepository extends JpaRepository<OutboxJpa, Long> {

    List<OutboxJpa> findByPublicadaEmIsNullOrderBySeqAsc(Pageable limite);

    Optional<OutboxJpa> findOneById(String id);
}
