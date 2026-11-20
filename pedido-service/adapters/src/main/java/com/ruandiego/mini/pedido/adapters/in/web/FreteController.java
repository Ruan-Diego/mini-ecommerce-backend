package com.ruandiego.mini.pedido.adapters.in.web;

import com.ruandiego.mini.kernel.Dinheiro;
import com.ruandiego.mini.pedido.application.port.in.CotarFreteUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/** Cotação de frete para a tela de escolha do frete: `GET /fretes/cotacao?cep=01310100&subtotal=250.00`. */
@RestController
class FreteController {

    private final CotarFreteUseCase cotar;

    FreteController(CotarFreteUseCase cotar) {
        this.cotar = cotar;
    }

    @GetMapping("/fretes/cotacao")
    List<FreteResposta> cotacao(@RequestParam String cep, @RequestParam BigDecimal subtotal) {
        if (subtotal.signum() < 0) {
            throw new RequisicaoInvalidaException("o subtotal não pode ser negativo");
        }
        return cotar.cotar(cep, new Dinheiro(subtotal)).stream().map(FreteResposta::de).toList();
    }
}
