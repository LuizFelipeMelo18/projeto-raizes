package br.com.raizes.pedidos.application;

import br.com.raizes.auditoria.application.RegistrarAuditoriaService;
import br.com.raizes.catalogo.infrastructure.jpa.CardapioItemJpaRepository;
import br.com.raizes.catalogo.infrastructure.jpa.UnidadeJpaRepository;
import br.com.raizes.estoque.application.ReservaEstoqueService;
import br.com.raizes.fidelidade.application.FidelidadeService;
import br.com.raizes.identidade.domain.UsuarioAutenticado;
import br.com.raizes.pedidos.domain.CanalPedido;
import br.com.raizes.pedidos.domain.ModalidadeAtendimento;
import br.com.raizes.pedidos.domain.StatusPedido;
import br.com.raizes.pedidos.infrastructure.IdempotenciaPedidoLock;
import br.com.raizes.pedidos.infrastructure.jpa.ItemPedidoEntity;
import br.com.raizes.pedidos.infrastructure.jpa.ItemPedidoJpaRepository;
import br.com.raizes.pedidos.infrastructure.jpa.PedidoEntity;
import br.com.raizes.pedidos.infrastructure.jpa.PedidoJpaRepository;
import br.com.raizes.shared.domain.AcessoNegadoException;
import br.com.raizes.shared.domain.ConflitoException;
import br.com.raizes.shared.domain.RecursoNaoEncontradoException;
import br.com.raizes.shared.domain.ValidacaoException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CriarPedidoService {
    private static final SecureRandom ALEATORIO = new SecureRandom();
    private final PedidoJpaRepository pedidos;
    private final ItemPedidoJpaRepository itens;
    private final CardapioItemJpaRepository cardapio;
    private final UnidadeJpaRepository unidades;
    private final ReservaEstoqueService estoque;
    private final FidelidadeService fidelidade;
    private final IdempotenciaPedidoLock lock;
    private final RegistrarAuditoriaService auditoria;

    public CriarPedidoService(PedidoJpaRepository pedidos, ItemPedidoJpaRepository itens,
                             CardapioItemJpaRepository cardapio, UnidadeJpaRepository unidades,
                             ReservaEstoqueService estoque, FidelidadeService fidelidade,
                             IdempotenciaPedidoLock lock, RegistrarAuditoriaService auditoria) {
        this.pedidos = pedidos;
        this.itens = itens;
        this.cardapio = cardapio;
        this.unidades = unidades;
        this.estoque = estoque;
        this.fidelidade = fidelidade;
        this.lock = lock;
        this.auditoria = auditoria;
    }

    @Transactional
    public Resultado criar(Entrada entrada, UUID chave, UsuarioAutenticado ator, UUID requestId) {
        if (chave == null) {
            throw new ValidacaoException("IDEMPOTENCY_KEY_OBRIGATORIA", "Idempotency-Key UUID é obrigatória.");
        }
        if (entrada.unidadeId() == null || entrada.unidadeId() < 1 || entrada.itens() == null
                || entrada.itens().isEmpty() || entrada.itens().size() > 50) {
            throw new ValidacaoException("PEDIDO_INVALIDO", "Unidade e de um a cinquenta itens são obrigatórios.");
        }
        CanalPedido canal = enumObrigatorio(CanalPedido.class, entrada.canalPedido(), "CANAL_PEDIDO_INVALIDO");
        ModalidadeAtendimento modalidade = enumObrigatorio(ModalidadeAtendimento.class,
                entrada.modalidadeAtendimento(), "MODALIDADE_INVALIDA");
        if (!"MOCK".equals(entrada.formaPagamento())) {
            throw new ValidacaoException("FORMA_PAGAMENTO_INVALIDA", "A forma de pagamento deve ser MOCK.");
        }
        exigirOrigem(ator, entrada.unidadeId(), canal);
        var ids = new HashSet<Long>();
        for (EntradaItem item : entrada.itens()) {
            if (item == null || item.produtoId() == null || item.produtoId() < 1
                    || item.quantidade() == null || item.quantidade() < 1 || item.quantidade() > 1000
                    || !ids.add(item.produtoId())) {
                throw new ValidacaoException("ITEM_INVALIDO", "Itens devem ser únicos, positivos e ter quantidade até 1000.");
            }
        }
        int pontosResgate = entrada.pontosResgate() == null ? 0 : entrada.pontosResgate();
        String hash = hash(ator.id(), entrada.unidadeId(), canal, modalidade, entrada.itens(), pontosResgate);
        lock.bloquear(chave);
        var existente = pedidos.findByChaveIdempotencia(chave);
        if (existente.isPresent()) {
            var pedido = existente.get();
            if (!hash.equals(pedido.getHashRequisicao()) || !ator.id().equals(pedido.getCriadoPorId())) {
                throw new ConflitoException("IDEMPOTENCIA_CONFLITO", "Chave já usada com conteúdo diferente.");
            }
            return new Resultado(resposta(pedido), true);
        }
        unidades.findByIdAndAtivaTrue(entrada.unidadeId()).orElseThrow(() ->
                new RecursoNaoEncontradoException("UNIDADE_NAO_ENCONTRADA", "Unidade ativa não encontrada."));
        var precos = cardapio.ativosPorIds(entrada.unidadeId(), ids).stream()
                .collect(Collectors.toMap(c -> c.getId().getProdutoId(), c -> c.getPreco()));
        if (precos.size() != ids.size()) {
            throw new ConflitoException("ITEM_INDISPONIVEL", "Produto fora do cardápio ativo da unidade.");
        }
        List<ItemPedidoEntity> linhas = entrada.itens().stream()
                .map(item -> new ItemPedidoEntity(null, item.produtoId(), item.quantidade(), precos.get(item.produtoId())))
                .toList();
        BigDecimal subtotal = linhas.stream().map(ItemPedidoEntity::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (subtotal.compareTo(new BigDecimal("9999999999.99")) > 0) {
            throw new ValidacaoException("TOTAL_INVALIDO", "Total excede o limite monetário.");
        }
        Long clienteId = ator.perfil() == br.com.raizes.identidade.domain.Perfil.CLIENTE ? ator.id() : null;
        var prepFidelidade = fidelidade.prepararCriacao(clienteId, subtotal, pontosResgate);
        BigDecimal total = prepFidelidade.totalAPagar();
        String codigo = clienteId == null ? gerarCodigo() : null;
        var pedido = pedidos.saveAndFlush(new PedidoEntity(entrada.unidadeId(), clienteId, ator.id(),
                canal, modalidade, total, prepFidelidade.descontoFidelidade(),
                prepFidelidade.pontosResgatados(), prepFidelidade.participaFidelidade(),
                chave, hash, codigo));
        fidelidade.registrarResgate(clienteId, pedido.getId(), pedido.getUnidadeId(),
                prepFidelidade.pontosResgatados(), ator.id(), requestId);
        // O pedido precisa existir antes de reservar, pois cada movimento referencia seu ID.
        var persistidos = entrada.itens().stream()
                .map(item -> new ItemPedidoEntity(pedido.getId(), item.produtoId(), item.quantidade(),
                        precos.get(item.produtoId())))
                .toList();
        itens.saveAll(persistidos);
        Map<Long, Integer> quantidades = entrada.itens().stream()
                .collect(Collectors.toMap(EntradaItem::produtoId, EntradaItem::quantidade));
        estoque.reservar(entrada.unidadeId(), quantidades, pedido.getId(), ator.id());
        auditoria.registrar(ator.id(), clienteId, pedido.getId(), pedido.getUnidadeId(),
                "CRIACAO_PEDIDO", "SUCESSO", "PEDIDO", requestId);
        return new Resultado(new Resposta(pedido.getId(), pedido.getUnidadeId(), canal, modalidade,
                pedido.getStatus(), subtotal, prepFidelidade.descontoFidelidade(),
                prepFidelidade.pontosResgatados(), prepFidelidade.participaFidelidade(), total,
                persistidos.stream().map(this::itemResposta).toList(),
                pedido.getCriadoEm(), codigo), false);
    }

    private Resposta resposta(PedidoEntity pedido) {
        return new Resposta(pedido.getId(), pedido.getUnidadeId(), pedido.getCanalPedido(),
                pedido.getModalidadeAtendimento(), pedido.getStatus(), pedido.getSubtotalItens(),
                pedido.getDescontoFidelidade(), pedido.getPontosResgatados(), pedido.isParticipaFidelidade(),
                pedido.getTotal(),
                itens.findByPedidoIdOrderById(pedido.getId()).stream().map(this::itemResposta).toList(),
                pedido.getCriadoEm(), pedido.getCodigoAcompanhamento());
    }

    private ItemResposta itemResposta(ItemPedidoEntity item) {
        return new ItemResposta(item.getProdutoId(), item.getQuantidade(), item.getPrecoUnitario(), item.getSubtotal());
    }

    private void exigirOrigem(UsuarioAutenticado ator, Long unidadeId, CanalPedido canal) {
        boolean permitido = switch (ator.perfil()) {
            case CLIENTE -> canal == CanalPedido.APP || canal == CanalPedido.WEB || canal == CanalPedido.PICKUP;
            case TOTEM -> canal == CanalPedido.TOTEM && unidadeId.equals(ator.unidadeId());
            case ATENDENTE -> (canal == CanalPedido.BALCAO || canal == CanalPedido.PICKUP)
                    && unidadeId.equals(ator.unidadeId());
            default -> false;
        };
        if (!permitido) throw new AcessoNegadoException();
    }

    private static <E extends Enum<E>> E enumObrigatorio(Class<E> tipo, String valor, String codigo) {
        try {
            return Enum.valueOf(tipo, valor);
        } catch (RuntimeException ex) {
            throw new ValidacaoException(codigo, "Valor obrigatório ou inválido: " + tipo.getSimpleName());
        }
    }

    private String hash(Long atorId, Long unidadeId, CanalPedido canal, ModalidadeAtendimento modalidade,
                        List<EntradaItem> produtos, int pontosResgate) {
        String itensCanonicos = produtos.stream().sorted((a, b) -> a.produtoId().compareTo(b.produtoId()))
                .map(i -> i.produtoId() + ":" + i.quantidade()).collect(Collectors.joining(","));
        String entrada = atorId + "|" + unidadeId + "|" + canal + "|" + modalidade + "|MOCK|"
                + itensCanonicos + (pontosResgate > 0 ? "|PONTOS:" + pontosResgate : "");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(entrada.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String gerarCodigo() {
        byte[] bytes = new byte[18];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record Entrada(Long unidadeId, String canalPedido, String modalidadeAtendimento,
                          List<EntradaItem> itens, String formaPagamento, Integer pontosResgate) { }
    public record EntradaItem(Long produtoId, Integer quantidade) { }
    public record ItemResposta(Long produtoId, int quantidade, BigDecimal precoUnitario, BigDecimal subtotal) { }
    public record Resposta(Long pedidoId, Long unidadeId, CanalPedido canalPedido,
                          ModalidadeAtendimento modalidadeAtendimento, StatusPedido status,
                          BigDecimal subtotalItens, BigDecimal descontoFidelidade,
                          int pontosResgatados, boolean participaFidelidade,
                          BigDecimal total, List<ItemResposta> itens, Instant criadoEm,
                          String codigoAcompanhamento) { }
    public record Resultado(Resposta body, boolean repeticao) { }
}
