package br.com.raizes.estoque.api;

import br.com.raizes.estoque.application.EstoqueService;
import br.com.raizes.identidade.domain.UsuarioAutenticado;
import br.com.raizes.shared.api.PageEnvelope;
import br.com.raizes.shared.api.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/estoque")
public class EstoqueController {
    private final EstoqueService service;

    public EstoqueController(EstoqueService service) {
        this.service = service;
    }

    @GetMapping
    public PageEnvelope<EstoqueService.Saldo> saldos(@RequestParam(required = false) Long unidadeId,
                                                     @RequestParam(required = false) Long produtoId,
                                                     @RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "20") int limit,
                                                     @AuthenticationPrincipal UsuarioAutenticado ator) {
        return PageEnvelope.from(service.consultarSaldo(unidadeId, produtoId, page, limit, ator));
    }

    @GetMapping("/movimentacoes")
    public PageEnvelope<EstoqueService.Movimento> movimentos(@RequestParam(required = false) Long unidadeId,
                                                               @RequestParam(required = false) Long produtoId,
                                                               @RequestParam(defaultValue = "1") int page,
                                                               @RequestParam(defaultValue = "20") int limit,
                                                               @AuthenticationPrincipal UsuarioAutenticado ator) {
        return PageEnvelope.from(service.consultarMovimentos(unidadeId, produtoId, page, limit, ator));
    }

    @PostMapping("/movimentacoes")
    @ResponseStatus(HttpStatus.CREATED)
    public EstoqueService.MovimentoResultado movimentar(@Valid @RequestBody MovimentoRequest body,
                                                         @AuthenticationPrincipal UsuarioAutenticado ator,
                                                         HttpServletRequest request) {
        return service.movimentar(body.unidadeId(), body.produtoId(), body.tipo(), body.quantidade(),
                body.motivo(), ator, RequestIds.from(request));
    }

    public record MovimentoRequest(@NotNull @Positive Long unidadeId,
                                   @NotNull @Positive Long produtoId,
                                   @NotBlank String tipo,
                                   @NotNull @Positive Integer quantidade,
                                   @NotBlank @Size(max = 200) String motivo) { }
}
