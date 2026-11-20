package br.com.raizes.fidelidade.infrastructure.jpa;

import br.com.raizes.fidelidade.domain.TipoMovimentoFidelidade;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FidelidadeMovimentoJpaRepository extends JpaRepository<FidelidadeMovimentoEntity, Long> {
    Optional<FidelidadeMovimentoEntity> findByPedidoIdAndTipo(Long pedidoId, TipoMovimentoFidelidade tipo);

    boolean existsByPedidoIdAndTipo(Long pedidoId, TipoMovimentoFidelidade tipo);

    Page<FidelidadeMovimentoEntity> findByUsuarioId(Long usuarioId, Pageable pageable);

    @Query("select m.tipo, sum(m.pontos) from FidelidadeMovimentoEntity m where m.usuarioId = :usuarioId group by m.tipo")
    List<Object[]> totaisPorTipo(@Param("usuarioId") Long usuarioId);
}

