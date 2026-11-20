package br.com.raizes.auditoria.api;

import br.com.raizes.auditoria.application.ConsultarAuditoriaService;
import br.com.raizes.identidade.domain.UsuarioAutenticado;
import br.com.raizes.shared.api.PageEnvelope;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auditoria")
public class AuditoriaController {
    private final ConsultarAuditoriaService service;

    public AuditoriaController(ConsultarAuditoriaService service) {
        this.service = service;
    }

    @GetMapping
    public PageEnvelope<ConsultarAuditoriaService.EventoAuditoria> listar(
            @RequestParam(required = false) Long unidadeId,
            @RequestParam(required = false) String acao,
            @RequestParam(required = false) String recurso,
            @RequestParam(required = false) Long pedidoId,
            @RequestParam(required = false) Long atorId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @AuthenticationPrincipal UsuarioAutenticado principal) {
        return PageEnvelope.from(service.listar(unidadeId, acao, recurso, pedidoId, atorId, page, limit, principal));
    }
}

