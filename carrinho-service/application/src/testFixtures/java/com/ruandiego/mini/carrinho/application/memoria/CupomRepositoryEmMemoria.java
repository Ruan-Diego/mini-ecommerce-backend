package com.ruandiego.mini.carrinho.application.memoria;

import com.ruandiego.mini.carrinho.application.port.out.CupomRepository;
import com.ruandiego.mini.carrinho.domain.model.Cupom;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Adapter em memória do CupomRepository, para testes. O port só lê; {@code adicionar} existe para montar cenários. */
public class CupomRepositoryEmMemoria implements CupomRepository {

    private final Map<String, Cupom> dados = new HashMap<>();

    public void adicionar(Cupom cupom) {
        dados.put(cupom.codigo(), cupom);
    }

    public void limpar() {
        dados.clear();
    }

    @Override
    public Optional<Cupom> buscarPorCodigo(String codigo) {
        if (codigo == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(dados.get(codigo.trim().toUpperCase(java.util.Locale.ROOT)));
    }
}
