package com.ruandiego.mini.estoque.adapters.out.mensageria;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.slf4j.MDC;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Leva o contexto do trace através da outbox. O evento é gravado durante a requisição, mas o relay o publica
 * depois, em outra thread: sem isto, o trace do clique acabaria no commit. Na gravação guarda-se o
 * `traceparent` W3C da requisição; na publicação ele volta a ser o contexto atual, e o OpenTelemetry Java agent
 * pendura nele o span de envio ao RabbitMQ (o consumidor continua o mesmo trace pelo cabeçalho `traceparent`).
 *
 * Sem o agent (nos testes, por exemplo) não há span ativo: grava-se o trace id do MDC, como antes.
 */
public final class ContextoDeTrace {

    /** Cabeçalho W3C Trace Context. */
    public static final String CABECALHO_TRACEPARENT = "traceparent";

    private static final Pattern TRACEPARENT =
            Pattern.compile("00-([0-9a-f]{32})-([0-9a-f]{16})-([0-9a-f]{2})");

    private ContextoDeTrace() {
    }

    /** O que gravar na outbox: o traceparent do span atual, ou o trace id do MDC se não houver span. */
    public static String capturar() {
        SpanContext atual = Span.current().getSpanContext();
        if (atual.isValid()) {
            return "00-" + atual.getTraceId() + "-" + atual.getSpanId() + "-" + atual.getTraceFlags().asHex();
        }
        return MDC.get("traceId");
    }

    /** O trace id para logs (cabeçalho x-trace-id): o do traceparent, ou o valor como veio. */
    public static String traceId(String gravado) {
        Matcher m = gravado == null ? null : TRACEPARENT.matcher(gravado);
        return m != null && m.matches() ? m.group(1) : gravado;
    }

    /** Se o valor gravado é um traceparent, devolve-o (para ir no cabeçalho da mensagem); senão nulo. */
    public static String traceparent(String gravado) {
        return gravado != null && TRACEPARENT.matcher(gravado).matches() ? gravado : null;
    }

    /**
     * Torna atual o contexto gravado, até o `close()` do Scope. Valor que não é traceparent não muda nada.
     * O span restaurado é "remoto": serve só de pai para o span de envio que o agent cria.
     */
    public static Scope restaurar(String gravado) {
        Matcher m = gravado == null ? null : TRACEPARENT.matcher(gravado);
        if (m == null || !m.matches()) {
            return Scope.noop();
        }
        SpanContext pai = SpanContext.createFromRemoteParent(m.group(1), m.group(2),
                TraceFlags.fromHex(m.group(3), 0), TraceState.getDefault());
        if (!pai.isValid()) {
            return Scope.noop();
        }
        return Context.current().with(Span.wrap(pai)).makeCurrent();
    }
}
