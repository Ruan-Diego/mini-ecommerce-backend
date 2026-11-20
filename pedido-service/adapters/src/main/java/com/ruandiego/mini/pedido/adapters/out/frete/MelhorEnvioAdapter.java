package com.ruandiego.mini.pedido.adapters.out.frete;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ruandiego.mini.kernel.Dinheiro;
import com.ruandiego.mini.pedido.application.ServicoIndisponivelException;
import com.ruandiego.mini.pedido.application.port.out.CalculadoraFrete;
import com.ruandiego.mini.pedido.domain.model.Frete;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Adapter do port CalculadoraFrete para a API do Melhor Envio (`POST /api/v2/me/shipment/calculate`).
 * Não é um bean por si só: {@link FreteConfig} o monta com timeout, retry e fallback quando
 * `frete.provedor=melhorenvio`.
 *
 * Como o port só recebe CEP e subtotal, o pacote é um tamanho padrão configurável (a dimensão real dos produtos
 * não existe no catálogo ainda) e o valor declarado é o subtotal. O código da opção é `me-<id do serviço>`, estável
 * entre a cotação e a criação do pedido (que cota de novo e confere o código).
 *
 * Qualquer falha (rede, timeout, 4xx/5xx, resposta sem opção utilizável) vira ServicoIndisponivelException, que o
 * decorator de retry/fallback trata; este adapter não decide o que fazer com a falha.
 */
public class MelhorEnvioAdapter implements CalculadoraFrete {

    public record Pacote(int alturaCm, int larguraCm, int comprimentoCm, BigDecimal pesoKg) {
    }

    private final RestClient http;
    private final String cepOrigem;
    private final Pacote pacote;

    public MelhorEnvioAdapter(String urlBase, String token, String userAgent, String cepOrigem, Pacote pacote,
                              Duration timeoutDeConexao, Duration timeoutDeLeitura) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout((int) timeoutDeConexao.toMillis());
        fabrica.setReadTimeout((int) timeoutDeLeitura.toMillis());
        this.http = RestClient.builder().baseUrl(urlBase).requestFactory(fabrica)
                .defaultHeader("Authorization", "Bearer " + token)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                // O Melhor Envio exige um User-Agent que identifique a aplicação.
                .defaultHeader("User-Agent", userAgent)
                .build();
        this.cepOrigem = cepOrigem;
        this.pacote = pacote;
    }

    @Override
    public List<Frete> cotar(String cep, Dinheiro subtotal) {
        List<OpcaoMe> opcoes;
        try {
            opcoes = http.post().uri("/api/v2/me/shipment/calculate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "from", Map.of("postal_code", cepOrigem),
                            "to", Map.of("postal_code", cep),
                            "package", Map.of(
                                    "height", pacote.alturaCm(),
                                    "width", pacote.larguraCm(),
                                    "length", pacote.comprimentoCm(),
                                    "weight", pacote.pesoKg()),
                            "options", Map.of("insurance_value", subtotal.valor(), "receipt", false, "own_hand", false)))
                    .retrieve().body(new org.springframework.core.ParameterizedTypeReference<List<OpcaoMe>>() {
                    });
        } catch (RestClientException e) {
            throw new ServicoIndisponivelException("Melhor Envio indisponível ou recusou a cotação", e);
        }
        List<Frete> fretes = (opcoes == null ? List.<OpcaoMe>of() : opcoes).stream()
                .filter(o -> o.erro() == null && o.id() != null && o.preco() != null && o.nome() != null)
                .map(o -> new Frete("me-" + o.id(),
                        o.empresa() == null || o.empresa().nome() == null ? o.nome() : o.empresa().nome() + " " + o.nome(),
                        new Dinheiro(o.preco()), Math.max(1, o.prazoDias() == null ? 1 : o.prazoDias())))
                .toList();
        if (fretes.isEmpty()) {
            throw new ServicoIndisponivelException("Melhor Envio não devolveu opção de frete para o CEP", null);
        }
        return fretes;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OpcaoMe(Long id, String name, BigDecimal price, @JsonProperty("delivery_time") Integer deliveryTime,
                   String error, Empresa company) {

        String nome() {
            return name;
        }

        BigDecimal preco() {
            return price;
        }

        Integer prazoDias() {
            return deliveryTime;
        }

        String erro() {
            return error;
        }

        Empresa empresa() {
            return company;
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Empresa(String name) {
            String nome() {
                return name;
            }
        }
    }
}
