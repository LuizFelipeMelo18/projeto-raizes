package br.com.raizes.pedidos.api;

import br.com.raizes.identidade.domain.UsuarioAutenticado;
import br.com.raizes.pagamentos.application.PagamentoService;
import br.com.raizes.pedidos.application.TransicionarPedidoService;
import br.com.raizes.shared.api.RequestIds;
import br.com.raizes.shared.domain.ValidacaoException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pedidos/{id}")
public class PedidoFluxoController {
    private final PagamentoService pagamento;
    private final TransicionarPedidoService transicao;

    public PedidoFluxoController(PagamentoService pagamento, TransicionarPedidoService transicao) {
        this.pagamento = pagamento;
        this.transicao = transicao;
    }

    @PostMapping("/pagamentos")
    public ResponseEntity<PagamentoService.Resposta> pagar(@PathVariable Long id,
            @RequestHeader(value = "Idempotency-Key", required = false) String chave,
            @RequestBody CenarioRequest body, @AuthenticationPrincipal UsuarioAutenticado ator,
            HttpServletRequest request) {
        UUID uuid;
        try {
            uuid = UUID.fromString(chave);
        } catch (RuntimeException ex) {
            throw new ValidacaoException("IDEMPOTENCY_KEY_INVALIDA", "Idempotency-Key UUID é obrigatória.");
        }
        var resultado = pagamento.solicitar(id, uuid, body == null ? null : body.cenarioMock(),
                ator, RequestIds.from(request));
        return ResponseEntity.status(resultado.indeterminado() ? 202 : 200).body(resultado);
    }

    @PostMapping("/pagamentos/reconciliacao")
    public ResponseEntity<PagamentoService.Resposta> reconciliar(@PathVariable Long id,
            @AuthenticationPrincipal UsuarioAutenticado ator, HttpServletRequest request) {
        var resultado = pagamento.reconciliar(id, ator, RequestIds.from(request));
        return ResponseEntity.status(resultado.indeterminado() ? 202 : 200).body(resultado);
    }

    @PatchMapping("/status")
    public TransicionarPedidoService.Resposta mudar(@PathVariable Long id, @RequestBody StatusRequest body,
            @AuthenticationPrincipal UsuarioAutenticado ator, HttpServletRequest request) {
        return transicao.mudar(id, body == null ? null : body.status(), ator, RequestIds.from(request));
    }

    @PostMapping("/cancelamento")
    public TransicionarPedidoService.Resposta cancelar(@PathVariable Long id,
            @RequestBody(required = false) CancelamentoRequest body,
            @AuthenticationPrincipal UsuarioAutenticado ator, HttpServletRequest request) {
        return transicao.cancelar(id, body == null ? null : body.motivo(), ator, RequestIds.from(request));
    }

    public record CenarioRequest(String cenarioMock) { }
    public record StatusRequest(String status) { }
    public record CancelamentoRequest(String motivo) { }
}
