package com.ruandiego.mini.pedido;

import com.ruandiego.mini.pedido.application.port.out.BrokerMensagens;
import com.ruandiego.mini.pedido.application.port.out.CalculadoraFrete;
import com.ruandiego.mini.pedido.application.port.out.CatalogoConsulta;
import com.ruandiego.mini.pedido.application.port.out.Outbox;
import com.ruandiego.mini.pedido.application.port.out.PedidoRepository;
import com.ruandiego.mini.pedido.application.port.out.PublicadorEventos;
import com.ruandiego.mini.pedido.application.service.PedidoService;
import com.ruandiego.mini.pedido.application.service.RelayDoOutbox;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.interceptor.DefaultTransactionAttribute;
import org.springframework.transaction.interceptor.MatchAlwaysTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.time.Clock;

/**
 * Wiring do pedido: liga os services (que não conhecem Spring) aos adapters.
 *
 * O PedidoService não tem @Transactional, porque a camada application não depende de Spring. A transação
 * entra aqui: o service vira um proxy que abre uma transação por chamada de use case. Assim o pedido e os
 * eventos da outbox são gravados juntos, ou nenhum dos dois (qualquer exceção de runtime desfaz tudo).
 *
 * O relay NÃO é transacional como um todo: ele lê o que já foi commitado, publica no broker e dá baixa
 * mensagem a mensagem (cada baixa é uma transação curta no adapter), sem segurar o banco durante a rede.
 */
@Configuration(proxyBeanMethods = false)
class PedidoConfig {

    @Bean
    Clock relogio() {
        return Clock.systemUTC();
    }

    /** Implementa CriarPedido, ConsultarPedidos, CancelarPedido e CotarFrete. */
    @Bean
    PedidoService pedidoService(PedidoRepository pedidos, CalculadoraFrete calculadoraFrete, CatalogoConsulta catalogo,
                                PublicadorEventos publicador, Clock relogio, PlatformTransactionManager transacoes) {
        return transacional(new PedidoService(pedidos, calculadoraFrete, catalogo, publicador, relogio), transacoes);
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
