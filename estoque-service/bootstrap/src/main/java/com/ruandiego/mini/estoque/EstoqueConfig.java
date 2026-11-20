package com.ruandiego.mini.estoque;

import com.ruandiego.mini.estoque.application.port.out.BrokerMensagens;
import com.ruandiego.mini.estoque.application.port.out.ItemEstoqueRepository;
import com.ruandiego.mini.estoque.application.port.out.MensagensProcessadas;
import com.ruandiego.mini.estoque.application.port.out.Outbox;
import com.ruandiego.mini.estoque.application.port.out.PublicadorEventos;
import com.ruandiego.mini.estoque.application.port.out.ReservaRepository;
import com.ruandiego.mini.estoque.application.service.EstoqueService;
import com.ruandiego.mini.estoque.application.service.RelayDoOutbox;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.interceptor.DefaultTransactionAttribute;
import org.springframework.transaction.interceptor.MatchAlwaysTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.time.Clock;

/**
 * Wiring do estoque: liga o service (que não conhece Spring) aos adapters.
 *
 * O EstoqueService não tem @Transactional, porque a camada application não depende de Spring. A transação
 * entra aqui: o service vira um proxy que abre uma transação por chamada de use case. Assim o saldo, a
 * reserva, o registro da mensagem como tratada e os eventos da outbox são gravados juntos, ou nenhum deles.
 * É também essa transação que segura a trava de linha (`SELECT ... FOR UPDATE`) dos saldos até o commit.
 *
 * O relay NÃO é transacional como um todo: lê o que já foi commitado, publica no broker e dá baixa mensagem
 * a mensagem, sem segurar o banco durante a rede.
 */
@Configuration(proxyBeanMethods = false)
class EstoqueConfig {

    @Bean
    Clock relogio() {
        return Clock.systemUTC();
    }

    /** Implementa Processar eventos do pedido, Consultar e Ajustar. */
    @Bean
    EstoqueService estoqueService(ItemEstoqueRepository itens, ReservaRepository reservas,
                                  MensagensProcessadas processadas, PublicadorEventos publicador, Clock relogio,
                                  PlatformTransactionManager transacoes) {
        return transacional(new EstoqueService(itens, reservas, processadas, publicador, relogio), transacoes);
    }

    @Bean
    RelayDoOutbox relayDoOutbox(Outbox outbox, BrokerMensagens broker, Clock relogio) {
        return new RelayDoOutbox(outbox, broker, relogio);
    }

    /**
     * Envolve o service num proxy que roda cada método numa transação. O proxy é uma subclasse gerada em
     * tempo de execução: o tipo continua o do service, então ele é injetado por qualquer port de entrada
     * que o service implementa. Exceção de runtime desfaz a transação (inclusive o registro da mensagem).
     */
    private static <T> T transacional(T service, PlatformTransactionManager transacoes) {
        DefaultTransactionAttribute atributo = new DefaultTransactionAttribute();
        MatchAlwaysTransactionAttributeSource regra = new MatchAlwaysTransactionAttributeSource();
        regra.setTransactionAttribute(atributo);

        ProxyFactory proxy = new ProxyFactory(service);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(transacoes, regra));
        @SuppressWarnings("unchecked")
        T transacional = (T) proxy.getProxy();
        return transacional;
    }
}
