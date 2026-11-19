package com.ruandiego.mini.carrinho.adapters.in.web;

import com.ruandiego.mini.carrinho.application.ServicoIndisponivelException;
import com.ruandiego.mini.carrinho.application.memoria.CarrinhoClienteRepositoryEmMemoria;
import com.ruandiego.mini.carrinho.application.memoria.CarrinhoVisitanteRepositoryEmMemoria;
import com.ruandiego.mini.carrinho.application.memoria.CatalogoProdutosEmMemoria;
import com.ruandiego.mini.carrinho.application.memoria.CupomRepositoryEmMemoria;
import com.ruandiego.mini.carrinho.application.service.CarrinhoService;
import com.ruandiego.mini.carrinho.domain.model.ProdutoId;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * Para os testes de controller: o service de verdade sobre os adapters em memória, sem banco, Redis nem
 * mocks. O teste exercita o caminho HTTP → controller → use case → regra de domínio → resposta.
 */
@TestConfiguration(proxyBeanMethods = false)
class CarrinhoEmMemoria {

    static final Instant AGORA = Instant.parse("2026-11-19T12:00:00Z");

    /** Catálogo em memória que também sabe "cair", para provar o 503. */
    static class CatalogoDeTeste extends CatalogoProdutosEmMemoria {
        boolean foraDoAr;

        @Override
        public Optional<Produto> buscar(ProdutoId id) {
            if (foraDoAr) {
                throw new ServicoIndisponivelException("catálogo indisponível", null);
            }
            return super.buscar(id);
        }
    }

    @Bean
    CarrinhoVisitanteRepositoryEmMemoria visitantes() {
        return new CarrinhoVisitanteRepositoryEmMemoria();
    }

    @Bean
    CarrinhoClienteRepositoryEmMemoria clientes() {
        return new CarrinhoClienteRepositoryEmMemoria();
    }

    @Bean
    CupomRepositoryEmMemoria cupons() {
        return new CupomRepositoryEmMemoria();
    }

    @Bean
    CatalogoDeTeste catalogo() {
        return new CatalogoDeTeste();
    }

    @Bean
    CarrinhoService carrinhoService(CarrinhoVisitanteRepositoryEmMemoria visitantes,
                                    CarrinhoClienteRepositoryEmMemoria clientes, CupomRepositoryEmMemoria cupons,
                                    CatalogoDeTeste catalogo) {
        return new CarrinhoService(visitantes, clientes, cupons, catalogo, Clock.fixed(AGORA, ZoneOffset.UTC));
    }
}
