package com.ruandiego.mini.carrinho.adapters.out.persistencia;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

interface CarrinhoJpaRepository extends JpaRepository<CarrinhoJpa, String> {

    Optional<CarrinhoJpa> findByClienteId(String clienteId);

    @Query("select distinct c.clienteId from CarrinhoJpa c join c.itens i where i.produtoId = :produtoId")
    List<String> findClienteIdsByProdutoId(@Param("produtoId") String produtoId);
}
