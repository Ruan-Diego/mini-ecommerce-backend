package com.ruandiego.mini.carrinho.application.memoria;

import com.ruandiego.mini.carrinho.application.port.out.PrecosJaProcessados;
import com.ruandiego.mini.carrinho.domain.model.ProdutoId;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/** Adapter em memória do PrecosJaProcessados, para testes. */
public class PrecosJaProcessadosEmMemoria implements PrecosJaProcessados {

    private final Map<ProdutoId, Instant> ultimos = new HashMap<>();

    public void limpar() {
        ultimos.clear();
    }

    @Override
    public boolean jaProcessado(ProdutoId produtoId, Instant ocorridoEm) {
        Instant ultimo = ultimos.get(produtoId);
        return ultimo != null && !ocorridoEm.isAfter(ultimo);
    }

    @Override
    public void registrar(ProdutoId produtoId, Instant ocorridoEm) {
        ultimos.merge(produtoId, ocorridoEm, (antigo, novo) -> novo.isAfter(antigo) ? novo : antigo);
    }
}
