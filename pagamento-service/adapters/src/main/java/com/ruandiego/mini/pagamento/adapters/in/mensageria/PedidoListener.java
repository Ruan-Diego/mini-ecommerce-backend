package com.ruandiego.mini.pagamento.adapters.in.mensageria;

import com.ruandiego.mini.pagamento.application.port.in.ProcessarEventoDePedidoUseCase;
import com.ruandiego.mini.pagamento.application.port.in.ProcessarEventoDePedidoUseCase.PedidoCancelado;
import com.ruandiego.mini.pagamento.domain.model.PedidoId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Adapter de entrada para os eventos do pedido: traduz a mensagem para o use case, sem regra nenhuma.
 * Qualquer exceção (inclusive o gateway fora do ar) sobe até o container do listener, que tenta 3 vezes e
 * manda a mensagem para a DLQ.
 */
@Component
class PedidoListener {

    private static final Logger LOG = LoggerFactory.getLogger(PedidoListener.class);

    private final ProcessarEventoDePedidoUseCase processar;

    PedidoListener(ProcessarEventoDePedidoUseCase processar) {
        this.processar = processar;
    }

    @RabbitListener(queues = RabbitConsumoConfig.FILA_PEDIDO)
    void aoReceber(PedidoMensagem mensagem,
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
            if ("pedido.cancelado".equals(chave)) {
                processar.cancelado(new PedidoCancelado(mensagemId, PedidoId.de(mensagem.pedidoId())));
            } else {
                throw new IllegalArgumentException("evento de pedido desconhecido: " + chave);
            }
        } finally {
            MDC.remove("traceId");
        }
    }
}
