package com.ruandiego.mini.pagamento.adapters.out.mensageria;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topologia que o pagamento precisa: a exchange de eventos do pagamento (topic, durável). As filas ficam com
 * quem consome (pedido, notificações), que as declaram e ligam pelas chaves `pagamento.*`.
 * Declarar é idempotente: o RabbitMQ só cria o que ainda não existe. Retry e DLQ são do item 34.
 */
@Configuration(proxyBeanMethods = false)
class RabbitConfig {

    static final String EXCHANGE_PAGAMENTOS = "pagamento.eventos";

    @Bean
    TopicExchange exchangePagamentos() {
        return new TopicExchange(EXCHANGE_PAGAMENTOS, true, false);
    }
}
