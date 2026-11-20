package com.ruandiego.mini.pedido.application.memoria;

import com.ruandiego.mini.kernel.Dinheiro;
import com.ruandiego.mini.pedido.application.port.out.CalculadoraFrete;
import com.ruandiego.mini.pedido.domain.model.Frete;

import java.util.List;

/** Calculadora para testes: duas opções fixas, sem regra de CEP nem de subtotal. */
public class CalculadoraFreteEmMemoria implements CalculadoraFrete {

    @Override
    public List<Frete> cotar(String cep, Dinheiro subtotal) {
        return List.of(
                new Frete("padrao", "Padrão", Dinheiro.de("19.90"), 7),
                new Frete("expresso", "Expresso", Dinheiro.de("34.90"), 3));
    }
}
