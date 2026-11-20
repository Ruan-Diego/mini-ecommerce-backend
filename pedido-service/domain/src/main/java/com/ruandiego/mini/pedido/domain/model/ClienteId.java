package com.ruandiego.mini.pedido.domain.model;

import com.ruandiego.mini.pedido.domain.RegraDeNegocioException;

/** Dono do pedido: o claim `sub` do JWT do Keycloak. Aqui é só um texto não vazio, sem depender do provedor. */
public record ClienteId(String valor) {

    private static final int TAMANHO_MAXIMO = 64;

    public ClienteId {
        if (valor == null || valor.isBlank()) {
            throw new RegraDeNegocioException("id do cliente é obrigatório");
        }
        valor = valor.trim();
        if (valor.length() > TAMANHO_MAXIMO) {
            throw new RegraDeNegocioException("id do cliente deve ter até " + TAMANHO_MAXIMO + " caracteres");
        }
    }

    @Override
    public String toString() {
        return valor;
    }
}
