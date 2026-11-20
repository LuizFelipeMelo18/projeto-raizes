package br.com.raizes.auditoria.application;

import br.com.raizes.auditoria.infrastructure.jpa.AuditoriaEntity;
import br.com.raizes.auditoria.infrastructure.jpa.AuditoriaJpaRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RegistrarAuditoriaService {
    private final AuditoriaJpaRepository repository;

    public RegistrarAuditoriaService(AuditoriaJpaRepository repository) {
        this.repository = repository;
    }

    public void registrar(Long atorId, Long sujeitoId, Long pedidoId,
                          String acao, String resultado, String recurso, UUID requestId) {
        registrar(atorId, sujeitoId, pedidoId, null, acao, resultado, recurso, requestId);
    }

    public void registrar(Long atorId, Long sujeitoId, Long pedidoId, Long unidadeId,
                          String acao, String resultado, String recurso, UUID requestId) {
        repository.save(new AuditoriaEntity(atorId, sujeitoId, pedidoId, unidadeId,
                acao, resultado, recurso, requestId));
    }
}
