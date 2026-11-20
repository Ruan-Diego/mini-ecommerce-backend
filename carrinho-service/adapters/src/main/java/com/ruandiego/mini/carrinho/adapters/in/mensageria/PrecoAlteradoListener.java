package com.ruandiego.mini.carrinho.adapters.in.mensageria;

import com.ruandiego.mini.carrinho.application.port.in.AtualizarPrecoNosCarrinhosUseCase;
import com.ruandiego.mini.carrinho.domain.RegraDeNegocioException;
import com.ruandiego.mini.carrinho.domain.model.ProdutoId;
import com.ruandiego.mini.kernel.Dinheiro;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.time.Instant;

/**
 * Adapter de entrada: traduz a mensagem do RabbitMQ para o use case. Não tem regra nenhuma: só converte
 * texto em tipos do domínio e delega. Idempotência e ordem são do use case.
 *
 * Mensagem malformada (UUID inválido, data ilegível, preço zero) nunca vai melhorar se for reentregue:
 * rejeitá-la sem devolver à fila evita um laço infinito. Com retry e DLQ (Fase 3, item 34) ela irá para a
 * fila morta; falha de infraestrutura (banco fora do ar), ao contrário, deixa a exceção subir e a
 * mensagem é reentregue.
 */
@Component
class PrecoAlteradoListener {

    private static final Logger LOG = LoggerFactory.getLogger(PrecoAlteradoListener.class);

    private final AtualizarPrecoNosCarrinhosUseCase atualizarPreco;

    PrecoAlteradoListener(AtualizarPrecoNosCarrinhosUseCase atualizarPreco) {
        this.atualizarPreco = atualizarPreco;
    }

    @RabbitListener(queues = RabbitConfig.FILA_PRECO_ALTERADO)
    void aoReceber(PrecoAlteradoMensagem mensagem, @Header(name = "x-trace-id", required = false) String traceId) {
        if (traceId != null) {
            MDC.put("traceId", traceId);
        }
        try {
            processar(mensagem);
        } finally {
            MDC.remove("traceId");
        }
    }

    private void processar(PrecoAlteradoMensagem mensagem) {
        ProdutoId produtoId;
        Dinheiro precoNovo;
        Instant ocorridoEm;
        try {
            produtoId = ProdutoId.de(mensagem.produtoId());
            precoNovo = new Dinheiro(mensagem.precoNovo());
            ocorridoEm = Instant.parse(mensagem.ocorridoEm());
        } catch (IllegalArgumentException | NullPointerException | DateTimeException e) {
            throw rejeitar(mensagem, e);
        }
        try {
            atualizarPreco.atualizar(produtoId, precoNovo, ocorridoEm);
        } catch (RegraDeNegocioException e) {
            // Preço novo inválido (zero ou negativo): o domínio recusou, reentregar não resolve.
            throw rejeitar(mensagem, e);
        }
    }

    private static AmqpRejectAndDontRequeueException rejeitar(PrecoAlteradoMensagem mensagem, Exception causa) {
        LOG.warn("PrecoAlterado malformado descartado: {} ({})", mensagem, causa.toString());
        return new AmqpRejectAndDontRequeueException("PrecoAlterado malformado", causa);
    }
}
