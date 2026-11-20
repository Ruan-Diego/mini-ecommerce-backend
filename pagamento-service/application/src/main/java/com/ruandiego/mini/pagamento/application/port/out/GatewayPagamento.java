package com.ruandiego.mini.pagamento.application.port.out;

import com.ruandiego.mini.kernel.Dinheiro;

import java.time.Instant;

/**
 * Port de saída do gateway de pagamento (a operadora). Hoje o adapter é fake (GatewayFakeAdapter); trocar
 * por uma operadora de verdade é escrever outro adapter, sem tocar nos use cases.
 */
public interface GatewayPagamento {

    record ResultadoCartao(String transacaoId, boolean aprovado, String motivoRecusa) {
    }

    record CobrancaPix(String transacaoId, String codigoCopiaECola) {
    }

    /**
     * @throws com.ruandiego.mini.pagamento.application.ServicoIndisponivelException se o gateway não respondeu
     */
    ResultadoCartao cobrarCartao(Dinheiro valor, String numeroCartao);

    CobrancaPix gerarPix(Dinheiro valor, Instant expiraEm);

    void estornar(String transacaoId);
}
