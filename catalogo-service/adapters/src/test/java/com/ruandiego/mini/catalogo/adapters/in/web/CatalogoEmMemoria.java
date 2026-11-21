package com.ruandiego.mini.catalogo.adapters.in.web;

import com.ruandiego.mini.catalogo.application.memoria.CategoriaRepositoryEmMemoria;
import com.ruandiego.mini.catalogo.application.memoria.ProdutoRepositoryEmMemoria;
import com.ruandiego.mini.catalogo.application.memoria.PublicadorEventosEmMemoria;
import com.ruandiego.mini.catalogo.application.memoria.ArmazenamentoImagensEmMemoria;
import com.ruandiego.mini.catalogo.application.service.AdministracaoProdutosService;
import com.ruandiego.mini.catalogo.application.service.ImagensProdutoService;
import com.ruandiego.mini.catalogo.application.service.ConsultaCatalogoService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * Para os testes de controller: os services de verdade sobre os adapters em memória, sem banco e sem mocks.
 * Assim o teste exercita o caminho HTTP → controller → use case → regra de domínio → resposta.
 */
@TestConfiguration(proxyBeanMethods = false)
class CatalogoEmMemoria {

    @Bean
    ProdutoRepositoryEmMemoria produtos() {
        return new ProdutoRepositoryEmMemoria();
    }

    @Bean
    CategoriaRepositoryEmMemoria categorias() {
        return new CategoriaRepositoryEmMemoria();
    }

    @Bean
    PublicadorEventosEmMemoria publicador() {
        return new PublicadorEventosEmMemoria();
    }

    @Bean
    ConsultaCatalogoService consulta(ProdutoRepositoryEmMemoria produtos, CategoriaRepositoryEmMemoria categorias) {
        return new ConsultaCatalogoService(produtos, categorias);
    }

    @Bean
    AdministracaoProdutosService administracao(ProdutoRepositoryEmMemoria produtos,
                                               CategoriaRepositoryEmMemoria categorias,
                                               PublicadorEventosEmMemoria publicador) {
        return new AdministracaoProdutosService(produtos, categorias, publicador,
                Clock.fixed(Instant.parse("2026-11-19T12:00:00Z"), ZoneOffset.UTC));
    }

    @Bean
    ArmazenamentoImagensEmMemoria armazenamento() {
        return new ArmazenamentoImagensEmMemoria();
    }

    @Bean
    ImagensProdutoService imagensProduto(ProdutoRepositoryEmMemoria produtos, ArmazenamentoImagensEmMemoria armazenamento) {
        return new ImagensProdutoService(produtos, armazenamento);
    }
}
