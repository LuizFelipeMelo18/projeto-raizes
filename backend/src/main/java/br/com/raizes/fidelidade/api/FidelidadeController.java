package br.com.raizes.fidelidade.api;

import br.com.raizes.fidelidade.application.FidelidadeService;
import br.com.raizes.identidade.application.IdentidadeService;
import br.com.raizes.identidade.domain.UsuarioAutenticado;
import br.com.raizes.shared.api.PageEnvelope;
import br.com.raizes.shared.api.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fidelidade")
public class FidelidadeController {
    private final FidelidadeService fidelidade;
    private final IdentidadeService identidade;

    public FidelidadeController(FidelidadeService fidelidade, IdentidadeService identidade) {
        this.fidelidade = fidelidade;
        this.identidade = identidade;
    }

    @GetMapping("/consentimento")
    public IdentidadeService.ConsentimentoEstado consultarConsentimento(
            @AuthenticationPrincipal UsuarioAutenticado principal, HttpServletRequest request) {
        return identidade.consultarConsentimento(principal, RequestIds.from(request));
    }

    @PutMapping("/consentimento")
    public IdentidadeService.ConsentimentoEstado atualizarConsentimento(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @Valid @RequestBody ConsentimentoRequest body, HttpServletRequest request) {
        return identidade.registrarConsentimento(principal, body.consentido(), RequestIds.from(request));
    }

    @GetMapping("/saldo")
    public FidelidadeService.ResumoSaldo saldo(@AuthenticationPrincipal UsuarioAutenticado principal,
                                               HttpServletRequest request) {
        return fidelidade.consultarSaldo(principal, RequestIds.from(request));
    }

    @GetMapping("/movimentacoes")
    public PageEnvelope<FidelidadeService.MovimentoView> movimentacoes(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            HttpServletRequest request) {
        return PageEnvelope.from(fidelidade.listarMovimentos(principal, page, limit, RequestIds.from(request)));
    }

    public record ConsentimentoRequest(@NotNull Boolean consentido) { }
}

