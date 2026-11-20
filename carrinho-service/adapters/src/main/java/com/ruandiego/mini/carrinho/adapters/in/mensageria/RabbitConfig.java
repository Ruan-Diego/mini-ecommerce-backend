package com.ruandiego.mini.carrinho.adapters.in.mensageria;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topologia que o carrinho precisa: a exchange de eventos do catálogo (topic), a fila própria do carrinho
 * e a ligação entre elas pela chave `catalogo.preco-alterado` (o `tipo` do evento no domínio do catálogo).
 * Cada serviço tem a sua fila: o carrinho consome as mensagens dele sem tirar nada de outros consumidores.
 * Declarar é idempotente: o RabbitMQ só cria o que ainda não existe.
 */
@Configuration(proxyBeanMethods = false)
class RabbitConfig {

    static final String EXCHANGE_CATALOGO = "mini.events";
    static final String CHAVE_PRECO_ALTERADO = "catalogo.preco-alterado";
    static final String FILA_PRECO_ALTERADO = "carrinho.preco-alterado";
    static final String EXCHANGE_MORTAS = "mini.events.dlx";
    static final String FILA_PRECO_ALTERADO_DLQ = "carrinho.preco-alterado.dlq";

    @Bean
    TopicExchange exchangeCatalogo() {
        return new TopicExchange(EXCHANGE_CATALOGO, true, false);
    }

    /**
     * Com `x-dead-letter-exchange`: quando o listener esgota as 3 tentativas (retry do application.yml), o broker
     * entrega a mensagem, pela exchange `mini.events.dlx`, na fila morta `carrinho.preco-alterado.dlq`.
     * Atenção: mudar os argumentos de uma fila que já existe no broker dá PRECONDITION_FAILED; num broker local
     * antigo, apague a fila `carrinho.preco-alterado` (ou o volume do RabbitMQ) uma vez.
     */
    @Bean
    Queue filaPrecoAlterado() {
        return QueueBuilder.durable(FILA_PRECO_ALTERADO)
                .deadLetterExchange(EXCHANGE_MORTAS)
                .deadLetterRoutingKey(FILA_PRECO_ALTERADO)
                .build();
    }

    @Bean
    Queue filaPrecoAlteradoDlq() {
        return QueueBuilder.durable(FILA_PRECO_ALTERADO_DLQ).build();
    }

    @Bean
    DirectExchange exchangeMortas() {
        return new DirectExchange(EXCHANGE_MORTAS, true, false);
    }

    @Bean
    Binding ligacaoPrecoAlterado(Queue filaPrecoAlterado, TopicExchange exchangeCatalogo) {
        return BindingBuilder.bind(filaPrecoAlterado).to(exchangeCatalogo).with(CHAVE_PRECO_ALTERADO);
    }

    @Bean
    Binding ligacaoDaFilaMorta(Queue filaPrecoAlteradoDlq, DirectExchange exchangeMortas) {
        return BindingBuilder.bind(filaPrecoAlteradoDlq).to(exchangeMortas).with(FILA_PRECO_ALTERADO);
    }

    /** JSON em vez da serialização Java padrão. O tipo vem do parâmetro do listener, não de um cabeçalho do publicador. */
    @Bean
    MessageConverter conversorJson(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
