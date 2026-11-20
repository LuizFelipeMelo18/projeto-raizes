package br.com.raizes.estoque.infrastructure.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface EstoqueSaldoJpaRepository extends JpaRepository<EstoqueSaldoEntity, EstoqueSaldoId> {
    @Query("select s from EstoqueSaldoEntity s where s.id.unidadeId = :unidadeId "
            + "and s.id.produtoId in :produtoIds")
    List<EstoqueSaldoEntity> todosDaPagina(@Param("unidadeId") Long unidadeId,
                                          @Param("produtoIds") List<Long> produtoIds);

    @Query(value = "select s from EstoqueSaldoEntity s where s.id.unidadeId = :unidadeId "
            + "and (:produtoId is null or s.id.produtoId = :produtoId) order by s.id.produtoId",
            countQuery = "select count(s) from EstoqueSaldoEntity s where s.id.unidadeId = :unidadeId "
                    + "and (:produtoId is null or s.id.produtoId = :produtoId)")
    Page<EstoqueSaldoEntity> listar(@Param("unidadeId") Long unidadeId,
                                    @Param("produtoId") Long produtoId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from EstoqueSaldoEntity s where s.id = :id")
    Optional<EstoqueSaldoEntity> bloquear(@Param("id") EstoqueSaldoId id);
}
