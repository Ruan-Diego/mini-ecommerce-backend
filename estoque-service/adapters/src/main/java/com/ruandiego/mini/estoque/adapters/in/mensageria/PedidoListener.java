package com.ruandiego.mini.estoque.adapters.in.mensageria;

import com.ruandiego.mini.estoque.application.port.in.ProcessarEventoDePedidoUseCase;
import com.ruandiego.mini.estoque.application.port.in.ProcessarEventoDePedidoUseCase.PedidoCancelado;
import com.ruandiego.mini.estoque.application.port.in.ProcessarEventoDePedidoUseCase.PedidoCriado;
import com.ruandiego.mini.estoque.application.port.in.ProcessarEventoDePedidoUseCase.PedidoPago;
import com.ruandiego.mini.estoque.domain.model.ItemReservado;
import com.ruandiego.mini.estoque.domain.model.PedidoId;
import com.ruandiego.mini.estoque.domain.model.ProdutoId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Adapter de entrada: traduz a mensagem do RabbitMQ para o use case. Sem regra nenhuma: converte texto em
 * tipos do domínio e delega. Idempotência (pelo `messageId`) e regras são do use case.
 *
 * Qualquer exceção sobe até o container do listener, que tenta a mesma mensagem 3 vezes com espera
 * crescente e, na terceira falha, a manda para a fila morta. Por isso nada é capturado aqui.
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
            PedidoId pedido = PedidoId.de(mensagem.pedidoId());
            switch (chave) {
                case "pedido.criado" -> processar.criado(new PedidoCriado(mensagemId, pedido, itens(mensagem)));
                case "pedido.pago" -> processar.pago(new PedidoPago(mensagemId, pedido));
                case "pedido.cancelado" -> processar.cancelado(new PedidoCancelado(mensagemId, pedido));
                default -> throw new IllegalArgumentException("evento de pedido desconhecido: " + chave);
            }
        } finally {
            MDC.remove("traceId");
        }
    }

    private static List<ItemReservado> itens(PedidoMensagem mensagem) {
        if (mensagem.itens() == null) {
            return List.of();
        }
        return mensagem.itens().stream()
                .map(i -> new ItemReservado(ProdutoId.de(i.produtoId()), i.quantidade()))
                .toList();
    }
}
