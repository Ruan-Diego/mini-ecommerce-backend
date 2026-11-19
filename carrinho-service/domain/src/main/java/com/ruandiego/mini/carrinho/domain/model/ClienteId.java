package com.ruandiego.mini.carrinho.domain.model;

import com.ruandiego.mini.carrinho.domain.RegraDeNegocioException;

/**
 * Identifica o cliente logado. Na Fase 3 vem do claim `sub` do JWT do Keycloak (um UUID);
 * aqui é só um texto não vazio, para o domínio não depender do provedor de identidade.
 */
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
