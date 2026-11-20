package com.ruandiego.mini.catalogo.application.port.out;

import com.ruandiego.mini.catalogo.application.ArquivoImagem;

/**
 * Port de saída: onde os arquivos de imagem ficam guardados. O domínio só conhece a URL pública; se o arquivo
 * está em disco, num bucket S3 ou num CDN é decisão do adapter.
 */
public interface ArmazenamentoImagens {

    /** Guarda o arquivo com um nome novo (nunca sobrescreve outro) e devolve a URL pública absoluta. */
    String guardar(ArquivoImagem arquivo);
}
