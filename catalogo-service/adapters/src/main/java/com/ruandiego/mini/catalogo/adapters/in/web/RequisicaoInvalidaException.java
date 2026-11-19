package com.ruandiego.mini.catalogo.adapters.in.web;

import java.util.function.Function;

/**
 * Parâmetro que nem chega a ser um valor de domínio (id que não é UUID, ordem desconhecida).
 * Vira 400, diferente da regra de negócio violada, que vira 422.
 */
class RequisicaoInvalidaException extends RuntimeException {

    RequisicaoInvalidaException(String mensagem) {
        super(mensagem);
    }

    static <T> T converter(String nomeCampo, String valor, Function<String, T> conversor) {
        try {
            return conversor.apply(valor);
        } catch (IllegalArgumentException e) {
            throw new RequisicaoInvalidaException(nomeCampo + " inválido: " + valor);
        }
    }
}
