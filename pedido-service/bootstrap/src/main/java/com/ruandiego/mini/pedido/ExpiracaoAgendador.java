package com.ruandiego.mini.pedido;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import com.ruandiego.mini.pedido.application.port.in.ExpirarPedidosSemPagamentoUseCase;
import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Adapter de entrada agendado: a cada minuto (`pedido.expiracao.intervalo`, em ms) chama o use case que
 * cancela os pedidos sem pagamento há 30 minutos. É detalhe de framework, por isso fica no bootstrap; a regra
 * (o prazo, o cancelamento, o evento) é do PedidoService.
 *
 * Trava entre instâncias: o job roda dentro de um lock do ShedLock (linha `expirar-pedidos` na tabela
 * `shedlock`). Com duas instâncias do serviço no ar, só a que pegar o lock executa a rodada; a outra pula.
 * `atMostFor` solta o lock sozinho se a instância que o pegou morrer no meio.
 *
 * Desliga por `pedido.expiracao.ligada=false`: os testes de integração o desligam e chamam {@link #executar()} à mão.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class ExpiracaoAgendador {

    static final String NOME_DO_LOCK = "expirar-pedidos";
    private static final Duration MAXIMO_COM_O_LOCK = Duration.ofMinutes(5);

    private static final Logger log = LoggerFactory.getLogger(ExpiracaoAgendador.class);

    private final ExpirarPedidosSemPagamentoUseCase expirar;
    private final LockingTaskExecutor travador;
    private final boolean ligado;
    private final Counter expiradas;

    ExpiracaoAgendador(ExpirarPedidosSemPagamentoUseCase expirar, LockingTaskExecutor travador,
                       @Value("${pedido.expiracao.ligada:true}") boolean ligado, ObjectProvider<MeterRegistry> metricas) {
        this.expirar = expirar;
        this.travador = travador;
        this.ligado = ligado;
        // Cada pedido cancelado por falta de pagamento devolve a reserva ao estoque: é a "reserva expirada".
        this.expiradas = Counter.builder("mini.reservas.expiradas")
                .description("Pedidos cancelados pelo job por falta de pagamento (reserva devolvida ao estoque)")
                .register(metricas.getIfAvailable(SimpleMeterRegistry::new));
    }

    @Bean
    static LockProvider lockProvider(DataSource dataSource) {
        // usingDbTime: o relógio do MySQL decide quando o lock vence, então relógios diferentes entre instâncias não atrapalham.
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .usingDbTime()
                .build());
    }

    @Bean
    static LockingTaskExecutor lockingTaskExecutor(LockProvider lockProvider) {
        return new DefaultLockingTaskExecutor(lockProvider);
    }

    @Scheduled(fixedDelayString = "${pedido.expiracao.intervalo:60000}")
    void expirarPedidosVencidos() {
        if (ligado) {
            executar();
        }
    }

    /**
     * Uma rodada, dentro do lock.
     *
     * @return quantos pedidos esta instância cancelou; 0 também quando outra instância estava com o lock
     */
    int executar() {
        AtomicInteger cancelados = new AtomicInteger();
        try {
            travador.executeWithLock((Runnable) () -> cancelados.set(expirar.expirarVencidos()),
                    new LockConfiguration(Instant.now(), NOME_DO_LOCK, MAXIMO_COM_O_LOCK, Duration.ZERO));
            if (cancelados.get() > 0) {
                log.info("expiração: {} pedido(s) sem pagamento cancelado(s)", cancelados.get());
                expiradas.increment(cancelados.get());
            }
        } catch (RuntimeException e) {
            // Banco fora do ar, ou o pagamento aprovado chegou no meio (conflito de versão): a próxima rodada tenta de novo.
            log.warn("expiração: falha na rodada: {}", e.getMessage());
        }
        return cancelados.get();
    }
}
