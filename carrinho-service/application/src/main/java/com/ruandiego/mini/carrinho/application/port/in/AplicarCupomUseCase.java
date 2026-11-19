package com.ruandiego.mini.carrinho.application.port.in;

import com.ruandiego.mini.carrinho.application.CarrinhoResumo;
import com.ruandiego.mini.carrinho.application.Identidade;

/** Aplica um cupom pelo código. Um cupom por carrinho: para trocar, remova o atual antes. */
public interface AplicarCupomUseCase {

    /**
     * @throws com.ruandiego.mini.carrinho.application.NaoEncontradoException se o código não existe
     * @throws com.ruandiego.mini.carrinho.domain.RegraDeNegocioException se vencido, abaixo do mínimo ou já houver outro cupom
     */
    CarrinhoResumo aplicar(Identidade identidade, String codigo);
}
