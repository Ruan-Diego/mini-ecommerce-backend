package com.ruandiego.mini.carrinho.adapters.out.visitante;

import com.ruandiego.mini.carrinho.application.contrato.CarrinhoVisitanteRepositoryContrato;
import com.ruandiego.mini.carrinho.application.port.out.CarrinhoVisitanteRepository;
import com.ruandiego.mini.carrinho.domain.model.Carrinho;
import com.ruandiego.mini.carrinho.domain.model.ProdutoId;
import com.ruandiego.mini.carrinho.domain.model.SessaoId;
import com.ruandiego.mini.kernel.Dinheiro;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** O adapter Redis contra um Redis de verdade, com o mesmo contrato do adapter em memória, mais a expiração. */
@DataRedisTest
@Import(RedisDeTeste.class)
class CarrinhoVisitanteRepositoryRedisTest extends CarrinhoVisitanteRepositoryContrato {

    private static final SessaoId SESSAO = new SessaoId("sessao-ttl");

    @Autowired
    private StringRedisTemplate redis;

    private CarrinhoVisitanteRepositoryRedis repositorio;

    private CarrinhoVisitanteRepositoryRedis repositorio() {
        if (repositorio == null) {
            repositorio = new CarrinhoVisitanteRepositoryRedis(redis, Duration.ofDays(7));
        }
        return repositorio;
    }

    @Override
    protected CarrinhoVisitanteRepository visitantes() {
        return repositorio();
    }

    @AfterEach
    void limpar() {
        redis.delete(redis.keys(CarrinhoVisitanteRepositoryRedis.PREFIXO + "*"));
    }

    private static Carrinho comUmItem() {
        Carrinho c = Carrinho.criar();
        c.adicionarItem(new ProdutoId(UUID.randomUUID()), "Esfera", Dinheiro.de("10.00"), 1);
        return c;
    }

    @Test
    void carrinhoExpiraEmSeteDias() {
        repositorio().salvar(SESSAO, comUmItem());

        Duration restante = Duration.ofSeconds(redis.getExpire(CarrinhoVisitanteRepositoryRedis.chave(SESSAO)));

        assertThat(restante).isBetween(Duration.ofDays(7).minusMinutes(1), Duration.ofDays(7));
    }

    @Test
    void cadaGravacaoRenovaOPrazo() {
        repositorio().salvar(SESSAO, comUmItem());
        String chave = CarrinhoVisitanteRepositoryRedis.chave(SESSAO);
        redis.expire(chave, Duration.ofSeconds(30));

        repositorio().salvar(SESSAO, comUmItem());

        assertThat(redis.getExpire(chave)).isGreaterThan(Duration.ofDays(6).toSeconds());
    }

    @Test
    void ttlVemDoConstrutor() {
        new CarrinhoVisitanteRepositoryRedis(redis, Duration.ofMinutes(10)).salvar(SESSAO, comUmItem());

        assertThat(redis.getExpire(CarrinhoVisitanteRepositoryRedis.chave(SESSAO))).isBetween(1L, 600L);
    }

    @Test
    void chaveTemPrefixoPorSessao() {
        repositorio().salvar(SESSAO, comUmItem());

        assertThat(redis.hasKey("carrinho:visitante:sessao-ttl")).isTrue();
    }
}
