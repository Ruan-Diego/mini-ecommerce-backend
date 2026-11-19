package com.ruandiego.mini.carrinho.adapters.out.visitante;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruandiego.mini.carrinho.application.port.out.CarrinhoVisitanteRepository;
import com.ruandiego.mini.carrinho.domain.model.Carrinho;
import com.ruandiego.mini.carrinho.domain.model.SessaoId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

/**
 * Adapter real do port CarrinhoVisitanteRepository (Redis).
 *
 * Cada carrinho é uma chave de texto (`carrinho:visitante:{sessao}`) com o JSON do carrinho. O Redis apaga
 * a chave sozinho depois do TTL (7 dias por padrão), e cada gravação renova o prazo: carrinho abandonado
 * some, carrinho em uso continua. Gravar numa chave só é atômico, sem precisar de transação.
 */
@Repository
public class CarrinhoVisitanteRepositoryRedis implements CarrinhoVisitanteRepository {

    static final String PREFIXO = "carrinho:visitante:";

    private final StringRedisTemplate redis;
    private final Duration ttl;
    // Sem módulos extras: o JSON só tem texto, números e listas (ver CarrinhoJson).
    private final ObjectMapper json = new ObjectMapper();

    public CarrinhoVisitanteRepositoryRedis(StringRedisTemplate redis,
                                            @Value("${carrinho.visitante.ttl:7d}") Duration ttl) {
        this.redis = redis;
        this.ttl = ttl;
    }

    @Override
    public Optional<Carrinho> buscar(SessaoId sessao) {
        String texto = redis.opsForValue().get(chave(sessao));
        if (texto == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(json.readValue(texto, CarrinhoJson.class).paraDominio());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("carrinho de visitante ilegível no Redis (sessão " + sessao + ")", e);
        }
    }

    @Override
    public void salvar(SessaoId sessao, Carrinho carrinho) {
        try {
            redis.opsForValue().set(chave(sessao), json.writeValueAsString(CarrinhoJson.de(carrinho)), ttl);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("não foi possível serializar o carrinho", e);
        }
    }

    @Override
    public void remover(SessaoId sessao) {
        redis.delete(chave(sessao));
    }

    static String chave(SessaoId sessao) {
        return PREFIXO + sessao.valor();
    }
}
