package com.ruandiego.mini.pedido.adapters.out.frete;

import com.ruandiego.mini.kernel.Dinheiro;
import com.ruandiego.mini.pedido.application.ServicoIndisponivelException;
import com.ruandiego.mini.pedido.application.port.out.CalculadoraFrete;
import com.ruandiego.mini.pedido.domain.model.Frete;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;

/**
 * Decorator do port CalculadoraFrete: tenta a transportadora com retry (Resilience4j) e, se ela continuar falhando,
 * cai no frete fixo. Assim uma transportadora fora do ar nunca impede a compra. O timeout fica no cliente HTTP do
 * {@link MelhorEnvioAdapter}; aqui só a decisão de tentar de novo e do que fazer quando acabam as tentativas.
 *
 * O fallback devolve os códigos do frete fixo ("padrao", "expresso"), que a cotação e a criação do pedido usam do mesmo
 * jeito. Se a transportadora voltar entre a cotação e a criação, o código escolhido deixa de existir e o pedido é
 * recusado com "opção de frete inválida": o cliente cota de novo (comportamento já coberto pelo use case).
 */
public class FreteComFallback implements CalculadoraFrete {

    private static final Logger LOG = LoggerFactory.getLogger(FreteComFallback.class);

    private final CalculadoraFrete principal;
    private final CalculadoraFrete reserva;
    private final Retry retry;

    public FreteComFallback(CalculadoraFrete principal, CalculadoraFrete reserva, int tentativas, Duration espera) {
        this.principal = principal;
        this.reserva = reserva;
        this.retry = Retry.of("melhor-envio", RetryConfig.custom()
                .maxAttempts(tentativas)
                .waitDuration(espera)
                .retryExceptions(ServicoIndisponivelException.class)
                .build());
    }

    @Override
    public List<Frete> cotar(String cep, Dinheiro subtotal) {
        try {
            return Retry.decorateSupplier(retry, () -> principal.cotar(cep, subtotal)).get();
        } catch (ServicoIndisponivelException e) {
            LOG.warn("transportadora indisponível depois de {} tentativas, usando o frete fixo: {}",
                    retry.getRetryConfig().getMaxAttempts(), e.getMessage());
            return reserva.cotar(cep, subtotal);
        }
    }
}
