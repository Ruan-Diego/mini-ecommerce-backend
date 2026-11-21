package com.ruandiego.mini.catalogo.adapters.in.web;

import com.ruandiego.mini.catalogo.application.ConflitoException;
import com.ruandiego.mini.catalogo.application.NaoEncontradoException;
import com.ruandiego.mini.catalogo.domain.RegraDeNegocioException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Traduz as exceções do domínio e da application para HTTP, no formato RFC 7807 (application/problem+json).
 *
 * Essa tradução é papel do adapter: o domínio só diz "regra violada" ou "não existe", sem saber que
 * existe HTTP. Herdar de ResponseEntityExceptionHandler faz os erros do próprio Spring (JSON mal formado,
 * campo obrigatório faltando, tipo errado num parâmetro) saírem no mesmo formato.
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
        ProblemDetail problema = problema(HttpStatus.CONFLICT, "Conflito", e.getMessage());
        if (e.campo() != null) {
            // Extensão do problem+json: a tela põe a mensagem no campo certo (ex.: "sku").
            problema.setProperty("campo", e.campo());
        }
        return problema;
    }

    @ExceptionHandler(RequisicaoInvalidaException.class)
    ProblemDetail requisicaoInvalida(RequisicaoInvalidaException e) {
        return problema(HttpStatus.BAD_REQUEST, "Requisição inválida", e.getMessage());
    }

    private static ProblemDetail problema(HttpStatus status, String titulo, String detalhe) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalhe);
        problema.setTitle(titulo);
        return problema;
    }
}
