package com.ruandiego.mini.pedido.adapters.out.persistencia;

import com.ruandiego.mini.pedido.application.port.out.PedidoRepository;
import com.ruandiego.mini.pedido.domain.model.ClienteId;
import com.ruandiego.mini.pedido.domain.model.Pedido;
import com.ruandiego.mini.pedido.domain.model.PedidoId;
import com.ruandiego.mini.pedido.domain.model.StatusPedido;
import com.ruandiego.mini.pedido.application.Pagina;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Adapter real do PedidoRepository: MySQL via JPA. A transação é a do use case (aberta no wiring). */
@Component
public class PedidoRepositoryJpa implements PedidoRepository {

    private final PedidoJpaRepository jpa;

    PedidoRepositoryJpa(PedidoJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void salvar(Pedido pedido) {
        // Carrega a linha existente (se houver) para o Hibernate gerar só o UPDATE do que mudou.
        PedidoJpa linha = jpa.findById(pedido.id().toString()).orElseGet(() -> new PedidoJpa(pedido.id().toString()));
        PedidoMapper.paraJpa(pedido, linha);
        jpa.save(linha);
    }

    @Override
    public Optional<Pedido> buscarPorId(PedidoId id) {
        return jpa.findById(id.toString()).map(PedidoMapper::paraDominio);
    }

    @Override
    public List<Pedido> listarPorCliente(ClienteId cliente) {
        return jpa.findByClienteIdOrderByCriadoEmDesc(cliente.valor()).stream().map(PedidoMapper::paraDominio).toList();
    }

    @Override
    public List<Pedido> listarAguardandoPagamentoCriadosAte(Instant criadosAte, int quantidade) {
        return jpa.findByStatusAndCriadoEmLessThanEqualOrderByCriadoEmAsc(StatusPedido.AGUARDANDO_PAGAMENTO, criadosAte,
                PageRequest.of(0, quantidade)).stream().map(PedidoMapper::paraDominio).toList();
    }

    @Override
    public Pagina<Pedido> buscar(Criterios criterios, int pagina, int tamanho) {
        Page<PedidoJpa> resultado = jpa.findAll(especificacao(criterios),
                PageRequest.of(pagina, tamanho, Sort.by(Sort.Direction.DESC, "criadoEm").and(Sort.by("id"))));
        return new Pagina<>(resultado.getContent().stream().map(PedidoMapper::paraDominio).toList(), pagina, tamanho,
                resultado.getTotalElements());
    }

    @Override
    public long contar(Criterios criterios) {
        return jpa.count(especificacao(criterios));
    }

    /** Monta o WHERE só com os filtros informados. O id no desempate deixa a paginação estável. */
    private static Specification<PedidoJpa> especificacao(Criterios criterios) {
        return (raiz, consulta, cb) -> {
            var condicoes = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (!criterios.status().isEmpty()) {
                condicoes.add(raiz.get("status").in(criterios.status()));
            }
            if (criterios.criadosDesde() != null) {
                condicoes.add(cb.greaterThanOrEqualTo(raiz.get("criadoEm"), criterios.criadosDesde()));
            }
            if (criterios.criadosAntes() != null) {
                condicoes.add(cb.lessThan(raiz.get("criadoEm"), criterios.criadosAntes()));
            }
            return cb.and(condicoes.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }
}
