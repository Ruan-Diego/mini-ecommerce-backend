package com.ruandiego.mini.pagamento.adapters.in.web;

import com.ruandiego.mini.pagamento.domain.model.Pagamento;

import java.math.BigDecimal;
import java.time.Instant;

/** Pagamento como o REST o devolve. Nunca inclui dados de cartão. `pixCodigo` só vem em pagamentos Pix. */
record PagamentoResposta(String id, String pedidoId, String metodo, BigDecimal valor, String status,
                         String transacaoId, String pixCodigo, Instant pixExpiraEm, String motivoRecusa,
                         Instant criadoEm, Instant atualizadoEm) {

    static PagamentoResposta de(Pagamento p) {
        return new PagamentoResposta(p.id().toString(), p.pedidoId().toString(), p.metodo().name(),
                p.valor().valor(), p.status().name(), p.transacaoId(), p.pixCodigo(), p.pixExpiraEm(),
                p.motivoRecusa(), p.criadoEm(), p.atualizadoEm());
    }
}
