package com.ruandiego.mini.pedido.adapters.in.web;

import com.ruandiego.mini.pedido.adapters.out.frete.FreteFixoAdapter;
import com.ruandiego.mini.pedido.application.ServicoIndisponivelException;
import com.ruandiego.mini.pedido.application.memoria.CatalogoConsultaEmMemoria;
import com.ruandiego.mini.pedido.application.memoria.OutboxEmMemoria;
import com.ruandiego.mini.pedido.application.memoria.PedidoRepositoryEmMemoria;
import com.ruandiego.mini.pedido.application.service.PedidoService;
import com.ruandiego.mini.pedido.domain.model.ProdutoId;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * Para os testes de controller: o service de verdade sobre os adapters em memória, sem banco nem mocks.
 * O teste exercita o caminho HTTP → controller → use case → regra de domínio → resposta.
 */
@TestConfiguration(proxyBeanMethods = false)
class PedidoEmMemoria {

    static final Instant AGORA = Instant.parse("2026-11-20T12:00:00Z");

    /** Catálogo em memória que também sabe "cair", para provar o 503. */
    static class CatalogoDeTeste extends CatalogoConsultaEmMemoria {
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
    PedidoRepositoryEmMemoria pedidos() {
        return new PedidoRepositoryEmMemoria();
    }

    @Bean
    OutboxEmMemoria outbox() {
        return new OutboxEmMemoria();
    }

    @Bean
    CatalogoDeTeste catalogo() {
        return new CatalogoDeTeste();
    }

    @Bean
    PedidoService pedidoService(PedidoRepositoryEmMemoria pedidos, CatalogoDeTeste catalogo, OutboxEmMemoria outbox) {
        return new PedidoService(pedidos, new FreteFixoAdapter(), catalogo, outbox, Clock.fixed(AGORA, ZoneOffset.UTC));
    }
}
