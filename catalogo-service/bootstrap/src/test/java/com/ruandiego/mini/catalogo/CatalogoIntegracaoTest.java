package com.ruandiego.mini.catalogo;

import com.ruandiego.mini.catalogo.application.port.in.AtualizarProdutoUseCase;
import com.ruandiego.mini.catalogo.application.port.in.CadastrarProdutoUseCase;
import com.ruandiego.mini.catalogo.application.port.in.ObterProdutoUseCase;
import com.ruandiego.mini.catalogo.application.port.out.CategoriaRepository;
import com.ruandiego.mini.catalogo.application.port.out.PublicadorEventos;
import com.ruandiego.mini.catalogo.domain.model.Categoria;
import com.ruandiego.mini.catalogo.domain.model.ProdutoId;
import com.ruandiego.mini.catalogo.domain.model.Sku;
import com.ruandiego.mini.catalogo.domain.model.Slug;
import com.ruandiego.mini.kernel.Dinheiro;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.containers.MySQLContainer;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sobe o catalogo-service inteiro (com o application.yml real) contra um MySQL 8 em container:
 * o Flyway cria o schema, o Hibernate valida as entidades e os use cases chegam pelos ports de entrada.
 */
@SpringBootTest
class CatalogoIntegracaoTest {

    /** Publicador de teste que pode ser mandado falhar, para provar que a transação do use case é desfeita. */
    static final AtomicBoolean PUBLICADOR_FALHA = new AtomicBoolean();

    @TestConfiguration(proxyBeanMethods = false)
    static class Infra {

        @Bean
        @ServiceConnection
        MySQLContainer<?> mysql() {
            return new MySQLContainer<>("mysql:8.4");
        }

        @Bean
        @Primary
        PublicadorEventos publicadorQuePodeFalhar() {
            return eventos -> {
                if (PUBLICADOR_FALHA.get()) {
                    throw new IllegalStateException("falha simulada ao publicar");
                }
            };
        }
    }

    @Autowired
    private CadastrarProdutoUseCase cadastrar;

    @Autowired
    private AtualizarProdutoUseCase atualizar;

    @Autowired
    private ObterProdutoUseCase obter;

    @Autowired
    private CategoriaRepository categorias;

    @Test
    void cadastraEObtemPeloSlug() {
        Categoria esferas = Categoria.criar("Esferas", null, null);
        categorias.salvar(esferas);

        cadastrar.cadastrar(new CadastrarProdutoUseCase.Comando(new Sku("ESF-CHROME"), "Esfera Chrome", null, "",
                Dinheiro.de("289.00"), List.of(), esferas.id()));

        assertThat(obter.obterPorSlug(new Slug("esfera-chrome")).preco()).isEqualTo(Dinheiro.de("289.00"));
    }

    /**
     * Mudar o preço grava o produto e depois publica PrecoAlterado. Se a publicação falha, a gravação
     * também precisa ser desfeita: é o que a transação amarrada no CatalogoConfig garante. Sem ela, o
     * save do repositório já teria sido confirmado sozinho e o preço novo ficaria no banco sem evento.
     */
    @Test
    void falhaAoPublicarDesfazAGravacao() {
        Categoria aneis = Categoria.criar("Anéis", null, null);
        categorias.salvar(aneis);
        ProdutoId id = cadastrar.cadastrar(new CadastrarProdutoUseCase.Comando(new Sku("ANL-IRIS"), "Anel Íris",
                null, "", Dinheiro.de("219.00"), List.of(), aneis.id()));

        PUBLICADOR_FALHA.set(true);
        try {
            assertThatThrownBy(() -> atualizar.atualizar(new AtualizarProdutoUseCase.Comando(id, "Anel Íris", "",
                    Dinheiro.de("199.00"), List.of(), aneis.id())))
                    .hasMessageContaining("falha simulada");
        } finally {
            PUBLICADOR_FALHA.set(false);
        }

        assertThat(obter.obterPorSlug(new Slug("anel-iris")).preco()).isEqualTo(Dinheiro.de("219.00"));
    }
}
