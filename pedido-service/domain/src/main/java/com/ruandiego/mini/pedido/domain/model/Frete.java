package com.ruandiego.mini.pedido.domain.model;

import com.ruandiego.mini.kernel.Dinheiro;
import com.ruandiego.mini.pedido.domain.RegraDeNegocioException;

import java.util.Objects;

/** Opção de frete escolhida, congelada no pedido. `codigo` identifica a opção (ex.: "padrao", "expresso"). */
public record Frete(String codigo, String nome, Dinheiro valor, int prazoDias) {

    public Frete {
        if (codigo == null || codigo.isBlank()) {
            throw new RegraDeNegocioException("opção de frete é obrigatória");
        }
        if (nome == null || nome.isBlank()) {
            throw new RegraDeNegocioException("nome do frete é obrigatório");
        }
        Objects.requireNonNull(valor, "valor");
        if (valor.valor().signum() < 0) {
            throw new RegraDeNegocioException("frete não pode ser negativo");
        }
        if (prazoDias < 1) {
            throw new RegraDeNegocioException("prazo do frete deve ser de pelo menos 1 dia");
        }
    }
}
