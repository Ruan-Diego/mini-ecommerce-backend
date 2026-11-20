package com.ruandiego.mini.pedido.application.port.out;

/** Port de saída para o broker (RabbitMQ). `tipo` é a routing key; `payload` já é o JSON do evento. */
public interface BrokerMensagens {

    /** @throws RuntimeException se o broker não confirmou o recebimento: a mensagem fica pendente. */
    void publicar(String tipo, String payload);
}
