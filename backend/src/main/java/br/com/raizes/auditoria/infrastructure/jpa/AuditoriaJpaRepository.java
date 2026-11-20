package br.com.raizes.auditoria.infrastructure.jpa;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditoriaJpaRepository extends JpaRepository<AuditoriaEntity, Long> {
    @Query("select a from AuditoriaEntity a where (:unidadeId is null or a.unidadeId = :unidadeId) "
            + "and (:acao is null or a.acao = :acao) "
            + "and (:recurso is null or a.recurso = :recurso) "
            + "and (:pedidoId is null or a.pedidoId = :pedidoId) "
            + "and (:atorId is null or a.atorId = :atorId)")
    Page<AuditoriaEntity> filtrar(@Param("unidadeId") Long unidadeId, @Param("acao") String acao,
                                  @Param("recurso") String recurso, @Param("pedidoId") Long pedidoId,
                                  @Param("atorId") Long atorId, Pageable pageable);
}
