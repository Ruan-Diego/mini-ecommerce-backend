package com.ruandiego.mini.carrinho.application.port.out;

import com.ruandiego.mini.carrinho.domain.model.Carrinho;
import com.ruandiego.mini.carrinho.domain.model.SessaoId;

import java.util.Optional;

/**
 * Port de saída do carrinho de visitante, indexado pela sessão. Dado passageiro: o adapter real (Redis)
 * faz o carrinho expirar sozinho depois de 7 dias sem uso, e cada {@code salvar} renova esse prazo.
 */
public interface CarrinhoVisitanteRepository {

    Optional<Carrinho> buscar(SessaoId sessao);

    /** Cria ou substitui o carrinho da sessão. */
    void salvar(SessaoId sessao, Carrinho carrinho);

    /** Apaga o carrinho da sessão. Sem carrinho, não é erro. */
    void remover(SessaoId sessao);
}
