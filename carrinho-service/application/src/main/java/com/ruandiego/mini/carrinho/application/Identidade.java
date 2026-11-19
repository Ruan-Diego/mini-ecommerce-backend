package com.ruandiego.mini.carrinho.application;

import com.ruandiego.mini.carrinho.domain.model.ClienteId;
import com.ruandiego.mini.carrinho.domain.model.SessaoId;

/**
 * Quem está pedindo: um visitante (só sessão), um cliente logado (só cliente) ou um cliente que
 * acabou de logar e ainda manda a sessão do tempo de visitante (os dois: é o gatilho da união).
 */
public record Identidade(SessaoId sessao, ClienteId cliente) {

    public Identidade {
        if (sessao == null && cliente == null) {
            throw new IllegalArgumentException("informe a sessão ou o cliente");
        }
    }

    public static Identidade visitante(SessaoId sessao) {
        return new Identidade(sessao, null);
    }

    public static Identidade cliente(ClienteId cliente) {
        return new Identidade(null, cliente);
    }

    public boolean autenticada() {
        return cliente != null;
    }
}
