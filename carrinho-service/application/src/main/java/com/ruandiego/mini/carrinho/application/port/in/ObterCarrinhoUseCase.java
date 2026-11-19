package com.ruandiego.mini.carrinho.application.port.in;

import com.ruandiego.mini.carrinho.application.CarrinhoResumo;
import com.ruandiego.mini.carrinho.application.Identidade;

/**
 * Carrinho atual de quem pergunta. Quem ainda não tem carrinho recebe um vazio (nada é gravado).
 * Se a identidade traz cliente e sessão, os carrinhos são unidos antes (primeiro acesso autenticado).
 */
public interface ObterCarrinhoUseCase {

    CarrinhoResumo obter(Identidade identidade);
}
