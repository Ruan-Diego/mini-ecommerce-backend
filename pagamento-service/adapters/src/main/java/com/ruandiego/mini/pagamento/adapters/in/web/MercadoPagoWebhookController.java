package com.ruandiego.mini.pagamento.adapters.in.web;

import com.ruandiego.mini.pagamento.adapters.out.gateway.MercadoPagoSandboxAdapter;
import com.ruandiego.mini.pagamento.application.port.in.ProcessarEventoDoGatewayUseCase;
import com.ruandiego.mini.pagamento.application.port.in.ProcessarEventoDoGatewayUseCase.Evento;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

/**
 * Webhook do Mercado Pago, no mesmo caminho do webhook do fake (`/webhooks/gateway`): só um dos dois existe,
 * conforme `pagamento.gateway`. O formato é o do Mercado Pago, que traduzimos para o evento do nosso domínio.
 *
 * Autenticação: o Mercado Pago assina a notificação (cabeçalho `x-signature` = `ts=...,v1=...`), com HMAC-SHA256 de
 * `id:<data.id>;request-id:<x-request-id>;ts:<ts>;` usando o segredo do webhook (`pagamento.mercadopago.webhook-secret`).
 * Sem assinatura válida, 401. Mesmo com ela, o corpo não é confiado: o estado vem de uma consulta ao Mercado Pago
 * (`consultarEvento`). Pagamento ainda pendente responde 200 e é ignorado.
 */
@RestController
@ConditionalOnProperty(name = "pagamento.gateway", havingValue = "mercadopago")
class MercadoPagoWebhookController {

    private final ProcessarEventoDoGatewayUseCase processar;
    private final MercadoPagoSandboxAdapter mercadoPago;
    private final byte[] segredo;

    MercadoPagoWebhookController(ProcessarEventoDoGatewayUseCase processar, MercadoPagoSandboxAdapter mercadoPago,
                                 @Value("${pagamento.mercadopago.webhook-secret}") String segredo) {
        if (segredo.isBlank()) {
            throw new IllegalStateException("pagamento.mercadopago.webhook-secret é obrigatório com pagamento.gateway=mercadopago");
        }
        this.processar = processar;
        this.mercadoPago = mercadoPago;
        this.segredo = segredo.getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping("/webhooks/gateway")
    Map<String, String> receber(@RequestParam(value = "data.id", required = false) String idDoPagamento,
                                @RequestParam(value = "type", required = false) String tipo,
                                @RequestHeader(value = "x-signature", required = false) String assinatura,
                                @RequestHeader(value = "x-request-id", required = false) String idDaRequisicao) {
        if (!assinaturaValida(assinatura, idDoPagamento, idDaRequisicao)) {
            throw new NaoAutenticadoException();
        }
        if (!"payment".equals(tipo)) {
            return Map.of("resultado", "IGNORADO");
        }
        Optional<Evento> evento = mercadoPago.consultarEvento(idDoPagamento);
        if (evento.isEmpty()) {
            return Map.of("resultado", "IGNORADO");
        }
        return Map.of("resultado", processar.processar(idDoPagamento, evento.get()).name());
    }

    private boolean assinaturaValida(String assinatura, String idDoPagamento, String idDaRequisicao) {
        if (assinatura == null || idDoPagamento == null) {
            return false;
        }
        String ts = null;
        String v1 = null;
        for (String parte : assinatura.split(",")) {
            String[] par = parte.trim().split("=", 2);
            if (par.length == 2 && par[0].equals("ts")) {
                ts = par[1];
            } else if (par.length == 2 && par[0].equals("v1")) {
                v1 = par[1];
            }
        }
        if (ts == null || v1 == null) {
            return false;
        }
        String manifesto = "id:" + idDoPagamento.toLowerCase() + ";request-id:" + (idDaRequisicao == null ? "" : idDaRequisicao)
                + ";ts:" + ts + ";";
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(segredo, "HmacSHA256"));
            String esperado = HexFormat.of().formatHex(mac.doFinal(manifesto.getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(esperado.getBytes(StandardCharsets.UTF_8), v1.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
