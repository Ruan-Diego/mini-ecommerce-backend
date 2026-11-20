package com.ruandiego.mini.pagamento.adapters.in.web;

import com.ruandiego.mini.pagamento.application.port.in.ProcessarEventoDoGatewayUseCase;
import com.ruandiego.mini.pagamento.application.port.in.ProcessarEventoDoGatewayUseCase.Resultado;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * Webhook do gateway de pagamento. Quem chama é o gateway, não um cliente logado, então não há JWT:
 * a autenticação é um segredo compartilhado no cabeçalho `X-Webhook-Segredo` (`pagamento.webhook.segredo`),
 * comparado em tempo constante. Sem o segredo certo, 401 e nada é processado.
 *
 * Idempotente: o mesmo evento da mesma transação responde 200 sempre, mas só tem efeito na primeira vez
 * (`resultado` diz qual foi o caso). Responder 200 no reenvio é o que faz o gateway parar de reenviar.
 */
@RestController
@ConditionalOnProperty(name = "pagamento.gateway", havingValue = "fake", matchIfMissing = true)
class WebhookController {

    static final String CABECALHO_SEGREDO = "X-Webhook-Segredo";

    private final ProcessarEventoDoGatewayUseCase processar;
    private final byte[] segredo;

    WebhookController(ProcessarEventoDoGatewayUseCase processar,
                      @Value("${pagamento.webhook.segredo}") String segredo) {
        this.processar = processar;
        this.segredo = segredo.getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping("/webhooks/gateway")
    Map<String, String> receber(@RequestHeader(value = CABECALHO_SEGREDO, required = false) String recebido,
                                @Valid @RequestBody EventoGatewayRequisicao corpo) {
        if (recebido == null || !MessageDigest.isEqual(segredo, recebido.getBytes(StandardCharsets.UTF_8))) {
            throw new NaoAutenticadoException();
        }
        Resultado resultado = processar.processar(corpo.transacaoId(), corpo.evento());
        return Map.of("resultado", resultado.name());
    }
}
