package com.ruandiego.mini.pedido.application.memoria;

import com.ruandiego.mini.pedido.application.port.out.CatalogoConsulta;
import com.ruandiego.mini.pedido.domain.model.ProdutoId;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Adapter em memória do CatalogoConsulta: só conhece o que for cadastrado com {@code cadastrar}. */
public class CatalogoConsultaEmMemoria implements CatalogoConsulta {

    private final Map<ProdutoId, Produto> dados = new HashMap<>();

    public void cadastrar(Produto produto) {
        dados.put(produto.id(), produto);
    }

    public void limpar() {
        dados.clear();
    }

    @Override
    public Optional<Produto> buscar(ProdutoId id) {
        return Optional.ofNullable(dados.get(id));
    }
}
