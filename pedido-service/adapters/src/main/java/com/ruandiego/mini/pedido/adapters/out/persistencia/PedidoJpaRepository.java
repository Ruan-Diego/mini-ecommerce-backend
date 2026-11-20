package com.ruandiego.mini.pedido.adapters.out.persistencia;

import com.ruandiego.mini.pedido.domain.model.StatusPedido;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

interface PedidoJpaRepository extends JpaRepository<PedidoJpa, String> {

    List<PedidoJpa> findByClienteIdOrderByCriadoEmDesc(String clienteId);

    List<PedidoJpa> findByStatusAndCriadoEmLessThanEqualOrderByCriadoEmAsc(StatusPedido status, Instant criadosAte,
                                                                          Pageable limite);
}
