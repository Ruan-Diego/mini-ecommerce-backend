package com.ruandiego.mini.estoque.adapters.in.web;

import com.ruandiego.mini.estoque.application.memoria.ItemEstoqueRepositoryEmMemoria;
import com.ruandiego.mini.estoque.application.memoria.MensagensProcessadasEmMemoria;
import com.ruandiego.mini.estoque.application.memoria.OutboxEmMemoria;
import com.ruandiego.mini.estoque.application.memoria.ReservaRepositoryEmMemoria;
import com.ruandiego.mini.estoque.application.service.EstoqueService;
import com.ruandiego.mini.estoque.domain.model.ItemEstoque;
import com.ruandiego.mini.estoque.domain.model.ProdutoId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Controller sobre o service real e os adapters em memória: HTTP, validação e tradução de erros. */
@WebMvcTest(controllers = EstoqueController.class)
@Import(EstoqueControllerTest.Cenario.class)
class EstoqueControllerTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class Cenario {
        @Bean
        ItemEstoqueRepositoryEmMemoria itens() {
            return new ItemEstoqueRepositoryEmMemoria();
        }

        @Bean
        EstoqueService estoqueService(ItemEstoqueRepositoryEmMemoria itens) {
            return new EstoqueService(itens, new ReservaRepositoryEmMemoria(), new MensagensProcessadasEmMemoria(),
                    new OutboxEmMemoria(), Clock.systemUTC());
        }
    }

    private final String produto = UUID.randomUUID().toString();

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ItemEstoqueRepositoryEmMemoria itens;

    @BeforeEach
    void limpar() {
        itens.limpar();
    }

    @Test
    void consultaOSaldo() throws Exception {
        itens.salvar(ItemEstoque.reconstituir(ProdutoId.de(produto), 7, 2));

        mvc.perform(get("/estoque/" + produto))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.produtoId").value(produto))
                .andExpect(jsonPath("$.disponivel").value(7))
                .andExpect(jsonPath("$.reservado").value(2))
                .andExpect(jsonPath("$.emEstoque").value(true));
    }

    @Test
    void saldoZeradoNaoEstaEmEstoque() throws Exception {
        itens.salvar(ItemEstoque.reconstituir(ProdutoId.de(produto), 0, 3));

        mvc.perform(get("/estoque/" + produto))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emEstoque").value(false));
    }

    @Test
    void produtoSemEstoqueCadastradoDa404EmProblemJson() throws Exception {
        mvc.perform(get("/estoque/" + produto))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void idQueNaoEUuidDa400() throws Exception {
        mvc.perform(get("/estoque/abc")).andExpect(status().isBadRequest());
    }

    @Test
    void adminDefineOSaldoECriaOItemSeNaoExiste() throws Exception {
        mvc.perform(put("/admin/estoque/" + produto).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"disponivel\":25}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disponivel").value(25))
                .andExpect(jsonPath("$.reservado").value(0));

        mvc.perform(get("/estoque/" + produto)).andExpect(jsonPath("$.disponivel").value(25));
    }

    @Test
    void saldoNegativoOuAusenteDa400() throws Exception {
        mvc.perform(put("/admin/estoque/" + produto).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"disponivel\":-1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/admin/estoque/" + produto).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }
}
