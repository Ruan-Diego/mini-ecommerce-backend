package com.ruandiego.mini.pedido.adapters.out.persistencia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface PedidoJpaRepository extends JpaRepository<PedidoJpa, String> {

    List<PedidoJpa> findByClienteIdOrderByCriadoEmDesc(String clienteId);
}
