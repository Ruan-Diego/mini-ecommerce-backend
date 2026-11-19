package com.ruandiego.mini.carrinho.adapters.out.visitante;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruandiego.mini.carrinho.application.port.out.CarrinhoVisitanteRepository;
import com.ruandiego.mini.carrinho.domain.RegraDeNegocioException;
import com.ruandiego.mini.carrinho.domain.model.Carrinho;
import com.ruandiego.mini.carrinho.domain.model.ItemCarrinho;
import com.ruandiego.mini.carrinho.domain.model.ProdutoId;
import com.ruandiego.mini.carrinho.domain.model.SessaoId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Adapter real do port CarrinhoVisitanteRepository (Redis).
 *
 * Cada carrinho é uma chave de texto (`carrinho:visitante:{sessao}`) com o JSON do carrinho. O Redis apaga
 * a chave sozinho depois do TTL (7 dias por padrão), e cada gravação renova o prazo: carrinho abandonado
 * some, carrinho em uso continua. Gravar numa chave só é atômico, sem precisar de transação.
 *
 * Índice por produto: para aplicar mudança de preço sem varrer todas as chaves, cada gravação também põe a
 * sessão no conjunto `carrinho:produto:{produtoId}`. O índice nunca é limpo item a item: pode listar sessão
 * que já tirou o produto ou cujo carrinho expirou, e quem consulta confere ao buscar o carrinho. O conjunto
 * ganha o mesmo TTL a cada gravação, então nunca expira antes de um carrinho que o referencia.
 */
@Repository
public class CarrinhoVisitanteRepositoryRedis implements CarrinhoVisitanteRepository {

    static final String PREFIXO = "carrinho:visitante:";
    static final String PREFIXO_PRODUTO = "carrinho:produto:";

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
        for (ItemCarrinho item : carrinho.itens()) {
            String indice = chaveProduto(item.produtoId());
            redis.opsForSet().add(indice, sessao.valor());
            redis.expire(indice, ttl);
        }
    }

    @Override
    public void remover(SessaoId sessao) {
        redis.delete(chave(sessao));
    }

    @Override
    public List<SessaoId> sessoesComProduto(ProdutoId produto) {
        Set<String> membros = redis.opsForSet().members(chaveProduto(produto));
        if (membros == null) {
            return List.of();
        }
        return membros.stream().map(CarrinhoVisitanteRepositoryRedis::sessaoOuNada)
                .filter(Optional::isPresent).map(Optional::get).toList();
    }

    /** Valor estranho no índice (não deveria existir) é ignorado em vez de derrubar a atualização de preços. */
    private static Optional<SessaoId> sessaoOuNada(String valor) {
        try {
            return Optional.of(new SessaoId(valor));
        } catch (RegraDeNegocioException e) {
            return Optional.empty();
        }
    }

    static String chave(SessaoId sessao) {
        return PREFIXO + sessao.valor();
    }

    static String chaveProduto(ProdutoId produto) {
        return PREFIXO_PRODUTO + produto;
    }
}
