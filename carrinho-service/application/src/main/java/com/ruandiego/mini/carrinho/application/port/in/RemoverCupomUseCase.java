package com.ruandiego.mini.carrinho.application.port.in;

import com.ruandiego.mini.carrinho.application.CarrinhoResumo;
import com.ruandiego.mini.carrinho.application.Identidade;

/** Tira o cupom do carrinho. Sem cupom aplicado, não é erro. */
public interface RemoverCupomUseCase {

    CarrinhoResumo removerCupom(Identidade identidade);
}
