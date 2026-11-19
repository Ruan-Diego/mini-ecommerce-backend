package com.ruandiego.mini.carrinho.adapters.out.persistencia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface CarrinhoJpaRepository extends JpaRepository<CarrinhoJpa, String> {

    Optional<CarrinhoJpa> findByClienteId(String clienteId);
}
