package com.ruandiego.mini.gateway;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Ponto de entrada do trace id: se a requisição chegou sem `X-Trace-Id` (ou com um valor de formato estranho),
 * o gateway gera um e o repassa para o serviço, e o devolve na resposta. Os serviços colocam o id no log e
 * nas mensagens que publicam, então uma compra pode ser seguida do navegador até o último consumidor.
 *
 * Com o OpenTelemetry Java agent, o gateway é o primeiro span do trace: o id devolvido é o trace id dele, e o
 * agent repassa o contexto aos serviços pelo cabeçalho `traceparent`.
 */
@Component
class TraceIdFiltro implements GlobalFilter, Ordered {

    static final String CABECALHO = "X-Trace-Id";
    private static final Pattern FORMATO_VALIDO = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String recebido = exchange.getRequest().getHeaders().getFirst(CABECALHO);
        SpanContext span = Span.current().getSpanContext();
        String traceId = span.isValid() ? span.getTraceId()
                : recebido != null && FORMATO_VALIDO.matcher(recebido).matches()
                ? recebido
                : UUID.randomUUID().toString().replace("-", "");
        ServerHttpRequest requisicao = exchange.getRequest().mutate().header(CABECALHO, traceId).build();
        exchange.getResponse().getHeaders().set(CABECALHO, traceId);
        return chain.filter(exchange.mutate().request(requisicao).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
