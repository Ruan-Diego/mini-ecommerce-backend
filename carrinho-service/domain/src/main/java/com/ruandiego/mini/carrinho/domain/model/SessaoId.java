package com.ruandiego.mini.carrinho.domain.model;

import com.ruandiego.mini.carrinho.domain.RegraDeNegocioException;

/**
 * Identifica um visitante (ainda sem login) pelo id de sessão que o navegador gera e reenvia.
 * É texto livre, mas limitado: o valor vira chave no Redis, então não pode ser vazio nem enorme.
 */
public record SessaoId(String valor) {

    private static final int TAMANHO_MAXIMO = 64;

    public SessaoId {
        if (valor == null || valor.isBlank()) {
            throw new RegraDeNegocioException("id de sessão é obrigatório");
        }
        valor = valor.trim();
        if (valor.length() > TAMANHO_MAXIMO || !valor.matches("[A-Za-z0-9_-]+")) {
            throw new RegraDeNegocioException(
                    "id de sessão deve ter até " + TAMANHO_MAXIMO + " letras, números, '-' ou '_'");
        }
    }

    @Override
    public String toString() {
        return valor;
    }
}
