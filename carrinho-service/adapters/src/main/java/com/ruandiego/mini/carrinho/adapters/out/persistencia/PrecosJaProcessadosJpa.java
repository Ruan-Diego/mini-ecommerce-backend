package com.ruandiego.mini.carrinho.adapters.out.persistencia;

import com.ruandiego.mini.carrinho.application.port.out.PrecosJaProcessados;
import com.ruandiego.mini.carrinho.domain.model.ProdutoId;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Adapter real do port PrecosJaProcessados (MySQL). Pressupõe um consumidor por vez por produto (a fila
 * tem um consumidor): duas instâncias gravando o primeiro evento do mesmo produto ao mesmo tempo
 * esbarrariam na chave primária, e a mensagem seria reentregue.
 */
@Repository
public class PrecosJaProcessadosJpa implements PrecosJaProcessados {

    private final PrecoProcessadoJpaRepository jpa;

    PrecosJaProcessadosJpa(PrecoProcessadoJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean jaProcessado(ProdutoId produtoId, Instant ocorridoEm) {
        return jpa.findById(produtoId.toString())
                .map(ultimo -> !ocorridoEm.isAfter(ultimo.getOcorridoEm()))
                .orElse(false);
    }

    @Override
    @Transactional
    public void registrar(ProdutoId produtoId, Instant ocorridoEm) {
        jpa.findById(produtoId.toString()).ifPresentOrElse(
                ultimo -> {
                    if (ocorridoEm.isAfter(ultimo.getOcorridoEm())) {
                        ultimo.setOcorridoEm(ocorridoEm);
                    }
                },
                () -> jpa.save(new PrecoProcessadoJpa(produtoId.toString(), ocorridoEm)));
    }
}
