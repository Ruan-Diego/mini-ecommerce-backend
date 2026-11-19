package com.ruandiego.mini.carrinho.adapters.in.mensageria;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
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

    static final String EXCHANGE_CATALOGO = "catalogo.eventos";
    static final String CHAVE_PRECO_ALTERADO = "catalogo.preco-alterado";
    static final String FILA_PRECO_ALTERADO = "carrinho.preco-alterado";

    @Bean
    TopicExchange exchangeCatalogo() {
        return new TopicExchange(EXCHANGE_CATALOGO, true, false);
    }

    @Bean
    Queue filaPrecoAlterado() {
        return QueueBuilder.durable(FILA_PRECO_ALTERADO).build();
    }

    @Bean
    Binding ligacaoPrecoAlterado(Queue filaPrecoAlterado, TopicExchange exchangeCatalogo) {
        return BindingBuilder.bind(filaPrecoAlterado).to(exchangeCatalogo).with(CHAVE_PRECO_ALTERADO);
    }

    /** JSON em vez da serialização Java padrão. O tipo vem do parâmetro do listener, não de um cabeçalho do publicador. */
    @Bean
    MessageConverter conversorJson(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
