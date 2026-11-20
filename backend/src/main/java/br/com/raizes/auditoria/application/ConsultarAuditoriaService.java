package br.com.raizes.auditoria.application;

import br.com.raizes.auditoria.infrastructure.jpa.AuditoriaJpaRepository;
import br.com.raizes.identidade.domain.Perfil;
import br.com.raizes.identidade.domain.UsuarioAutenticado;
import br.com.raizes.shared.domain.AcessoNegadoException;
import br.com.raizes.shared.domain.ValidacaoException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsultarAuditoriaService {
    private final AuditoriaJpaRepository repository;

    public ConsultarAuditoriaService(AuditoriaJpaRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Page<EventoAuditoria> listar(Long unidadeId, String acao, String recurso, Long pedidoId, Long atorId,
                                        int page, int limit, UsuarioAutenticado principal) {
        if (principal == null || (principal.perfil() != Perfil.GERENTE && principal.perfil() != Perfil.ADMIN)) {
            throw new AcessoNegadoException();
        }
        if (page < 1 || limit < 1 || limit > 100) {
            throw new ValidacaoException("PAGINACAO_INVALIDA", "page deve ser positivo e limit entre 1 e 100.");
        }
        if ((unidadeId != null && unidadeId < 1) || (pedidoId != null && pedidoId < 1) || (atorId != null && atorId < 1)) {
            throw new ValidacaoException("IDENTIFICADOR_INVALIDO", "Identificadores de filtro devem ser positivos.");
        }
        Long unidadeEscopo;
        if (principal.perfil() == Perfil.GERENTE) {
            if (principal.unidadeId() == null || (unidadeId != null && !Objects.equals(unidadeId, principal.unidadeId()))) {
                throw new AcessoNegadoException();
            }
            unidadeEscopo = principal.unidadeId();
        } else {
            unidadeEscopo = unidadeId;
        }
        String acaoFiltro = normalizar(acao);
        String recursoFiltro = normalizar(recurso);
        var pageable = PageRequest.of(page - 1, limit,
                Sort.by(Sort.Order.desc("criadoEm"), Sort.Order.desc("id")));
        return repository.filtrar(unidadeEscopo, acaoFiltro, recursoFiltro, pedidoId, atorId, pageable)
                .map(a -> new EventoAuditoria(a.getId(), a.getUnidadeId(), a.getPedidoId(),
                        a.getAtorId(), a.getSujeitoUsuarioId(), a.getRecurso(), a.getAcao(),
                        a.getResultado(), a.getRequestId(), a.getCriadoEm()));
    }

    private static String normalizar(String texto) {
        if (texto == null || texto.isBlank()) return null;
        if (texto.length() > 80) {
            throw new ValidacaoException("FILTRO_INVALIDO", "Filtro textual excede o tamanho permitido.");
        }
        return texto.trim();
    }

    public record EventoAuditoria(Long auditoriaId, Long unidadeId, Long pedidoId, Long atorId,
                                  Long sujeitoUsuarioId, String recurso, String acao, String resultado,
                                  UUID requestId, Instant criadoEm) { }
}

