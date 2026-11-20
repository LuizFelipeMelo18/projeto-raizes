package br.com.raizes.pedidos.api;

import br.com.raizes.identidade.domain.UsuarioAutenticado;
import br.com.raizes.pedidos.application.CriarPedidoService;
import br.com.raizes.pedidos.application.ListarPedidosService;
import br.com.raizes.shared.api.PageEnvelope;
import br.com.raizes.shared.api.RequestIds;
import br.com.raizes.shared.domain.ValidacaoException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pedidos")
public class PedidoCriacaoController {
    private final CriarPedidoService criacao;
    private final ListarPedidosService listagem;

    public PedidoCriacaoController(CriarPedidoService criacao, ListarPedidosService listagem) {
        this.criacao = criacao;
        this.listagem = listagem;
    }

    @PostMapping
    public ResponseEntity<CriarPedidoService.Resposta> criar(@RequestBody EntradaRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String chave,
            @AuthenticationPrincipal UsuarioAutenticado ator, HttpServletRequest request) {
        UUID uuid;
        try {
            uuid = UUID.fromString(chave);
        } catch (RuntimeException ex) {
            throw new ValidacaoException("IDEMPOTENCY_KEY_INVALIDA", "Idempotency-Key UUID é obrigatória.");
        }
        if (body == null || body.clienteId() != null || body.total() != null || body.descontoFidelidade() != null
                || body.subtotalItens() != null || body.itens() == null
                || body.itens().stream().anyMatch(i -> i == null || i.precoUnitario() != null || i.subtotal() != null)) {
            throw new ValidacaoException("PEDIDO_INVALIDO", "Cliente, preço e total são definidos pelo servidor.");
        }
        var entrada = new CriarPedidoService.Entrada(body.unidadeId(), body.canalPedido(),
                body.modalidadeAtendimento(), body.itens().stream()
                .map(i -> new CriarPedidoService.EntradaItem(i.produtoId(), i.quantidade())).toList(),
                body.formaPagamento(), body.pontosResgate());
        var resultado = criacao.criar(entrada, uuid, ator, RequestIds.from(request));
        URI location = URI.create("/api/v1/pedidos/" + resultado.body().pedidoId());
        return ResponseEntity.status(resultado.repeticao() ? 200 : 201)
                .location(location).header("Idempotency-Replayed", Boolean.toString(resultado.repeticao()))
                .body(resultado.body());
    }

    @GetMapping
    public PageEnvelope<ListarPedidosService.Resumo> listar(@RequestParam(required = false) Long unidadeId,
            @RequestParam(required = false) String canalPedido,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int limit,
            @AuthenticationPrincipal UsuarioAutenticado ator) {
        return PageEnvelope.from(listagem.listar(unidadeId, canalPedido, status, page, limit, ator));
    }

    @GetMapping("/acompanhar/{codigo}")
    public ListarPedidosService.Acompanhamento acompanhar(
            @org.springframework.web.bind.annotation.PathVariable String codigo) {
        return listagem.acompanhar(codigo);
    }

    public record EntradaRequest(Long unidadeId, String canalPedido, String modalidadeAtendimento,
                                 List<ItemRequest> itens, String formaPagamento, Integer pontosResgate,
                                 Long clienteId, java.math.BigDecimal total,
                                 java.math.BigDecimal subtotalItens, java.math.BigDecimal descontoFidelidade) { }
    public record ItemRequest(Long produtoId, Integer quantidade, java.math.BigDecimal precoUnitario,
                              java.math.BigDecimal subtotal) { }
}
