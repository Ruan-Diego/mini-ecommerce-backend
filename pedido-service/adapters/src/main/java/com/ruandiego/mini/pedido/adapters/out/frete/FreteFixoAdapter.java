package com.ruandiego.mini.pedido.adapters.out.frete;

import com.ruandiego.mini.kernel.Dinheiro;
import com.ruandiego.mini.pedido.application.port.out.CalculadoraFrete;
import com.ruandiego.mini.pedido.domain.model.Frete;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Adapter do port CalculadoraFrete com valores fixos, sem consultar transportadora: o Padrão é grátis
 * a partir de R$ 299,00 de subtotal; o Expresso tem preço único. O CEP não muda o preço (ainda).
 * Trocar por uma transportadora de verdade é escrever outro adapter, sem tocar no use case.
 */
@Component
public class FreteFixoAdapter implements CalculadoraFrete {

    static final Dinheiro FRETE_GRATIS_A_PARTIR_DE = Dinheiro.de("299.00");

    @Override
    public List<Frete> cotar(String cep, Dinheiro subtotal) {
        Dinheiro padrao = subtotal.compareTo(FRETE_GRATIS_A_PARTIR_DE) >= 0 ? Dinheiro.ZERO : Dinheiro.de("19.90");
        return List.of(
                new Frete("padrao", "Padrão", padrao, 7),
                new Frete("expresso", "Expresso", Dinheiro.de("34.90"), 3));
    }
}
