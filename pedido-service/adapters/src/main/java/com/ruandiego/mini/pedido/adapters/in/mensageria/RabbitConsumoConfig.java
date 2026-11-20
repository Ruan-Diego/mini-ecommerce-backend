package com.ruandiego.mini.pedido.adapters.in.mensageria;

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
 * Topologia do pedido como consumidor: a fila própria `pedido.pagamento`, ligada à exchange `mini.events`
 * pelas chaves `pagamento.aprovado` e `pagamento.estornado`, e a fila morta (DLQ) dela.
 *
 * A fila tem `x-dead-letter-exchange`: quando o listener esgota as 3 tentativas (retry configurado no
 * application.yml) a mensagem é rejeitada sem voltar à fila e o broker a entrega, pela exchange
 * `mini.events.dlx`, na `pedido.pagamento.dlq`. Lá ela fica à vista no painel do RabbitMQ para uma pessoa
 * inspecionar e, se for o caso, republicar.
 */
@Configuration(proxyBeanMethods = false)
class RabbitConsumoConfig {

    static final String EXCHANGE_EVENTOS = "mini.events";
    static final String EXCHANGE_MORTAS = "mini.events.dlx";
    static final String FILA_PAGAMENTO = "pedido.pagamento";
    static final String FILA_PAGAMENTO_DLQ = "pedido.pagamento.dlq";

    @Bean
    Queue filaPagamento() {
        return QueueBuilder.durable(FILA_PAGAMENTO)
                .deadLetterExchange(EXCHANGE_MORTAS)
                .deadLetterRoutingKey(FILA_PAGAMENTO)
                .build();
    }

    @Bean
    Queue filaPagamentoDlq() {
        return QueueBuilder.durable(FILA_PAGAMENTO_DLQ).build();
    }

    @Bean
    DirectExchange exchangeMortas() {
        return new DirectExchange(EXCHANGE_MORTAS, true, false);
    }

    @Bean
    Binding ligacaoPagamentoAprovado() {
        return new Binding(FILA_PAGAMENTO, Binding.DestinationType.QUEUE, EXCHANGE_EVENTOS, "pagamento.aprovado", null);
    }

    @Bean
    Binding ligacaoPagamentoEstornado() {
        return new Binding(FILA_PAGAMENTO, Binding.DestinationType.QUEUE, EXCHANGE_EVENTOS, "pagamento.estornado", null);
    }

    @Bean
    Binding ligacaoDaFilaMorta(Queue filaPagamentoDlq, DirectExchange exchangeMortas) {
        return BindingBuilder.bind(filaPagamentoDlq).to(exchangeMortas).with(FILA_PAGAMENTO);
    }

    /** JSON em vez da serialização Java padrão. O tipo vem do parâmetro do listener, não de um cabeçalho do publicador. */
    @Bean
    MessageConverter conversorJson(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
