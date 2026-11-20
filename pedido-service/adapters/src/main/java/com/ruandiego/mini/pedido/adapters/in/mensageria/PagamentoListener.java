package com.ruandiego.mini.pedido.adapters.in.mensageria;

import com.ruandiego.mini.kernel.Dinheiro;
import com.ruandiego.mini.pedido.application.port.in.ProcessarEventoDePagamentoUseCase;
import com.ruandiego.mini.pedido.application.port.in.ProcessarEventoDePagamentoUseCase.PagamentoAprovado;
import com.ruandiego.mini.pedido.application.port.in.ProcessarEventoDePagamentoUseCase.PagamentoEstornado;
import com.ruandiego.mini.pedido.domain.model.ClienteId;
import com.ruandiego.mini.pedido.domain.model.PedidoId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Adapter de entrada: traduz a mensagem do RabbitMQ para o use case. Não tem regra nenhuma: converte
 * texto em tipos do domínio e delega. Idempotência (pelo `messageId`) e regras (valor, dono, fluxo) são do use case.
 *
 * Qualquer exceção sobe até o container do listener, que tenta a mesma mensagem 3 vezes com espera
 * crescente (retry do application.yml) e, na terceira falha, a manda para a fila morta. Por isso aqui não se
 * captura nada: "mensagem ruim" e "banco fora do ar" seguem o mesmo caminho, e a DLQ é onde uma pessoa decide.
 */
@Component
class PagamentoListener {

    private static final Logger LOG = LoggerFactory.getLogger(PagamentoListener.class);

    private final ProcessarEventoDePagamentoUseCase processar;

    PagamentoListener(ProcessarEventoDePagamentoUseCase processar) {
        this.processar = processar;
    }

    @RabbitListener(queues = RabbitConsumoConfig.FILA_PAGAMENTO)
    void aoReceber(PagamentoMensagem mensagem,
                   @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String chave,
                   @Header(name = AmqpHeaders.MESSAGE_ID, required = false) String messageId,
                   @Header(name = "x-trace-id", required = false) String traceId) {
        if (traceId != null) {
            MDC.put("traceId", traceId);
        }
        try {
            LOG.info("recebido {} (mensagem {})", chave, messageId);
            // Sem messageId não há como ser idempotente: falha e a mensagem vai para a DLQ.
            UUID mensagemId = UUID.fromString(messageId);
            PedidoId pedido = PedidoId.de(mensagem.pedidoId());
            switch (chave) {
                case "pagamento.aprovado" -> processar.aprovado(new PagamentoAprovado(mensagemId, pedido,
                        new ClienteId(mensagem.clienteId()), new Dinheiro(mensagem.valor())));
                case "pagamento.estornado" -> processar.estornado(new PagamentoEstornado(mensagemId, pedido));
                default -> throw new IllegalArgumentException("evento de pagamento desconhecido: " + chave);
            }
        } finally {
            MDC.remove("traceId");
        }
    }
}
