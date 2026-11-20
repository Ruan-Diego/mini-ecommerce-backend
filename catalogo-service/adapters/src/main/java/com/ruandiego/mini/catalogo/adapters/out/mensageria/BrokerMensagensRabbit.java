package com.ruandiego.mini.catalogo.adapters.out.mensageria;

import com.ruandiego.mini.catalogo.application.port.out.BrokerMensagens;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Adapter real do BrokerMensagens: publica o JSON do evento na exchange `mini.events` (topic), com a
 * routing key igual ao tipo do evento (ex.: `catalogo.preco-alterado`). A mensagem é persistente, leva o id da outbox
 * como `messageId` e o trace id no cabeçalho `x-trace-id`, e só conta como publicada depois da
 * confirmação do broker (publisher confirm); sem confirmação, lança e o relay tenta de novo.
 *
 * Observabilidade: a publicação acontece dentro do contexto de trace gravado na outbox (ContextoDeTrace), e
 * cada evento confirmado soma 1 em `mini.eventos.publicados{tipo}`, a métrica de negócio do Grafana
 * (pedidos criados, pagamentos aprovados e recusados...). Conta-se depois da confirmação, que só acontece
 * para eventos já commitados: rollback não infla o número.
 */
@Component
public class BrokerMensagensRabbit implements BrokerMensagens {

    /** Cabeçalho que carrega o trace id; o consumidor o coloca no log (MDC). */
    public static final String CABECALHO_TRACE_ID = "x-trace-id";

    /** Contador de eventos publicados, com a tag `tipo`. No Prometheus: mini_eventos_publicados_total. */
    public static final String METRICA_EVENTOS = "mini.eventos.publicados";

    private static final Logger log = LoggerFactory.getLogger(BrokerMensagensRabbit.class);
    private static final long ESPERA_DA_CONFIRMACAO_MS = 5_000;

    private final RabbitTemplate rabbit;
    private final MeterRegistry metricas;

    /**
     * @param tipos tipos de evento que este serviço publica (`mini.eventos.tipos`): os contadores nascem com 0
     *              na subida. Sem isso a série só aparece no primeiro evento e o Prometheus não tem o "antes",
     *              então `increase()` daria 0 para o primeiro evento.
     */
    BrokerMensagensRabbit(RabbitTemplate rabbit, ObjectProvider<MeterRegistry> metricas,
                          @Value("${mini.eventos.tipos:}") String[] tipos) {
        this.rabbit = rabbit;
        this.metricas = metricas.getIfAvailable(SimpleMeterRegistry::new);
        for (String tipo : tipos) {
            contador(tipo);
        }
    }

    @Override
    public void publicar(UUID id, String tipo, String payload, String traceId) {
        var construtor = MessageBuilder.withBody(payload.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setMessageId(id.toString());
        if (traceId != null) {
            construtor.setHeader(CABECALHO_TRACE_ID, ContextoDeTrace.traceId(traceId));
        }
        String traceparent = ContextoDeTrace.traceparent(traceId);
        if (traceparent != null) {
            // Com o agent, o span de envio sobrescreve este valor com o dele (filho deste); sem o agent, o
            // consumidor ao menos recebe o trace de quem gravou o evento.
            construtor.setHeader(ContextoDeTrace.CABECALHO_TRACEPARENT, traceparent);
        }
        Message mensagem = construtor.build();
        try (Scope ignorado = ContextoDeTrace.restaurar(traceId)) {
            rabbit.invoke(operacoes -> {
                operacoes.send(RabbitConfig.EXCHANGE_EVENTOS, tipo, mensagem);
                operacoes.waitForConfirmsOrDie(ESPERA_DA_CONFIRMACAO_MS);
                return null;
            });
        } catch (RuntimeException e) {
            log.warn("broker não confirmou o evento {}: {}", tipo, e.getMessage());
            throw e;
        }
        contador(tipo).increment();
    }

    private Counter contador(String tipo) {
        return Counter.builder(METRICA_EVENTOS).description("Eventos de domínio publicados no RabbitMQ")
                .tag("tipo", tipo).register(metricas);
    }
}
