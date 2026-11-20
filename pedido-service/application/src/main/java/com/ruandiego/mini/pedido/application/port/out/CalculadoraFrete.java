package com.ruandiego.mini.pedido.application.port.out;

import com.ruandiego.mini.kernel.Dinheiro;
import com.ruandiego.mini.pedido.domain.model.Frete;

import java.util.List;

/**
 * Port de saída do cálculo de frete. Hoje o adapter é fixo (FreteFixoAdapter); trocar por uma
 * transportadora real não muda o use case.
 */
public interface CalculadoraFrete {

    /** Opções disponíveis para o CEP (8 dígitos) e o subtotal dos itens. Nunca vazia. */
    List<Frete> cotar(String cep, Dinheiro subtotal);
}
