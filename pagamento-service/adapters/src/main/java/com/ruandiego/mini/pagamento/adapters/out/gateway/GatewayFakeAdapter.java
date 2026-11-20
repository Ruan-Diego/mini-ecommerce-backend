package com.ruandiego.mini.pagamento.adapters.out.gateway;

import com.ruandiego.mini.kernel.Dinheiro;
import com.ruandiego.mini.pagamento.application.port.out.GatewayPagamento;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Adapter fake do port GatewayPagamento, para estudo e testes ponta a ponta: não fala com operadora nenhuma.
 *
 * Regra do cartão: o último dígito decide. Par aprova, ímpar recusa (ex.: ...2 aprova, ...3 recusa).
 * O Pix só devolve um código "copia e cola" de mentira; quem "paga" é o webhook, chamado à mão ou pelo E2E.
 * O número do cartão é lido para decidir e descartado: não é guardado nem registrado em log.
 */
@Component
public class GatewayFakeAdapter implements GatewayPagamento {

    @Override
    public ResultadoCartao cobrarCartao(Dinheiro valor, String numeroCartao) {
        String transacaoId = novaTransacao();
        int ultimoDigito = numeroCartao.charAt(numeroCartao.length() - 1) - '0';
        return ultimoDigito % 2 == 0
                ? new ResultadoCartao(transacaoId, true, null)
                : new ResultadoCartao(transacaoId, false, "cartão recusado pela operadora");
    }

    @Override
    public CobrancaPix gerarPix(Dinheiro valor, Instant expiraEm) {
        String transacaoId = novaTransacao();
        String codigo = "00020126FAKE" + transacaoId.replace("-", "").toUpperCase() + "5204000053039865406"
                + valor.valor().toPlainString().replace(".", "") + "6304";
        return new CobrancaPix(transacaoId, codigo);
    }

    @Override
    public void estornar(String transacaoId) {
        // O gateway fake aceita todo estorno.
    }

    private static String novaTransacao() {
        return "fake-" + UUID.randomUUID();
    }
}
