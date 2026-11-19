package com.ruandiego.mini.carrinho.adapters.out.persistencia;

import com.ruandiego.mini.carrinho.application.contrato.CarrinhoClienteRepositoryContrato;
import com.ruandiego.mini.carrinho.application.port.out.CarrinhoClienteRepository;
import com.ruandiego.mini.carrinho.domain.model.Carrinho;
import com.ruandiego.mini.carrinho.domain.model.ClienteId;
import com.ruandiego.mini.carrinho.domain.model.ProdutoId;
import com.ruandiego.mini.kernel.Dinheiro;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O adapter JPA do carrinho do cliente contra MySQL 8 de verdade, rodando o mesmo contrato do adapter em
 * memória. Sem transação de teste: o @Transactional do @DataJpaTest não vale para métodos herdados do
 * contrato, e o adapter já abre a própria transação. Os dados são apagados no @AfterEach.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({MySqlDeTeste.class, CarrinhoClienteRepositoryJpa.class})
class CarrinhoClienteRepositoryJpaTest extends CarrinhoClienteRepositoryContrato {

    @Autowired
    private CarrinhoClienteRepositoryJpa clientes;

    @Autowired
    private JdbcTemplate jdbc;

    @Override
    protected CarrinhoClienteRepository clientes() {
        return clientes;
    }

    @AfterEach
    void limpar() {
        MySqlDeTeste.limpar(jdbc);
    }

    @Test
    void carrinhoNovoParaOMesmoClienteSubstituiOAntigo() {
        ClienteId ana = new ClienteId("ana");
        Carrinho antigo = Carrinho.criar();
        antigo.adicionarItem(new ProdutoId(UUID.randomUUID()), "Esfera", Dinheiro.de("10.00"), 1);
        clientes.salvar(ana, antigo);

        Carrinho novo = Carrinho.criar();
        novo.adicionarItem(new ProdutoId(UUID.randomUUID()), "Cubo", Dinheiro.de("20.00"), 2);
        clientes.salvar(ana, novo);

        assertThat(clientes.buscar(ana).orElseThrow().id()).isEqualTo(novo.id());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM carrinho", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM item_carrinho", Integer.class)).isEqualTo(1);
    }
}
