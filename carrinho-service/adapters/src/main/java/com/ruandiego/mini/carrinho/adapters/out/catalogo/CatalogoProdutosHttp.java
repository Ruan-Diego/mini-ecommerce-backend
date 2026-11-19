package com.ruandiego.mini.carrinho.adapters.out.catalogo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ruandiego.mini.carrinho.application.ServicoIndisponivelException;
import com.ruandiego.mini.carrinho.application.port.out.CatalogoProdutos;
import com.ruandiego.mini.carrinho.domain.model.ProdutoId;
import com.ruandiego.mini.kernel.Dinheiro;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

/**
 * Adapter real do port CatalogoProdutos: chama a API HTTP do catalogo-service (`GET /produtos/por-id/{id}`).
 * O carrinho nunca lê o banco do catálogo, e nunca confia em nome ou preço enviados pelo navegador.
 *
 * 404 é "produto não existe ou inativo" (resposta normal, vira Optional vazio). Qualquer outra falha
 * (catálogo fora do ar, timeout, 5xx) vira ServicoIndisponivelException, que o REST traduz para 503.
 */
@Component
public class CatalogoProdutosHttp implements CatalogoProdutos {

    private final RestClient http;

    public CatalogoProdutosHttp(@Value("${carrinho.catalogo.url:http://localhost:8081}") String urlBase) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout((int) Duration.ofSeconds(2).toMillis());
        fabrica.setReadTimeout((int) Duration.ofSeconds(3).toMillis());
        this.http = RestClient.builder().baseUrl(urlBase).requestFactory(fabrica).build();
    }

    @Override
    public Optional<Produto> buscar(ProdutoId id) {
        try {
            ProdutoJson produto = http.get().uri("/produtos/por-id/{id}", id.toString())
                    .retrieve().body(ProdutoJson.class);
            if (produto == null) {
                throw new ServicoIndisponivelException("catálogo respondeu sem corpo", null);
            }
            return Optional.of(new Produto(id, produto.nome(), new Dinheiro(produto.preco())));
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            }
            throw new ServicoIndisponivelException("catálogo recusou a consulta: " + e.getStatusCode(), e);
        } catch (RestClientException e) {
            throw new ServicoIndisponivelException("catálogo indisponível", e);
        }
    }

    /** Só os campos que o carrinho usa; o resto do produto é ignorado. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ProdutoJson(String nome, BigDecimal preco) {
    }
}
