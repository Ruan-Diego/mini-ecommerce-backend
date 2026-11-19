package com.ruandiego.mini.carrinho.application.memoria;

import com.ruandiego.mini.carrinho.application.port.out.CarrinhoClienteRepository;
import com.ruandiego.mini.carrinho.domain.model.Carrinho;
import com.ruandiego.mini.carrinho.domain.model.ClienteId;
import com.ruandiego.mini.carrinho.domain.model.ProdutoId;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Adapter em memória do CarrinhoClienteRepository, para testes. Guarda cópias, como um banco. */
public class CarrinhoClienteRepositoryEmMemoria implements CarrinhoClienteRepository {

    private final Map<ClienteId, Carrinho> dados = new HashMap<>();

    public void limpar() {
        dados.clear();
    }

    @Override
    public Optional<Carrinho> buscar(ClienteId cliente) {
        return Optional.ofNullable(dados.get(cliente)).map(CarrinhoVisitanteRepositoryEmMemoria::copiar);
    }

    @Override
    public void salvar(ClienteId cliente, Carrinho carrinho) {
        dados.put(cliente, CarrinhoVisitanteRepositoryEmMemoria.copiar(carrinho));
    }

    @Override
    public List<ClienteId> clientesComProduto(ProdutoId produto) {
        return dados.entrySet().stream()
                .filter(e -> e.getValue().itens().stream().anyMatch(i -> i.produtoId().equals(produto)))
                .map(Map.Entry::getKey)
                .toList();
    }
}
