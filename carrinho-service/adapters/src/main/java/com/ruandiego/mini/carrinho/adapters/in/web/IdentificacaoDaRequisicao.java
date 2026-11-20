package com.ruandiego.mini.carrinho.adapters.in.web;

import com.ruandiego.mini.carrinho.application.Identidade;
import com.ruandiego.mini.carrinho.application.NaoAutenticadoException;
import com.ruandiego.mini.carrinho.domain.RegraDeNegocioException;
import com.ruandiego.mini.carrinho.domain.model.ClienteId;
import com.ruandiego.mini.carrinho.domain.model.ProdutoId;
import com.ruandiego.mini.carrinho.domain.model.SessaoId;

import java.security.Principal;
import java.util.function.Supplier;

/**
 * Descobre quem está pedindo.
 *
 * O cliente logado é o {@code sub} de um JWT que o Spring Security já validou (assinatura, emissor, prazo):
 * chega aqui como o {@link Principal} da requisição, sem que o controller saiba o que é JWT. Por isso não existe
 * mais header de cliente: qualquer {@code X-Cliente-Id} mandado por alguém é ignorado.
 * O visitante continua sendo identificado pelo {@code X-Sessao-Id}, um id aleatório que o próprio front gera.
 */
final class IdentificacaoDaRequisicao {

    static final String HEADER_SESSAO = "X-Sessao-Id";

    private IdentificacaoDaRequisicao() {
    }

    /** Visitante (só sessão), cliente (só token) ou cliente recém-logado (os dois, gatilho da união). */
    static Identidade identidade(String sessao, Principal principal) {
        SessaoId sessaoId = vazio(sessao) ? null : converter(() -> new SessaoId(sessao));
        ClienteId clienteId = cliente(principal);
        if (sessaoId == null && clienteId == null) {
            throw new RequisicaoInvalidaException("informe o header " + HEADER_SESSAO
                    + " (visitante) ou um token de acesso (cliente logado)");
        }
        return new Identidade(sessaoId, clienteId);
    }

    /** Operações que só existem para cliente logado (checkout). */
    static ClienteId exigirCliente(Principal principal) {
        ClienteId cliente = cliente(principal);
        if (cliente == null) {
            throw new NaoAutenticadoException("faça login para continuar");
        }
        return cliente;
    }

    private static ClienteId cliente(Principal principal) {
        if (principal == null) {
            return null;
        }
        return converter(() -> new ClienteId(principal.getName()));
    }

    static ProdutoId produtoId(String valor) {
        try {
            return ProdutoId.de(valor);
        } catch (IllegalArgumentException e) {
            throw new RequisicaoInvalidaException("produtoId inválido: " + valor);
        }
    }

    private static boolean vazio(String texto) {
        return texto == null || texto.isBlank();
    }

    private static <T> T converter(Supplier<T> criar) {
        try {
            return criar.get();
        } catch (RegraDeNegocioException e) {
            throw new RequisicaoInvalidaException("identificação inválida: " + e.getMessage());
        }
    }
}
