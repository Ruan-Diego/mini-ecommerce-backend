package com.ruandiego.mini.estoque.adapters.out.persistencia;

import com.ruandiego.mini.estoque.application.port.out.ItemEstoqueRepository;
import com.ruandiego.mini.estoque.domain.model.ItemEstoque;
import com.ruandiego.mini.estoque.domain.model.ProdutoId;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Adapter real do ItemEstoqueRepository: MySQL via JPA. A transação é a do use case (aberta no wiring).
 * A trava de linha só vale dentro dela: é por isso que o saldo lido já vem descontado por quem reservou antes.
 */
@Component
public class ItemEstoqueRepositoryJpa implements ItemEstoqueRepository {

    private final ItemEstoqueJpaRepository jpa;
    private final Clock relogio;

    ItemEstoqueRepositoryJpa(ItemEstoqueJpaRepository jpa, Clock relogio) {
        this.jpa = jpa;
        this.relogio = relogio;
    }

    @Override
    public Optional<ItemEstoque> buscar(ProdutoId produto) {
        return jpa.findById(produto.toString()).map(ItemEstoqueRepositoryJpa::paraDominio);
    }

    @Override
    public List<ItemEstoque> buscarComTrava(Collection<ProdutoId> produtos) {
        if (produtos.isEmpty()) {
            return List.of();
        }
        return jpa.buscarComTrava(produtos.stream().map(ProdutoId::toString).distinct().toList()).stream()
                .map(ItemEstoqueRepositoryJpa::paraDominio).toList();
    }

    @Override
    public void salvar(ItemEstoque item) {
        ItemEstoqueJpa linha = jpa.findById(item.produtoId().toString())
                .orElseGet(() -> new ItemEstoqueJpa(item.produtoId().toString()));
        linha.preencher(item.disponivel(), item.reservado(), relogio.instant());
        jpa.save(linha);
    }

    @Override
    public List<ItemEstoque> listarComDisponivelAbaixoDe(Integer limite) {
        List<ItemEstoqueJpa> linhas = limite == null ? jpa.findAllByOrderByDisponivelAscProdutoIdAsc()
                : jpa.findByDisponivelLessThanOrderByDisponivelAscProdutoIdAsc(limite);
        return linhas.stream().map(ItemEstoqueRepositoryJpa::paraDominio).toList();
    }

    @Override
    public long contarComDisponivelAbaixoDe(int limite) {
        return jpa.countByDisponivelLessThan(limite);
    }

    private static ItemEstoque paraDominio(ItemEstoqueJpa linha) {
        return ItemEstoque.reconstituir(ProdutoId.de(linha.getProdutoId()), linha.getDisponivel(), linha.getReservado());
    }
}
