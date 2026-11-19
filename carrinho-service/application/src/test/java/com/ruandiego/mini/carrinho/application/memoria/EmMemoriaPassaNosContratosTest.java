package com.ruandiego.mini.carrinho.application.memoria;

import com.ruandiego.mini.carrinho.application.contrato.CarrinhoClienteRepositoryContrato;
import com.ruandiego.mini.carrinho.application.contrato.CarrinhoVisitanteRepositoryContrato;
import com.ruandiego.mini.carrinho.application.contrato.CupomRepositoryContrato;
import com.ruandiego.mini.carrinho.application.port.out.CarrinhoClienteRepository;
import com.ruandiego.mini.carrinho.application.port.out.CarrinhoVisitanteRepository;
import com.ruandiego.mini.carrinho.application.port.out.CupomRepository;
import com.ruandiego.mini.carrinho.domain.model.Cupom;
import com.ruandiego.mini.kernel.Dinheiro;

import java.time.Instant;

/** Os adapters em memória passam nos mesmos contratos que Redis, JPA e o catálogo de cupons reais. */
class EmMemoriaPassaNosContratosTest {

    static class Visitante extends CarrinhoVisitanteRepositoryContrato {
        private final CarrinhoVisitanteRepositoryEmMemoria repositorio = new CarrinhoVisitanteRepositoryEmMemoria();

        @Override
        protected CarrinhoVisitanteRepository visitantes() {
            return repositorio;
        }
    }

    static class Cliente extends CarrinhoClienteRepositoryContrato {
        private final CarrinhoClienteRepositoryEmMemoria repositorio = new CarrinhoClienteRepositoryEmMemoria();

        @Override
        protected CarrinhoClienteRepository clientes() {
            return repositorio;
        }
    }

    static class Cupons extends CupomRepositoryContrato {
        private final CupomRepositoryEmMemoria repositorio = new CupomRepositoryEmMemoria();

        Cupons() {
            Instant validade = Instant.parse("2030-12-31T23:59:59Z");
            repositorio.adicionar(Cupom.percentual("BEMVINDO", 10, validade, Dinheiro.de("100.00")));
            repositorio.adicionar(Cupom.valorFixo("FRETE15", Dinheiro.de("15.00"), validade, null));
        }

        @Override
        protected CupomRepository cupons() {
            return repositorio;
        }
    }
}
