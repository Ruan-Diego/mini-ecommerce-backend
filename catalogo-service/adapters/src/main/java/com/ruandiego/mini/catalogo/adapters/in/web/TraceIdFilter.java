package com.ruandiego.mini.catalogo.adapters.in.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Trace id da requisição: lê o cabeçalho `X-Trace-Id` (posto pelo gateway), gera um se faltar ou se vier em
 * formato estranho, guarda no MDC (`traceId`, que entra em todo log da requisição) e devolve na resposta.
 * Quem grava eventos na outbox lê o MDC e o repassa na mensagem, ligando o log de quem publica ao de quem consome.
 * O formato é restrito para um cabeçalho malicioso não injetar linhas falsas no log.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class TraceIdFilter extends OncePerRequestFilter {

    static final String CABECALHO = "X-Trace-Id";
    static final String CHAVE_MDC = "traceId";
    private static final Pattern FORMATO_VALIDO = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String recebido = request.getHeader(CABECALHO);
        String traceId = recebido != null && FORMATO_VALIDO.matcher(recebido).matches()
                ? recebido
                : UUID.randomUUID().toString().replace("-", "");
        MDC.put(CHAVE_MDC, traceId);
        response.setHeader(CABECALHO, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(CHAVE_MDC);
        }
    }
}
