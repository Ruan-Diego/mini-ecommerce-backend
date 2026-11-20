package com.ruandiego.mini.pagamento.adapters.in.mensageria;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topologia do pagamento como consumidor: a fila própria `pagamento.pedido`, ligada à exchange `mini.events`
 * pela chave `pedido.cancelado`, e a fila morta (DLQ) dela. Como nas outras filas do projeto, esgotadas as 3
 * tentativas (retry do application.yml) a mensagem vai para `pagamento.pedido.dlq` pela exchange `mini.events.dlx`.
 */
@Configuration(proxyBeanMethods = false)
class RabbitConsumoConfig {

    static final String EXCHANGE_EVENTOS = "mini.events";
    static final String EXCHANGE_MORTAS = "mini.events.dlx";
    static final String FILA_PEDIDO = "pagamento.pedido";
    static final String FILA_PEDIDO_DLQ = "pagamento.pedido.dlq";

    @Bean
    Queue filaPedido() {
        return QueueBuilder.durable(FILA_PEDIDO)
                .deadLetterExchange(EXCHANGE_MORTAS)
                .deadLetterRoutingKey(FILA_PEDIDO)
                .build();
    }

    @Bean
    Queue filaPedidoDlq() {
        return QueueBuilder.durable(FILA_PEDIDO_DLQ).build();
    }

    @Bean
    DirectExchange exchangeMortas() {
        return new DirectExchange(EXCHANGE_MORTAS, true, false);
    }

    @Bean
    Binding ligacaoPedidoCancelado() {
        return new Binding(FILA_PEDIDO, Binding.DestinationType.QUEUE, EXCHANGE_EVENTOS, "pedido.cancelado", null);
    }

    @Bean
    Binding ligacaoDaFilaMorta(Queue filaPedidoDlq, DirectExchange exchangeMortas) {
        return BindingBuilder.bind(filaPedidoDlq).to(exchangeMortas).with(FILA_PEDIDO);
    }

    /** JSON em vez da serialização Java padrão. O tipo vem do parâmetro do listener, não de um cabeçalho do publicador. */
    @Bean
    MessageConverter conversorJson(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
