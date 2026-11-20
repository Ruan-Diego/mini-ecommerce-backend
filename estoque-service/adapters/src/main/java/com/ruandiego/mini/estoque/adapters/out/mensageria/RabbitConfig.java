package com.ruandiego.mini.estoque.adapters.out.mensageria;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declara a exchange única de eventos do projeto, `mini.events` (topic, durável). Todo serviço publica
 * nela, com a routing key igual ao tipo do evento (`catalogo.preco-alterado`, `pedido.criado`,
 * `pagamento.aprovado`...). Cada consumidor tem a sua fila (e a sua fila morta), declaradas por ele.
 * Declarar é idempotente: o RabbitMQ só cria o que ainda não existe.
 */
@Configuration(proxyBeanMethods = false)
class RabbitConfig {

    static final String EXCHANGE_EVENTOS = "mini.events";

    @Bean
    TopicExchange exchangeEventos() {
        return new TopicExchange(EXCHANGE_EVENTOS, true, false);
    }
}
