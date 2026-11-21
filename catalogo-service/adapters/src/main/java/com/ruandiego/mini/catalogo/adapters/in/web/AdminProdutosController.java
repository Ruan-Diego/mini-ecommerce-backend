package com.ruandiego.mini.catalogo.adapters.in.web;

import com.ruandiego.mini.catalogo.application.ArquivoImagem;
import com.ruandiego.mini.catalogo.application.port.in.AdicionarImagemUseCase;
import com.ruandiego.mini.catalogo.application.port.in.AtualizarProdutoUseCase;
import com.ruandiego.mini.catalogo.application.port.in.CadastrarProdutoUseCase;
import com.ruandiego.mini.catalogo.application.port.in.DesativarProdutoUseCase;
import com.ruandiego.mini.catalogo.application.port.in.ListarProdutosAdminUseCase;
import com.ruandiego.mini.catalogo.domain.model.CategoriaId;
import com.ruandiego.mini.catalogo.domain.model.Imagem;
import com.ruandiego.mini.catalogo.domain.model.ProdutoId;
import com.ruandiego.mini.catalogo.domain.model.Sku;
import com.ruandiego.mini.catalogo.domain.model.Slug;
import com.ruandiego.mini.kernel.Dinheiro;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

/**
 * Administração de produtos. A proteção por papel (ADMIN no Keycloak) entra na Fase 6; por enquanto o
 * prefixo /admin só separa as rotas para o gateway poder proteger tudo de uma vez.
 */
@RestController
@RequestMapping("/admin/produtos")
@Tag(name = "Admin de produtos", description = "Lista, cadastro, atualização e desativação")
class AdminProdutosController {

    private final CadastrarProdutoUseCase cadastrar;
    private final AtualizarProdutoUseCase atualizar;
    private final DesativarProdutoUseCase desativar;
    private final AdicionarImagemUseCase imagens;
    private final ListarProdutosAdminUseCase lista;

    AdminProdutosController(CadastrarProdutoUseCase cadastrar, AtualizarProdutoUseCase atualizar,
                            DesativarProdutoUseCase desativar, AdicionarImagemUseCase imagens,
                            ListarProdutosAdminUseCase lista) {
        this.cadastrar = cadastrar;
        this.atualizar = atualizar;
        this.desativar = desativar;
        this.imagens = imagens;
        this.lista = lista;
    }

    @GetMapping
    @Operation(summary = "Lista os produtos, inclusive desativados; a busca acha por nome, descrição ou SKU")
    PaginaResposta<ProdutoResposta> listar(@RequestParam(required = false) String busca,
                                           @RequestParam(required = false) Integer pagina,
                                           @RequestParam(required = false) Integer tamanho) {
        return PaginaResposta.de(lista.listarParaAdmin(new ListarProdutosAdminUseCase.Filtro(busca, pagina, tamanho)),
                ProdutoResposta::de);
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

    /**
     * Upload de uma imagem (multipart: `arquivo` e `textoAlternativo`). Vai para o fim da galeria; o produto
     * aceita até 5. Para reordenar ou remover, use o PUT com a lista de imagens.
     */
    @PostMapping(path = "/{id}/imagens", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Envia uma imagem do produto (JPEG, PNG ou WebP, até 2 MB; no máximo 5 por produto)")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    ImagemJson enviarImagem(@PathVariable String id, @RequestPart("arquivo") MultipartFile arquivo,
                            @RequestParam("textoAlternativo") String textoAlternativo) {
        byte[] conteudo;
        try {
            conteudo = arquivo.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Imagem imagem = imagens.adicionarImagem(produtoId(id), ArquivoImagem.de(conteudo), textoAlternativo);
        return ImagemJson.de(imagem);
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
