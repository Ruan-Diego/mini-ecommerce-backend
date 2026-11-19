package com.ruandiego.mini.carrinho.adapters.out.persistencia;

import com.ruandiego.mini.carrinho.application.port.out.CarrinhoClienteRepository;
import com.ruandiego.mini.carrinho.domain.model.Carrinho;
import com.ruandiego.mini.carrinho.domain.model.ClienteId;
import com.ruandiego.mini.carrinho.domain.model.ProdutoId;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Adapter real do port CarrinhoClienteRepository (MySQL).
 *
 * A transação fica aqui, no adapter: o service da application não tem Spring, e cada operação do
 * port precisa ser atômica por si (salvar apaga e regrava os itens de uma vez).
 */
@Repository
public class CarrinhoClienteRepositoryJpa implements CarrinhoClienteRepository {

    private final CarrinhoJpaRepository jpa;

    CarrinhoClienteRepositoryJpa(CarrinhoJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Carrinho> buscar(ClienteId cliente) {
        return jpa.findByClienteId(cliente.valor()).map(CarrinhoMapper::paraDominio);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClienteId> clientesComProduto(ProdutoId produto) {
        return jpa.findClienteIdsByProdutoId(produto.toString()).stream().map(ClienteId::new).toList();
    }

    @Override
    @Transactional
    public void salvar(ClienteId cliente, Carrinho carrinho) {
        String id = carrinho.id().toString();
        // O cliente só pode ter um carrinho. Se o que está gravado é outro (id diferente), ele é substituído.
        jpa.findByClienteId(cliente.valor())
                .filter(existente -> !existente.getId().equals(id))
                .ifPresent(existente -> {
                    jpa.delete(existente);
                    jpa.flush();
                });
        jpa.save(CarrinhoMapper.paraJpa(cliente, carrinho));
    }
}
