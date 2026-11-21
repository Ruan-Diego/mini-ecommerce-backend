package com.ruandiego.mini.catalogo.application.port.in;

import com.ruandiego.mini.catalogo.application.ArquivoImagem;
import com.ruandiego.mini.catalogo.domain.model.Imagem;
import com.ruandiego.mini.catalogo.domain.model.ProdutoId;

/** Upload de imagem: guarda o arquivo e acrescenta a imagem no fim da galeria do produto (até 5). */
public interface AdicionarImagemUseCase {

    Imagem adicionarImagem(ProdutoId produto, ArquivoImagem arquivo, String textoAlternativo);
}
