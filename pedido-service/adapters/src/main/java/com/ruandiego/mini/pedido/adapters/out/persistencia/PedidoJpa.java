package com.ruandiego.mini.pedido.adapters.out.persistencia;

import com.ruandiego.mini.pedido.domain.model.StatusPedido;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Version;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Linha da tabela pedido (e seus itens). Só dados: a máquina de estados fica no Pedido do domínio. */
@Entity
@Table(name = "pedido")
class PedidoJpa {

    @Id
    @Column(columnDefinition = "char(36)")
    private String id;

    @Column(name = "cliente_id", nullable = false, length = 64)
    private String clienteId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatusPedido status;

    @Column(name = "frete_codigo", nullable = false, length = 30)
    private String freteCodigo;

    @Column(name = "frete_nome", nullable = false, length = 60)
    private String freteNome;

    @Column(name = "frete_valor", nullable = false, precision = 12, scale = 2)
    private BigDecimal freteValor;

    @Column(name = "frete_prazo_dias", nullable = false)
    private int fretePrazoDias;

    @Column(name = "end_destinatario", nullable = false, length = 120)
    private String endDestinatario;

    @Column(name = "end_cep", nullable = false, columnDefinition = "char(8)")
    private String endCep;

    @Column(name = "end_logradouro", nullable = false, length = 160)
    private String endLogradouro;

    @Column(name = "end_numero", nullable = false, length = 20)
    private String endNumero;

    @Column(name = "end_complemento", length = 80)
    private String endComplemento;

    @Column(name = "end_bairro", nullable = false, length = 80)
    private String endBairro;

    @Column(name = "end_cidade", nullable = false, length = 80)
    private String endCidade;

    @Column(name = "end_uf", nullable = false, columnDefinition = "char(2)")
    private String endUf;

    @Column(name = "criado_em", nullable = false, columnDefinition = "datetime(6)")
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false, columnDefinition = "datetime(6)")
    private Instant atualizadoEm;

    /**
     * Controle de concorrência otimista: o Hibernate só grava se a linha ainda estiver na versão lida. Protege a
     * corrida entre o job de expiração e o pagamento aprovado: quem chegar depois falha em vez de sobrescrever.
     */
    @Version
    private Long versao;

    // EAGER porque o Pedido do domínio sempre vem com os itens.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "item_pedido", joinColumns = @JoinColumn(name = "pedido_id", columnDefinition = "char(36)"))
    @OrderColumn(name = "posicao")
    private List<ItemPedidoJpa> itens = new ArrayList<>();

    protected PedidoJpa() {
        // exigido pelo JPA
    }

    PedidoJpa(String id) {
        this.id = id;
    }

    /** Copia os campos de um pedido do domínio para esta linha (nova ou já existente). */
    void preencher(String clienteId, StatusPedido status, String freteCodigo, String freteNome, BigDecimal freteValor,
                   int fretePrazoDias, String[] endereco, Instant criadoEm, Instant atualizadoEm,
                   List<ItemPedidoJpa> novosItens) {
        this.clienteId = clienteId;
        this.status = status;
        this.freteCodigo = freteCodigo;
        this.freteNome = freteNome;
        this.freteValor = freteValor;
        this.fretePrazoDias = fretePrazoDias;
        this.endDestinatario = endereco[0];
        this.endCep = endereco[1];
        this.endLogradouro = endereco[2];
        this.endNumero = endereco[3];
        this.endComplemento = endereco[4];
        this.endBairro = endereco[5];
        this.endCidade = endereco[6];
        this.endUf = endereco[7];
        this.criadoEm = criadoEm;
        this.atualizadoEm = atualizadoEm;
        this.itens.clear();
        this.itens.addAll(novosItens);
    }

    String getId() {
        return id;
    }

    String getClienteId() {
        return clienteId;
    }

    StatusPedido getStatus() {
        return status;
    }

    String getFreteCodigo() {
        return freteCodigo;
    }

    String getFreteNome() {
        return freteNome;
    }

    BigDecimal getFreteValor() {
        return freteValor;
    }

    int getFretePrazoDias() {
        return fretePrazoDias;
    }

    String getEndDestinatario() {
        return endDestinatario;
    }

    String getEndCep() {
        return endCep;
    }

    String getEndLogradouro() {
        return endLogradouro;
    }

    String getEndNumero() {
        return endNumero;
    }

    String getEndComplemento() {
        return endComplemento;
    }

    String getEndBairro() {
        return endBairro;
    }

    String getEndCidade() {
        return endCidade;
    }

    String getEndUf() {
        return endUf;
    }

    Instant getCriadoEm() {
        return criadoEm;
    }

    Instant getAtualizadoEm() {
        return atualizadoEm;
    }

    List<ItemPedidoJpa> getItens() {
        return itens;
    }
}
