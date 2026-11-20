package com.ruandiego.mini.pagamento.adapters.in.web;

import com.ruandiego.mini.pagamento.application.memoria.EventosDoGatewayProcessadosEmMemoria;
import com.ruandiego.mini.pagamento.application.memoria.GatewayPagamentoEmMemoria;
import com.ruandiego.mini.pagamento.application.memoria.OutboxEmMemoria;
import com.ruandiego.mini.pagamento.application.memoria.PagamentoRepositoryEmMemoria;
import com.ruandiego.mini.pagamento.application.memoria.PedidosCanceladosEmMemoria;
import com.ruandiego.mini.pagamento.application.service.PagamentoService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * Para os testes de controller: o service de verdade sobre os adapters em memória, sem banco nem mocks.
 * O teste exercita o caminho HTTP → controller → use case → regra de domínio → resposta.
 */
@TestConfiguration(proxyBeanMethods = false)
class PagamentoEmMemoria {

    static final Instant AGORA = Instant.parse("2026-11-20T12:00:00Z");

    @Bean
    PagamentoRepositoryEmMemoria pagamentos() {
        return new PagamentoRepositoryEmMemoria();
    }

    @Bean
    GatewayPagamentoEmMemoria gateway() {
        return new GatewayPagamentoEmMemoria();
    }

    @Bean
    EventosDoGatewayProcessadosEmMemoria processados() {
        return new EventosDoGatewayProcessadosEmMemoria();
    }

    @Bean
    PedidosCanceladosEmMemoria cancelados() {
        return new PedidosCanceladosEmMemoria();
    }

    @Bean
    OutboxEmMemoria outbox() {
        return new OutboxEmMemoria();
    }

    @Bean
    PagamentoService pagamentoService(PagamentoRepositoryEmMemoria pagamentos, GatewayPagamentoEmMemoria gateway,
                                      EventosDoGatewayProcessadosEmMemoria processados,
                                      PedidosCanceladosEmMemoria cancelados, OutboxEmMemoria outbox) {
        return new PagamentoService(pagamentos, gateway, processados, cancelados, outbox, Clock.fixed(AGORA, ZoneOffset.UTC));
    }
}
