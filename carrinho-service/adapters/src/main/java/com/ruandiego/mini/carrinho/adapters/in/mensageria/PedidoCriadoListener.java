package com.ruandiego.mini.carrinho.adapters.in.mensageria;

import com.ruandiego.mini.carrinho.application.port.in.EsvaziarCarrinhoDoClienteUseCase;
import com.ruandiego.mini.carrinho.domain.RegraDeNegocioException;
import com.ruandiego.mini.carrinho.domain.model.ClienteId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Adapter de entrada: o pedido foi criado, então o carrinho do cliente é esvaziado. Só traduz a mensagem e
 * delega. Mensagem sem cliente nunca melhora se reentregue: é rejeitada sem voltar à fila e, esgotado o
 * retry, vai para a DLQ. Falha de infraestrutura (MySQL fora) deixa a exceção subir e a mensagem é reentregue.
 */
@Component
class PedidoCriadoListener {

    private static final Logger LOG = LoggerFactory.getLogger(PedidoCriadoListener.class);

    private final EsvaziarCarrinhoDoClienteUseCase esvaziar;

    PedidoCriadoListener(EsvaziarCarrinhoDoClienteUseCase esvaziar) {
        this.esvaziar = esvaziar;
    }

    @RabbitListener(queues = RabbitConfig.FILA_PEDIDO_CRIADO)
    void aoReceber(PedidoCriadoMensagem mensagem, @Header(name = "x-trace-id", required = false) String traceId) {
        if (traceId != null) {
            MDC.put("traceId", traceId);
        }
        try {
            ClienteId cliente;
            try {
                cliente = new ClienteId(mensagem.clienteId());
            } catch (RegraDeNegocioException e) {
                LOG.warn("PedidoCriado sem cliente válido descartado: {}", mensagem);
                throw new AmqpRejectAndDontRequeueException("PedidoCriado malformado", e);
            }
            esvaziar.esvaziar(cliente);
            LOG.info("Carrinho do cliente esvaziado após o pedido {}", mensagem.pedidoId());
        } finally {
            MDC.remove("traceId");
        }
    }
}
