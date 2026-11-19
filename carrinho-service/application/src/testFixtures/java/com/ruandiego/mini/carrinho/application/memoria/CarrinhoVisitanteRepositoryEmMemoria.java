package com.ruandiego.mini.carrinho.application.memoria;

import com.ruandiego.mini.carrinho.application.port.out.CarrinhoVisitanteRepository;
import com.ruandiego.mini.carrinho.domain.model.Carrinho;
import com.ruandiego.mini.carrinho.domain.model.ProdutoId;
import com.ruandiego.mini.carrinho.domain.model.SessaoId;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Adapter em memória do CarrinhoVisitanteRepository, para testes. Guarda cópias, como o Redis. Sem expiração. */
public class CarrinhoVisitanteRepositoryEmMemoria implements CarrinhoVisitanteRepository {

    private final Map<SessaoId, Carrinho> dados = new HashMap<>();

    public void limpar() {
        dados.clear();
    }

    @Override
    public Optional<Carrinho> buscar(SessaoId sessao) {
        return Optional.ofNullable(dados.get(sessao)).map(CarrinhoVisitanteRepositoryEmMemoria::copiar);
    }

    @Override
    public void salvar(SessaoId sessao, Carrinho carrinho) {
        dados.put(sessao, copiar(carrinho));
    }

    @Override
    public void remover(SessaoId sessao) {
        dados.remove(sessao);
    }

    @Override
    public List<SessaoId> sessoesComProduto(ProdutoId produto) {
        return dados.entrySet().stream()
                .filter(e -> e.getValue().itens().stream().anyMatch(i -> i.produtoId().equals(produto)))
                .map(Map.Entry::getKey)
                .toList();
    }

    static Carrinho copiar(Carrinho c) {
        return Carrinho.reconstituir(c.id(), c.itens(), c.cupom());
    }
}
