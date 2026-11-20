package com.ruandiego.mini.pagamento.adapters.out.gateway;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ruandiego.mini.kernel.Dinheiro;
import com.ruandiego.mini.pagamento.application.ServicoIndisponivelException;
import com.ruandiego.mini.pagamento.application.port.in.ProcessarEventoDoGatewayUseCase.Evento;
import com.ruandiego.mini.pagamento.application.port.out.GatewayPagamento;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter do port GatewayPagamento para o sandbox do Mercado Pago. Só existe com `pagamento.gateway=mercadopago`;
 * com `fake` (o padrão) quem vale é o GatewayFakeAdapter. O domínio e a application não sabem qual está ligado.
 *
 * Pix: `POST /v1/payments` com `payment_method_id=pix`; o "copia e cola" vem em `point_of_interaction`.
 * Estorno: `POST /v1/payments/{id}/refunds`. O resultado do Pix chega depois, pelo webhook
 * ({@link com.ruandiego.mini.pagamento.adapters.in.web.MercadoPagoWebhookController}).
 *
 * <b>Limite do sandbox (cartão):</b> a API de pagamentos do Mercado Pago só aceita um token de cartão, e o token
 * normal nasce no navegador (MP.js), para o número nunca passar pelo servidor. O port atual recebe o número, então
 * este adapter gera o token no servidor com os dados de teste do sandbox (validade e CVV fixos) e escolhe o
 * resultado pelo nome do titular, como a documentação de testes manda: "APRO" aprova e "OTHE" recusa. Mantemos a regra do
 * fake: último dígito par aprova, ímpar recusa. Para produção o port deve passar a receber o token do navegador;
 * não use este adapter com cartões reais.
 *
 * Falha de rede, timeout ou 5xx viram ServicoIndisponivelException (503 no REST); nada é gravado nesse caso.
 */
@Component
@ConditionalOnProperty(name = "pagamento.gateway", havingValue = "mercadopago")
public class MercadoPagoSandboxAdapter implements GatewayPagamento {

    private static final DateTimeFormatter DATA_MP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSxxx");

    private final RestClient http;
    private final String chavePublica;
    private final String emailPagador;
    private final String urlNotificacao;

