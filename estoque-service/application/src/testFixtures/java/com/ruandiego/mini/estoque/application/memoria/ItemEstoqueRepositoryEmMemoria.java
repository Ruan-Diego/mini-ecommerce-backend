package com.ruandiego.mini.estoque.application.memoria;

import com.ruandiego.mini.estoque.application.port.out.ItemEstoqueRepository;
import com.ruandiego.mini.estoque.domain.model.ItemEstoque;
import com.ruandiego.mini.estoque.domain.model.ProdutoId;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Adapter em memória. Guarda cópias, como o banco: quem altera o objeto lido só muda o saldo depois de salvar.
 * Não trava de verdade (os testes que usam este adapter são de uma thread só); a trava real é do adapter JPA.
 */
public class ItemEstoqueRepositoryEmMemoria implements ItemEstoqueRepository {

    private final Map<ProdutoId, ItemEstoque> itens = new HashMap<>();

    @Override
    public Optional<ItemEstoque> buscar(ProdutoId produto) {
        return Optional.ofNullable(itens.get(produto)).map(ItemEstoqueRepositoryEmMemoria::copia);
    }

    @Override
    public List<ItemEstoque> buscarComTrava(Collection<ProdutoId> produtos) {
        return produtos.stream().distinct().sorted()
                .map(itens::get)
                .filter(java.util.Objects::nonNull)
                .map(ItemEstoqueRepositoryEmMemoria::copia)
                .toList();
    }

    @Override
    public void salvar(ItemEstoque item) {
        itens.put(item.produtoId(), copia(item));
    }

    public void limpar() {
        itens.clear();
    }

    private static ItemEstoque copia(ItemEstoque item) {
        return ItemEstoque.reconstituir(item.produtoId(), item.disponivel(), item.reservado());
    }
}
