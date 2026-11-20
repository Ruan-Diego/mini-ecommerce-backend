package com.ruandiego.mini.pedido.adapters.in.mensageria;

import com.ruandiego.mini.pedido.application.port.in.ProcessarEventoDeEstoqueUseCase;
import com.ruandiego.mini.pedido.application.port.in.ProcessarEventoDeEstoqueUseCase.EstoqueIndisponivel;
import com.ruandiego.mini.pedido.domain.model.PedidoId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Adapter de entrada para os eventos do estoque: traduz a mensagem para o use case, sem regra nenhuma.
 * Qualquer exceção sobe até o container do listener, que tenta 3 vezes e manda a mensagem para a DLQ
 * (mesmo caminho do PagamentoListener).
 */
@Component
class EstoqueListener {

    private static final Logger LOG = LoggerFactory.getLogger(EstoqueListener.class);

    private final ProcessarEventoDeEstoqueUseCase processar;

    EstoqueListener(ProcessarEventoDeEstoqueUseCase processar) {
        this.processar = processar;
    }

    @RabbitListener(queues = RabbitConsumoConfig.FILA_ESTOQUE)
    void aoReceber(EstoqueMensagem mensagem,
                   @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String chave,
                   @Header(name = AmqpHeaders.MESSAGE_ID, required = false) String messageId,
                   @Header(name = "x-trace-id", required = false) String traceId) {
        if (traceId != null) {
            MDC.put("traceId", traceId);
        }
        try {
            LOG.info("recebido {} (mensagem {})", chave, messageId);
            // Sem messageId não há como ser idempotente: falha e a mensagem vai para a DLQ.
            UUID mensagemId = UUID.fromString(messageId);
            if ("estoque.indisponivel".equals(chave)) {
                processar.indisponivel(new EstoqueIndisponivel(mensagemId, PedidoId.de(mensagem.pedidoId())));
            } else {
                throw new IllegalArgumentException("evento de estoque desconhecido: " + chave);
            }
        } finally {
            MDC.remove("traceId");
        }
    }
}
