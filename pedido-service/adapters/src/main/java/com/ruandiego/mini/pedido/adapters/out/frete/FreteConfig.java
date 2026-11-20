package com.ruandiego.mini.pedido.adapters.out.frete;

import com.ruandiego.mini.pedido.application.port.out.CalculadoraFrete;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * Escolhe o adapter do port CalculadoraFrete por `frete.provedor`: `fixo` (padrão, {@link FreteFixoAdapter}, um
 * @Component) ou `melhorenvio` (este bean). Trocar de provedor é só configuração; domain e application não mudam.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "frete.provedor", havingValue = "melhorenvio")
class FreteConfig {

    @Bean
    CalculadoraFrete calculadoraFreteMelhorEnvio(
            @Value("${frete.melhorenvio.url:https://sandbox.melhorenvio.com.br}") String url,
            @Value("${frete.melhorenvio.token}") String token,
            @Value("${frete.melhorenvio.user-agent:Mini Ecommerce (contato@exemplo.com)}") String userAgent,
            @Value("${frete.melhorenvio.cep-origem}") String cepOrigem,
            @Value("${frete.melhorenvio.pacote.altura-cm:10}") int altura,
            @Value("${frete.melhorenvio.pacote.largura-cm:20}") int largura,
            @Value("${frete.melhorenvio.pacote.comprimento-cm:30}") int comprimento,
            @Value("${frete.melhorenvio.pacote.peso-kg:1}") BigDecimal peso,
            @Value("${frete.melhorenvio.timeout-conexao-ms:2000}") long timeoutDeConexaoMs,
            @Value("${frete.melhorenvio.timeout-leitura-ms:3000}") long timeoutDeLeituraMs,
            @Value("${frete.melhorenvio.tentativas:3}") int tentativas,
            @Value("${frete.melhorenvio.espera-entre-tentativas-ms:300}") long esperaMs) {
        MelhorEnvioAdapter melhorEnvio = new MelhorEnvioAdapter(url, token, userAgent, cepOrigem,
                new MelhorEnvioAdapter.Pacote(altura, largura, comprimento, peso),
                Duration.ofMillis(timeoutDeConexaoMs), Duration.ofMillis(timeoutDeLeituraMs));
        return new FreteComFallback(melhorEnvio, new FreteFixoAdapter(), tentativas, Duration.ofMillis(esperaMs));
    }
}
