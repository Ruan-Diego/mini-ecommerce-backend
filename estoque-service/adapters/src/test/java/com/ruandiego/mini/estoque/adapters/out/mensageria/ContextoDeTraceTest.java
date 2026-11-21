package com.ruandiego.mini.estoque.adapters.out.mensageria;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Scope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

class ContextoDeTraceTest {

    private static final String TRACE = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String SPAN = "00f067aa0ba902b7";
    private static final String TRACEPARENT = "00-" + TRACE + "-" + SPAN + "-01";

    @AfterEach
    void limparMdc() {
        MDC.clear();
    }

    @Test
    void comSpanAtivoGravaOTraceparentW3c() {
        SpanContext contexto = SpanContext.create(TRACE, SPAN, TraceFlags.getSampled(), TraceState.getDefault());

        try (Scope ignorado = Span.wrap(contexto).makeCurrent()) {
            assertThat(ContextoDeTrace.capturar()).isEqualTo(TRACEPARENT);
        }
    }

    @Test
    void semSpanGravaOTraceIdDoMdcComoAntes() {
        MDC.put("traceId", "abc123");

        assertThat(ContextoDeTrace.capturar()).isEqualTo("abc123");
    }

    @Test
    void restaurarTornaOTraceGravadoOContextoAtualAteOClose() {
        assertThat(Span.current().getSpanContext().isValid()).isFalse();

        try (Scope ignorado = ContextoDeTrace.restaurar(TRACEPARENT)) {
            SpanContext atual = Span.current().getSpanContext();
            assertThat(atual.getTraceId()).isEqualTo(TRACE);
            assertThat(atual.getSpanId()).isEqualTo(SPAN);
            assertThat(atual.isRemote()).isTrue();
            // Um evento gravado dentro do contexto restaurado continua o mesmo trace (consumidor que publica).
            assertThat(ContextoDeTrace.capturar()).isEqualTo(TRACEPARENT);
        }
        assertThat(Span.current().getSpanContext().isValid()).isFalse();
    }

    @Test
    void valorQueNaoEhTraceparentNaoMudaOContexto() {
        for (String valor : new String[]{null, "abc123", "00-zz-yy-01", "00-" + "0".repeat(32) + "-" + SPAN + "-01"}) {
            try (Scope ignorado = ContextoDeTrace.restaurar(valor)) {
                assertThat(Span.current().getSpanContext().isValid()).as(String.valueOf(valor)).isFalse();
            }
        }
    }

    @Test
    void separaOTraceIdParaOLogEOTraceparentParaOCabecalho() {
        assertThat(ContextoDeTrace.traceId(TRACEPARENT)).isEqualTo(TRACE);
        assertThat(ContextoDeTrace.traceId("abc123")).isEqualTo("abc123");
        assertThat(ContextoDeTrace.traceId(null)).isNull();
        assertThat(ContextoDeTrace.traceparent(TRACEPARENT)).isEqualTo(TRACEPARENT);
        assertThat(ContextoDeTrace.traceparent("abc123")).isNull();
    }
}
