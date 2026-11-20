package com.ruandiego.mini.carrinho.adapters.in.web;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TraceIdFilterTest {

    private final TraceIdFilter filtro = new TraceIdFilter();

    private record Resultado(String noMdcDuranteARequisicao, MockHttpServletResponse resposta) {
    }

    private Resultado executar(String cabecalho) throws Exception {
        MockHttpServletRequest requisicao = new MockHttpServletRequest();
        if (cabecalho != null) {
            requisicao.addHeader("X-Trace-Id", cabecalho);
        }
        MockHttpServletResponse resposta = new MockHttpServletResponse();
        AtomicReference<String> visto = new AtomicReference<>();
        filtro.doFilter(requisicao, resposta, new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                visto.set(MDC.get("traceId"));
            }
        });
        return new Resultado(visto.get(), resposta);
    }

    @Test
    void usaOTraceIdRecebidoNoMdcENaResposta() throws Exception {
        Resultado r = executar("abc-123.DEF_456");

        assertThat(r.noMdcDuranteARequisicao()).isEqualTo("abc-123.DEF_456");
        assertThat(r.resposta().getHeader("X-Trace-Id")).isEqualTo("abc-123.DEF_456");
    }

    @Test
    void geraUmQuandoNaoVem() throws Exception {
        Resultado r = executar(null);

        assertThat(r.noMdcDuranteARequisicao()).matches("[0-9a-f]{32}");
        assertThat(r.resposta().getHeader("X-Trace-Id")).isEqualTo(r.noMdcDuranteARequisicao());
    }

    @Test
    void trocaUmValorComFormatoEstranhoParaNaoInjetarLinhasNoLog() throws Exception {
        Resultado r = executar("falso\nINFO login ok");

        assertThat(r.noMdcDuranteARequisicao()).matches("[0-9a-f]{32}");
        assertThat(executar("x".repeat(65)).noMdcDuranteARequisicao()).matches("[0-9a-f]{32}");
    }

    @Test
    void limpaOMdcNoFim() throws Exception {
        executar("abc");

        assertThat(MDC.get("traceId")).isNull();
    }
}
