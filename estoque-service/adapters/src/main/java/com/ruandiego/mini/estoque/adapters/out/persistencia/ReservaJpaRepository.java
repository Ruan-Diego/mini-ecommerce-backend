package com.ruandiego.mini.estoque.adapters.out.persistencia;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

interface ReservaJpaRepository extends JpaRepository<ReservaJpa, String> {

    @Transactional
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ReservaJpa r where r.pedidoId = :pedidoId")
    Optional<ReservaJpa> buscarComTrava(@Param("pedidoId") String pedidoId);
}
