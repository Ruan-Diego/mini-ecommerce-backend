package com.ruandiego.mini.catalogo;

import com.ruandiego.mini.catalogo.application.port.out.BrokerMensagens;
import com.ruandiego.mini.catalogo.application.port.out.CategoriaRepository;
import com.ruandiego.mini.catalogo.application.port.out.Outbox;
import com.ruandiego.mini.catalogo.application.port.out.ProdutoRepository;
import com.ruandiego.mini.catalogo.application.port.out.PublicadorEventos;
import com.ruandiego.mini.catalogo.application.port.out.ArmazenamentoImagens;
import com.ruandiego.mini.catalogo.application.service.AdministracaoProdutosService;
import com.ruandiego.mini.catalogo.application.service.ImagensProdutoService;
import com.ruandiego.mini.catalogo.application.service.ConsultaCatalogoService;
import com.ruandiego.mini.catalogo.application.service.RelayDoOutbox;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.interceptor.DefaultTransactionAttribute;
import org.springframework.transaction.interceptor.MatchAlwaysTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.time.Clock;

/**
 * Wiring do catálogo: liga os services (que não conhecem Spring) aos adapters.
 *
 * Os services não têm @Transactional, porque a camada application não depende de Spring. A transação
 * entra aqui: cada service vira um proxy que abre uma transação por chamada de use case. Assim, todas as
 * gravações de um use case (produto + eventos) são confirmadas ou desfeitas juntas.
 */
@Configuration(proxyBeanMethods = false)
class CatalogoConfig {

    @Bean
    Clock relogio() {
        return Clock.systemUTC();
    }

    /** Implementa BuscarProdutos, ObterProduto e ListarCategorias. Só leitura. */
    @Bean
    ConsultaCatalogoService consultaCatalogo(ProdutoRepository produtos, CategoriaRepository categorias,
                                             PlatformTransactionManager transacoes) {
        return transacional(new ConsultaCatalogoService(produtos, categorias), transacoes, true);
    }

    /** Implementa CadastrarProduto, AtualizarProduto e DesativarProduto. */
    @Bean
    AdministracaoProdutosService administracaoProdutos(ProdutoRepository produtos, CategoriaRepository categorias,
                                                       PublicadorEventos publicador, Clock relogio,
                                                       PlatformTransactionManager transacoes) {
        return transacional(new AdministracaoProdutosService(produtos, categorias, publicador, relogio),
                transacoes, false);
    }

    /** Implementa AdicionarImagem (upload). Transacional: a imagem entra na galeria junto com o salvamento. */
    @Bean
    ImagensProdutoService imagensProduto(ProdutoRepository produtos, ArmazenamentoImagens armazenamento,
                                         PlatformTransactionManager transacoes) {
        return transacional(new ImagensProdutoService(produtos, armazenamento), transacoes, false);
    }

    /**
     * Relay da outbox: lê o que já foi commitado, publica no RabbitMQ e dá baixa mensagem a mensagem (cada baixa é
     * uma transação curta no adapter). Não é transacional como um todo, para não segurar o banco durante a rede.
     * É ele que leva `PrecoAlterado` ao carrinho: antes só havia um log provisório.
     */
    @Bean
    RelayDoOutbox relayDoOutbox(Outbox outbox, BrokerMensagens broker, Clock relogio) {
        return new RelayDoOutbox(outbox, broker, relogio);
    }

    /**
     * Envolve o service num proxy que roda cada método numa transação. O proxy é uma subclasse gerada em
     * tempo de execução: o tipo continua o do service, então ele é injetado por qualquer port de entrada
     * que o service implementa. Exceção de runtime (ex.: RegraDeNegocioException) desfaz a transação.
     */
    private static <T> T transacional(T service, PlatformTransactionManager transacoes, boolean somenteLeitura) {
        DefaultTransactionAttribute atributo = new DefaultTransactionAttribute();
        atributo.setReadOnly(somenteLeitura);
        MatchAlwaysTransactionAttributeSource regra = new MatchAlwaysTransactionAttributeSource();
        regra.setTransactionAttribute(atributo);

        ProxyFactory proxy = new ProxyFactory(service);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(transacoes, regra));
        @SuppressWarnings("unchecked")
        T transacional = (T) proxy.getProxy();
        return transacional;
    }
}
