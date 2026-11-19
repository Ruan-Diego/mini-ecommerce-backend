package com.ruandiego.mini.carrinho.adapters.in.web;

import com.ruandiego.mini.carrinho.application.Identidade;
import com.ruandiego.mini.carrinho.application.NaoAutenticadoException;
import com.ruandiego.mini.carrinho.domain.RegraDeNegocioException;
import com.ruandiego.mini.carrinho.domain.model.ClienteId;
import com.ruandiego.mini.carrinho.domain.model.ProdutoId;
import com.ruandiego.mini.carrinho.domain.model.SessaoId;

/**
 * Descobre quem está pedindo a partir dos headers.
 *
 * PROVISÓRIO: `X-Cliente-Id` é aceito como veio, sem prova nenhuma de que o chamador é aquele cliente.
 * Serve só até a Fase 3, quando o gateway/serviço passa a validar o JWT do Keycloak e o cliente vem do
 * claim `sub`. Só este arquivo muda nessa troca. Enquanto isso, não exponha o serviço fora da máquina local.
 */
final class IdentificacaoDaRequisicao {

    static final String HEADER_SESSAO = "X-Sessao-Id";
    static final String HEADER_CLIENTE = "X-Cliente-Id";

    private IdentificacaoDaRequisicao() {
    }

    /** Visitante (só sessão), cliente (só cliente) ou cliente recém-logado (os dois, gatilho da união). */
    static Identidade identidade(String sessao, String cliente) {
        SessaoId sessaoId = vazio(sessao) ? null : converter(HEADER_SESSAO, () -> new SessaoId(sessao));
        ClienteId clienteId = vazio(cliente) ? null : converter(HEADER_CLIENTE, () -> new ClienteId(cliente));
        if (sessaoId == null && clienteId == null) {
            throw new RequisicaoInvalidaException("informe o header " + HEADER_SESSAO + " (visitante) ou "
                    + HEADER_CLIENTE + " (cliente logado)");
        }
        return new Identidade(sessaoId, clienteId);
    }

    /** Operações que só existem para cliente logado (checkout). */
    static ClienteId exigirCliente(String cliente) {
        if (vazio(cliente)) {
            throw new NaoAutenticadoException("faça login para continuar (header " + HEADER_CLIENTE + ")");
        }
        return converter(HEADER_CLIENTE, () -> new ClienteId(cliente));
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

    private static <T> T converter(String header, java.util.function.Supplier<T> criar) {
        try {
            return criar.get();
        } catch (RegraDeNegocioException e) {
            throw new RequisicaoInvalidaException("header " + header + " inválido: " + e.getMessage());
        }
    }
}
