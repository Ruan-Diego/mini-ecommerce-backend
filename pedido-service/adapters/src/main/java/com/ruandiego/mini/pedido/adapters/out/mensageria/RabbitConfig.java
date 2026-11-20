package com.ruandiego.mini.pedido.adapters.out.mensageria;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topologia que o pedido precisa: a exchange de eventos do pedido (topic, durável). As filas ficam com
 * quem consome (pagamento, estoque, notificações), que as declaram e ligam pelas chaves `pedido.*`.
 * Declarar é idempotente: o RabbitMQ só cria o que ainda não existe. Retry e DLQ são do item 34.
 */
@Configuration(proxyBeanMethods = false)
class RabbitConfig {

    static final String EXCHANGE_PEDIDOS = "pedido.eventos";

    @Bean
    TopicExchange exchangePedidos() {
        return new TopicExchange(EXCHANGE_PEDIDOS, true, false);
    }
}
