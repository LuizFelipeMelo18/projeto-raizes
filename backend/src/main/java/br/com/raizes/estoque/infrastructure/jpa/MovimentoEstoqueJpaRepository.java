package br.com.raizes.estoque.infrastructure.jpa;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MovimentoEstoqueJpaRepository extends JpaRepository<MovimentoEstoqueEntity, Long> {
    @Query(value = "select m from MovimentoEstoqueEntity m where m.unidadeId = :unidadeId "
            + "and (:produtoId is null or m.produtoId = :produtoId) order by m.criadoEm desc, m.id desc",
            countQuery = "select count(m) from MovimentoEstoqueEntity m where m.unidadeId = :unidadeId "
                    + "and (:produtoId is null or m.produtoId = :produtoId)")
    Page<MovimentoEstoqueEntity> listar(@Param("unidadeId") Long unidadeId,
                                        @Param("produtoId") Long produtoId, Pageable pageable);
}
