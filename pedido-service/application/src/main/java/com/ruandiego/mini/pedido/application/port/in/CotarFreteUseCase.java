package com.ruandiego.mini.pedido.application.port.in;

import com.ruandiego.mini.kernel.Dinheiro;
import com.ruandiego.mini.pedido.domain.model.Frete;

import java.util.List;

/** Opções de frete para um CEP e um subtotal. */
public interface CotarFreteUseCase {

    List<Frete> cotar(String cep, Dinheiro subtotal);
}
