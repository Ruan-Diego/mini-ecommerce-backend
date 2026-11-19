package com.ruandiego.mini.carrinho;

import com.ruandiego.mini.carrinho.application.port.out.CarrinhoClienteRepository;
import com.ruandiego.mini.carrinho.application.port.out.CarrinhoVisitanteRepository;
import com.ruandiego.mini.carrinho.application.port.out.CatalogoProdutos;
import com.ruandiego.mini.carrinho.application.port.out.CupomRepository;
import com.ruandiego.mini.carrinho.application.port.out.PrecosJaProcessados;
import com.ruandiego.mini.carrinho.application.service.CarrinhoService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Wiring do carrinho: liga o service (que não conhece Spring) aos adapters.
 *
 * Diferente do catálogo, o service não é envolvido em transação: o carrinho do visitante está no Redis
 * (uma chave, gravação atômica) e o do cliente no MySQL, cuja transação já é aberta pelo adapter JPA
 * em cada operação do port. O service só decide a ordem (grava o cliente antes de apagar o visitante).
 */
@Configuration(proxyBeanMethods = false)
class CarrinhoConfig {

    @Bean
    Clock relogio() {
        return Clock.systemUTC();
    }

    /** Implementa todos os use cases do carrinho (ObterCarrinho, AdicionarItem, UnirCarrinhos, ...). */
    @Bean
    CarrinhoService carrinhoService(CarrinhoVisitanteRepository visitantes, CarrinhoClienteRepository clientes,
                                    CupomRepository cupons, CatalogoProdutos catalogo, PrecosJaProcessados processados,
                                    Clock relogio) {
        return new CarrinhoService(visitantes, clientes, cupons, catalogo, processados, relogio);
    }
}
