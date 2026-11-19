package com.ruandiego.mini.carrinho.adapters.out.catalogo;

import com.ruandiego.mini.carrinho.application.ServicoIndisponivelException;
import com.ruandiego.mini.carrinho.application.port.out.CatalogoProdutos;
import com.ruandiego.mini.carrinho.domain.model.ProdutoId;
import com.ruandiego.mini.kernel.Dinheiro;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** O adapter HTTP contra um catálogo falso do JDK: prova a URL chamada, o 404 e a falha de rede. */
class CatalogoProdutosHttpTest {

    private static final ProdutoId EXISTE = new ProdutoId(UUID.randomUUID());
    private static final ProdutoId INATIVO = new ProdutoId(UUID.randomUUID());
    private static final ProdutoId QUEBRADO = new ProdutoId(UUID.randomUUID());
    private static HttpServer catalogoFalso;

    @BeforeAll
    static void subir() throws IOException {
        catalogoFalso = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        catalogoFalso.createContext("/produtos/por-id/", troca -> {
            String id = troca.getRequestURI().getPath().substring("/produtos/por-id/".length());
            int status;
            String corpo;
            if (id.equals(EXISTE.toString())) {
                status = 200;
                // Campos a mais (sku, imagens...) precisam ser ignorados.
                corpo = "{\"id\":\"" + id + "\",\"sku\":\"ESF-01\",\"nome\":\"Esfera Chrome\",\"preco\":289.9,"
                        + "\"imagens\":[],\"ativo\":true}";
            } else if (id.equals(INATIVO.toString())) {
                status = 404;
                corpo = "{\"title\":\"Não encontrado\"}";
            } else {
                status = 500;
                corpo = "{}";
            }
            byte[] bytes = corpo.getBytes(StandardCharsets.UTF_8);
            troca.getResponseHeaders().add("Content-Type", "application/json");
            troca.sendResponseHeaders(status, bytes.length);
            troca.getResponseBody().write(bytes);
            troca.close();
        });
        catalogoFalso.start();
    }

    @AfterAll
    static void parar() {
        catalogoFalso.stop(0);
    }

    private CatalogoProdutosHttp adapter() {
        return new CatalogoProdutosHttp("http://localhost:" + catalogoFalso.getAddress().getPort());
    }

    @Test
    void produtoAtivoVoltaComNomeEPrecoDoCatalogo() {
        CatalogoProdutos.Produto produto = adapter().buscar(EXISTE).orElseThrow();

        assertThat(produto.id()).isEqualTo(EXISTE);
        assertThat(produto.nome()).isEqualTo("Esfera Chrome");
        assertThat(produto.preco()).isEqualTo(Dinheiro.de("289.90"));
    }

    @Test
    void produtoInexistenteOuInativoViraOptionalVazio() {
        assertThat(adapter().buscar(INATIVO)).isEmpty();
    }

    @Test
    void erroDoCatalogoViraServicoIndisponivel() {
        assertThatThrownBy(() -> adapter().buscar(QUEBRADO)).isInstanceOf(ServicoIndisponivelException.class);
    }

    @Test
    void catalogoForaDoArViraServicoIndisponivel() {
        // Porta 1 não tem ninguém escutando.
        assertThatThrownBy(() -> new CatalogoProdutosHttp("http://localhost:1").buscar(EXISTE))
                .isInstanceOf(ServicoIndisponivelException.class);
    }
}
