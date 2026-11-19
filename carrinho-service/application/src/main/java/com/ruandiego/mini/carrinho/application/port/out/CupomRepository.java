package com.ruandiego.mini.carrinho.application.port.out;

import com.ruandiego.mini.carrinho.domain.model.Cupom;

import java.util.Optional;

/** Port de saída dos cupons cadastrados. O código é comparado sem diferenciar maiúsculas. */
public interface CupomRepository {

    Optional<Cupom> buscarPorCodigo(String codigo);
}
