package br.com.raizes.saude.api;

import br.com.raizes.saude.application.VerificarSaudeService;
import br.com.raizes.shared.domain.ServicoIndisponivelException;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {
    private final VerificarSaudeService service;

    public HealthController(VerificarSaudeService service) {
        this.service = service;
    }

    @GetMapping
    public Map<String, String> health() {
        try {
            if (service.bancoDisponivel()) {
                return Map.of("status", "UP", "database", "UP");
            }
        } catch (RuntimeException ignored) {
            // O health check não devolve detalhes de conexão ou credenciais.
        }
        throw new ServicoIndisponivelException("BANCO_INDISPONIVEL", "Banco indisponível.");
    }
}
