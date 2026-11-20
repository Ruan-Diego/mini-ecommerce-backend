package com.ruandiego.mini.pagamento.adapters.out.mensageria;

import com.ruandiego.mini.pagamento.application.port.out.BrokerMensagens;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Adapter real do BrokerMensagens: publica o JSON do evento na exchange `pagamento.eventos` (topic), com a
 * routing key igual ao tipo do evento (ex.: `pagamento.criado`). A mensagem é persistente e só conta como
 * publicada depois da confirmação do broker (publisher confirm); sem confirmação, lança e o relay tenta de novo.
 */
@Component
public class BrokerMensagensRabbit implements BrokerMensagens {

    private static final Logger log = LoggerFactory.getLogger(BrokerMensagensRabbit.class);
    private static final long ESPERA_DA_CONFIRMACAO_MS = 5_000;

    private final RabbitTemplate rabbit;

    BrokerMensagensRabbit(RabbitTemplate rabbit) {
        this.rabbit = rabbit;
    }

    @Override
    public void publicar(String tipo, String payload) {
        Message mensagem = MessageBuilder.withBody(payload.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .build();
        try {
            rabbit.invoke(operacoes -> {
                operacoes.send(RabbitConfig.EXCHANGE_PAGAMENTOS, tipo, mensagem);
                operacoes.waitForConfirmsOrDie(ESPERA_DA_CONFIRMACAO_MS);
                return null;
            });
        } catch (RuntimeException e) {
            log.warn("broker não confirmou o evento {}: {}", tipo, e.getMessage());
            throw e;
        }
    }
}
