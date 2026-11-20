package com.ruandiego.mini.pagamento;

import com.ruandiego.mini.pagamento.application.port.out.BrokerMensagens;
import com.ruandiego.mini.pagamento.application.port.out.EventosDoGatewayProcessados;
import com.ruandiego.mini.pagamento.application.port.out.GatewayPagamento;
import com.ruandiego.mini.pagamento.application.port.out.MensagensProcessadas;
import com.ruandiego.mini.pagamento.application.port.out.Outbox;
import com.ruandiego.mini.pagamento.application.port.out.PagamentoRepository;
import com.ruandiego.mini.pagamento.application.port.out.PublicadorEventos;
import com.ruandiego.mini.pagamento.application.service.PagamentoService;
import com.ruandiego.mini.pagamento.application.service.PedidoEventosService;
import com.ruandiego.mini.pagamento.application.service.RelayDoOutbox;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.interceptor.DefaultTransactionAttribute;
import org.springframework.transaction.interceptor.MatchAlwaysTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.time.Clock;

/**
 * Wiring do pagamento: liga os services (que não conhecem Spring) aos adapters.
 *
 * O PagamentoService não tem @Transactional, porque a camada application não depende de Spring. A transação
 * entra aqui: o service vira um proxy que abre uma transação por chamada de use case. Assim o pagamento, o
 * registro do webhook como tratado e os eventos da outbox são gravados juntos, ou nenhum deles (qualquer
 * exceção de runtime desfaz tudo, inclusive o registro do webhook, que então pode ser reenviado).
 *
 * O relay NÃO é transacional como um todo: ele lê o que já foi commitado, publica no broker e dá baixa
 * mensagem a mensagem (cada baixa é uma transação curta no adapter), sem segurar o banco durante a rede.
 */
@Configuration(proxyBeanMethods = false)
class PagamentoConfig {

    @Bean
    Clock relogio() {
        return Clock.systemUTC();
    }

    /** Implementa Iniciar, Consultar, ProcessarEventoDoGateway, Estornar e ExpirarPixVencidos. */
    @Bean
    PagamentoService pagamentoService(PagamentoRepository pagamentos, GatewayPagamento gateway,
                                      EventosDoGatewayProcessados processados, PublicadorEventos publicador,
                                      Clock relogio, PlatformTransactionManager transacoes) {
        return transacional(new PagamentoService(pagamentos, gateway, processados, publicador, relogio), transacoes);
    }

    /** Implementa ProcessarEventoDePedido: o pagamento reagindo ao pedido cancelado (compensação da saga). */
    @Bean
    PedidoEventosService pedidoEventosService(PagamentoRepository pagamentos, GatewayPagamento gateway,
                                              MensagensProcessadas processadas, PublicadorEventos publicador,
                                              Clock relogio, PlatformTransactionManager transacoes) {
        return transacional(new PedidoEventosService(pagamentos, gateway, processadas, publicador, relogio), transacoes);
    }

    @Bean
    RelayDoOutbox relayDoOutbox(Outbox outbox, BrokerMensagens broker, Clock relogio) {
        return new RelayDoOutbox(outbox, broker, relogio);
    }

    /**
     * Envolve o service num proxy que roda cada método numa transação. O proxy é uma subclasse gerada em
     * tempo de execução: o tipo continua o do service, então ele é injetado por qualquer port de entrada
     * que o service implementa. Exceção de runtime (ex.: TransicaoDeStatusInvalida) desfaz a transação.
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