    public MercadoPagoSandboxAdapter(
            @Value("${pagamento.mercadopago.url:https://api.mercadopago.com}") String urlBase,
            @Value("${pagamento.mercadopago.access-token}") String tokenDeAcesso,
            @Value("${pagamento.mercadopago.public-key}") String chavePublica,
            @Value("${pagamento.mercadopago.email-pagador:test_user@testuser.com}") String emailPagador,
            @Value("${pagamento.mercadopago.url-notificacao:}") String urlNotificacao) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout((int) Duration.ofSeconds(3).toMillis());
        fabrica.setReadTimeout((int) Duration.ofSeconds(10).toMillis());
        this.http = RestClient.builder().baseUrl(urlBase).requestFactory(fabrica)
                .defaultHeader("Authorization", "Bearer " + tokenDeAcesso)
                .build();
        this.chavePublica = chavePublica;
        this.emailPagador = emailPagador;
        this.urlNotificacao = urlNotificacao;
    }

    @Override
    public ResultadoCartao cobrarCartao(Dinheiro valor, String numeroCartao) {
        try {
            int ultimoDigito = numeroCartao.charAt(numeroCartao.length() - 1) - '0';
            String titular = ultimoDigito % 2 == 0 ? "APRO" : "OTHE";
            String token = gerarToken(numeroCartao, titular);

            PagamentoMp resposta = http.post().uri("/v1/payments")
                    .header("X-Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "transaction_amount", valor.valor(),
                            "token", token,
                            "installments", 1,
                            "payment_method_id", numeroCartao.startsWith("4") ? "visa" : "master",
                            "payer", Map.of("email", emailPagador)))
                    .retrieve().body(PagamentoMp.class);
            exigirCorpo(resposta);
            if ("approved".equals(resposta.status())) {
                return new ResultadoCartao(resposta.id(), true, null);
            }
            // Recusado, ou em análise: o port só sabe aprovar ou recusar, então o que não aprovou é recusa.
            return new ResultadoCartao(resposta.id(), false, "cartão não aprovado pela operadora ("
                    + resposta.status() + (resposta.statusDetail() == null ? "" : ": " + resposta.statusDetail()) + ")");
        } catch (RestClientException e) {
            throw new ServicoIndisponivelException("Mercado Pago indisponível ou recusou a cobrança", e);
        }
    }

    @Override
    public CobrancaPix gerarPix(Dinheiro valor, Instant expiraEm) {
        try {
            Map<String, Object> corpo = new java.util.HashMap<>();
            corpo.put("transaction_amount", valor.valor());
            corpo.put("payment_method_id", "pix");
            corpo.put("payer", Map.of("email", emailPagador));
            corpo.put("date_of_expiration", DATA_MP.format(expiraEm.atOffset(ZoneOffset.UTC)));
            if (!urlNotificacao.isBlank()) {
                corpo.put("notification_url", urlNotificacao);
            }
            PagamentoMp resposta = http.post().uri("/v1/payments")
                    .header("X-Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(corpo)
                    .retrieve().body(PagamentoMp.class);
            exigirCorpo(resposta);
            String codigo = resposta.pointOfInteraction() == null || resposta.pointOfInteraction().transactionData() == null
                    ? null : resposta.pointOfInteraction().transactionData().qrCode();
            if (codigo == null || codigo.isBlank()) {
                throw new ServicoIndisponivelException("Mercado Pago não devolveu o código do Pix", null);
            }
            return new CobrancaPix(resposta.id(), codigo);
        } catch (RestClientException e) {
            throw new ServicoIndisponivelException("Mercado Pago indisponível ou recusou o Pix", e);
        }
    }

    @Override
    public void estornar(String transacaoId) {
        try {
            http.post().uri("/v1/payments/{id}/refunds", transacaoId)
                    .header("X-Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of())
                    .retrieve().toBodilessEntity();
        } catch (RestClientException e) {
            throw new ServicoIndisponivelException("Mercado Pago recusou ou não recebeu o estorno", e);
        }
    }

    /**
     * Pergunta ao Mercado Pago o estado atual do pagamento. O webhook só avisa "algo mudou no pagamento X";
     * quem diz o que mudou é esta consulta, feita com o nosso token, e não o corpo da notificação.
     *
     * @return o evento correspondente, ou vazio se o pagamento ainda está pendente/em análise
     */
    public Optional<Evento> consultarEvento(String transacaoId) {
        try {
            PagamentoMp pagamento = http.get().uri("/v1/payments/{id}", transacaoId).retrieve().body(PagamentoMp.class);
            exigirCorpo(pagamento);
            return eventoDoStatus(pagamento.status());
        } catch (RestClientException e) {
            throw new ServicoIndisponivelException("Mercado Pago indisponível na consulta do pagamento", e);
        }
    }

    static Optional<Evento> eventoDoStatus(String status) {
        if (status == null) {
            return Optional.empty();
        }
        return switch (status) {
            case "approved" -> Optional.of(Evento.APROVADO);
            case "rejected", "cancelled" -> Optional.of(Evento.RECUSADO);
            case "refunded", "charged_back" -> Optional.of(Evento.ESTORNADO);
            default -> Optional.empty();
        };
    }

    private String gerarToken(String numeroCartao, String titular) {
        TokenMp token = http.post().uri(uri -> uri.path("/v1/card_tokens").queryParam("public_key", chavePublica).build())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "card_number", numeroCartao,
                        "security_code", "123",
                        "expiration_month", 11,
                        "expiration_year", 2030,
                        "cardholder", Map.of("name", titular, "identification",
                                Map.of("type", "CPF", "number", "12345678909"))))
                .retrieve().body(TokenMp.class);
        if (token == null || token.id() == null) {
            throw new ServicoIndisponivelException("Mercado Pago não devolveu o token do cartão", null);
        }
        return token.id();
    }

    private static void exigirCorpo(PagamentoMp resposta) {
        if (resposta == null || resposta.id() == null) {
            throw new ServicoIndisponivelException("Mercado Pago respondeu sem corpo", null);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenMp(String id) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PagamentoMp(String id, String status,
                       @JsonProperty("status_detail") String statusDetail,
                       @JsonProperty("point_of_interaction") PontoDeInteracao pointOfInteraction) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        record PontoDeInteracao(@JsonProperty("transaction_data") DadosDaTransacao transactionData) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record DadosDaTransacao(@JsonProperty("qr_code") String qrCode) {
        }
    }
}
