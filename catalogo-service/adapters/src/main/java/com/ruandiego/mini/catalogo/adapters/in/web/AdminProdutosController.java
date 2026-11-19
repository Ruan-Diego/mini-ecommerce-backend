package com.ruandiego.mini.catalogo.adapters.in.web;

import com.ruandiego.mini.catalogo.application.port.in.AtualizarProdutoUseCase;
import com.ruandiego.mini.catalogo.application.port.in.CadastrarProdutoUseCase;
import com.ruandiego.mini.catalogo.application.port.in.DesativarProdutoUseCase;
import com.ruandiego.mini.catalogo.domain.model.CategoriaId;
import com.ruandiego.mini.catalogo.domain.model.Imagem;
import com.ruandiego.mini.catalogo.domain.model.ProdutoId;
import com.ruandiego.mini.catalogo.domain.model.Sku;
import com.ruandiego.mini.catalogo.domain.model.Slug;
import com.ruandiego.mini.kernel.Dinheiro;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.Map;

/**
 * Administração de produtos. A proteção por papel (ADMIN no Keycloak) entra na Fase 6; por enquanto o
 * prefixo /admin só separa as rotas para o gateway poder proteger tudo de uma vez.
 */
@RestController
@RequestMapping("/admin/produtos")
@Tag(name = "Admin de produtos", description = "Cadastro, atualização e desativação")
class AdminProdutosController {

    private final CadastrarProdutoUseCase cadastrar;
    private final AtualizarProdutoUseCase atualizar;
    private final DesativarProdutoUseCase desativar;

    AdminProdutosController(CadastrarProdutoUseCase cadastrar, AtualizarProdutoUseCase atualizar,
                            DesativarProdutoUseCase desativar) {
        this.cadastrar = cadastrar;
        this.atualizar = atualizar;
        this.desativar = desativar;
    }

    @PostMapping
    @Operation(summary = "Cadastra um produto (slug opcional: gerado do nome)")
    ResponseEntity<Map<String, String>> cadastrar(@Valid @RequestBody ProdutoRequisicao req) {
        ProdutoId id = cadastrar.cadastrar(new CadastrarProdutoUseCase.Comando(
                new Sku(req.sku()), req.nome(),
                req.slug() == null || req.slug().isBlank() ? null : new Slug(req.slug()),
                req.descricao(), new Dinheiro(req.preco()), imagens(req), categoriaId(req)));
        var local = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(id).toUri();
        return ResponseEntity.created(local).body(Map.of("id", id.toString()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualiza dados e preço (SKU e slug não mudam)")
    ResponseEntity<Void> atualizar(@PathVariable String id, @Valid @RequestBody ProdutoRequisicao req) {
        atualizar.atualizar(new AtualizarProdutoUseCase.Comando(produtoId(id), req.nome(), req.descricao(),
                new Dinheiro(req.preco()), imagens(req), categoriaId(req)));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Desativa o produto (não apaga: pedidos antigos apontam para ele)")
    ResponseEntity<Void> desativar(@PathVariable String id) {
        desativar.desativar(produtoId(id));
        return ResponseEntity.noContent().build();
    }

    private static List<Imagem> imagens(ProdutoRequisicao req) {
        return req.imagens().stream().map(ImagemJson::paraDominio).toList();
    }

    private static CategoriaId categoriaId(ProdutoRequisicao req) {
        return RequisicaoInvalidaException.converter("categoriaId", req.categoriaId(), CategoriaId::de);
    }

    private static ProdutoId produtoId(String id) {
        return RequisicaoInvalidaException.converter("id", id, ProdutoId::de);
    }
}
