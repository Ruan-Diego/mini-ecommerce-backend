package com.ruandiego.mini.carrinho.adapters.out.persistencia;

import com.ruandiego.mini.carrinho.application.contrato.CupomRepositoryContrato;
import com.ruandiego.mini.carrinho.application.port.out.CupomRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Os cupons de demonstração vêm da migração V2: o contrato confere que o adapter os lê direito. */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({MySqlDeTeste.class, CupomRepositoryJpa.class})
class CupomRepositoryJpaTest extends CupomRepositoryContrato {

    @Autowired
    private CupomRepositoryJpa cupons;

    @Override
    protected CupomRepository cupons() {
        return cupons;
    }
}
