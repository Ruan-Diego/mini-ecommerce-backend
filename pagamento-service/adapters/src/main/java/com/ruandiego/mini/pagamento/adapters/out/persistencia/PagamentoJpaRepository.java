package com.ruandiego.mini.pagamento.adapters.out.persistencia;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

interface PagamentoJpaRepository extends JpaRepository<PagamentoJpa, String> {

    Optional<PagamentoJpa> findByTransacaoId(String transacaoId);

    List<PagamentoJpa> findByPedidoIdOrderByCriadoEmDesc(String pedidoId);

    /** Pix pendente cujo prazo já passou em `agora`, do que venceu primeiro ao que venceu depois. */
    @Query("""
            select p from PagamentoJpa p
            where p.status = com.ruandiego.mini.pagamento.domain.model.StatusPagamento.PENDENTE
              and p.metodo = com.ruandiego.mini.pagamento.domain.model.MetodoPagamento.PIX
              and p.pixExpiraEm <= :agora
            order by p.pixExpiraEm asc""")
    List<PagamentoJpa> pixVencidos(@Param("agora") Instant agora, Pageable limite);
}
