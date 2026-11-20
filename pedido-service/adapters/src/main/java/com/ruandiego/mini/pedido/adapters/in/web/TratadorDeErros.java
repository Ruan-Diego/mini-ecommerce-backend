package com.ruandiego.mini.pedido.adapters.in.web;

import com.ruandiego.mini.pedido.application.ConflitoException;
import com.ruandiego.mini.pedido.application.NaoEncontradoException;
import com.ruandiego.mini.pedido.application.ServicoIndisponivelException;
import com.ruandiego.mini.pedido.domain.RegraDeNegocioException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Traduz as exceções do domínio e da application para HTTP, no formato RFC 7807 (application/problem+json).
 * Essa tradução é papel do adapter: o domínio só diz "regra violada" ou "não existe", sem saber que
 * existe HTTP. TransicaoDeStatusInvalida é uma RegraDeNegocioException, então também sai como 422.
 */
@RestControllerAdvice
class TratadorDeErros extends ResponseEntityExceptionHandler {

    @ExceptionHandler(RegraDeNegocioException.class)
    ProblemDetail regraDeNegocio(RegraDeNegocioException e) {
        return problema(HttpStatus.UNPROCESSABLE_ENTITY, "Regra de negócio violada", e.getMessage());
    }

    @ExceptionHandler(NaoEncontradoException.class)
    ProblemDetail naoEncontrado(NaoEncontradoException e) {
        return problema(HttpStatus.NOT_FOUND, "Não encontrado", e.getMessage());
    }

    @ExceptionHandler(ConflitoException.class)
    ProblemDetail conflito(ConflitoException e) {
        return problema(HttpStatus.CONFLICT, "Conflito", e.getMessage());
    }

    @ExceptionHandler(NaoAutenticadoException.class)
    ProblemDetail naoAutenticado(NaoAutenticadoException e) {
        return problema(HttpStatus.UNAUTHORIZED, "Não autenticado", e.getMessage());
    }

    @ExceptionHandler(RequisicaoInvalidaException.class)
    ProblemDetail requisicaoInvalida(RequisicaoInvalidaException e) {
        return problema(HttpStatus.BAD_REQUEST, "Requisição inválida", e.getMessage());
    }

    @ExceptionHandler(ServicoIndisponivelException.class)
    ProblemDetail servicoIndisponivel(ServicoIndisponivelException e) {
        return problema(HttpStatus.SERVICE_UNAVAILABLE, "Serviço indisponível", e.getMessage());
    }

    private static ProblemDetail problema(HttpStatus status, String titulo, String detalhe) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalhe);
        problema.setTitle(titulo);
        return problema;
    }
}
