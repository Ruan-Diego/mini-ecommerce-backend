package com.ruandiego.mini.carrinho.adapters.out.persistencia;

import com.ruandiego.mini.carrinho.application.port.out.CupomRepository;
import com.ruandiego.mini.carrinho.domain.model.Cupom;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

/** Adapter real do port CupomRepository (MySQL). O código é guardado em maiúsculas. */
@Repository
public class CupomRepositoryJpa implements CupomRepository {

    private final CupomJpaRepository jpa;

    CupomRepositoryJpa(CupomJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Cupom> buscarPorCodigo(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return Optional.empty();
        }
        return jpa.findById(codigo.trim().toUpperCase(Locale.ROOT)).map(CarrinhoMapper::paraDominio);
    }
}
