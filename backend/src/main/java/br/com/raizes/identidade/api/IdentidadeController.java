package br.com.raizes.identidade.api;

import br.com.raizes.identidade.application.IdentidadeService;
import br.com.raizes.identidade.domain.UsuarioAutenticado;
import br.com.raizes.shared.api.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class IdentidadeController {
    private final IdentidadeService service;

    public IdentidadeController(IdentidadeService service) {
        this.service = service;
    }

    @PostMapping("/clientes")
    @ResponseStatus(HttpStatus.CREATED)
    public IdentidadeService.Cadastro cadastrar(@Valid @RequestBody CadastroRequest body,
                                                HttpServletRequest request) {
        return service.cadastrar(body.nome(), body.email(), body.senha(), body.consentimentoFidelidade(),
                RequestIds.from(request));
    }

    @PostMapping("/auth/login")
    public IdentidadeService.Login login(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
        return service.login(body.email(), body.senha(), RequestIds.from(request));
    }

    @GetMapping("/usuarios/me")
    public IdentidadeService.PerfilPublico me(@AuthenticationPrincipal UsuarioAutenticado principal,
                                              HttpServletRequest request) {
        return service.meuPerfil(principal, RequestIds.from(request));
    }

    @PutMapping("/usuarios/me/consentimentos/fidelidade")
    public IdentidadeService.ConsentimentoEstado consentir(@AuthenticationPrincipal UsuarioAutenticado principal,
                                                           @Valid @RequestBody ConsentimentoRequest body,
                                                           HttpServletRequest request) {
        return service.registrarConsentimento(principal, body.consentido(), RequestIds.from(request));
    }

    public record CadastroRequest(@NotBlank @Size(max = 120) String nome,
                                  @NotBlank @Email @Size(max = 254) String email,
                                  @NotBlank @Size(min = 12, max = 72) String senha,
                                  Boolean consentimentoFidelidade) { }
    public record LoginRequest(@NotBlank @Email String email, @NotBlank String senha) { }
    public record ConsentimentoRequest(@NotNull Boolean consentido) { }
}
