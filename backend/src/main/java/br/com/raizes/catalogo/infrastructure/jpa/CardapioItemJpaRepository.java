package br.com.raizes.catalogo.infrastructure.jpa;

import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CardapioItemJpaRepository extends JpaRepository<CardapioItemEntity, CardapioItemId> {
    @Query("select c from CardapioItemEntity c join fetch c.produto p "
            + "where c.id.unidadeId = :unidadeId and c.id.produtoId in :produtoIds "
            + "and c.ativo = true and p.ativo = true")
    List<CardapioItemEntity> ativosPorIds(@Param("unidadeId") Long unidadeId,
                                           @Param("produtoIds") Collection<Long> produtoIds);
    @Query(value = "select c from CardapioItemEntity c join fetch c.produto p "
            + "where c.id.unidadeId = :unidadeId and c.ativo = true and p.ativo = true "
            + "order by c.id.produtoId",
            countQuery = "select count(c) from CardapioItemEntity c join c.produto p "
                    + "where c.id.unidadeId = :unidadeId and c.ativo = true and p.ativo = true")
    Page<CardapioItemEntity> ativosDaUnidade(@Param("unidadeId") Long unidadeId, Pageable pageable);
}
