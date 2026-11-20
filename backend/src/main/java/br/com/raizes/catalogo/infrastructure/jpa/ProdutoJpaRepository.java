package br.com.raizes.catalogo.infrastructure.jpa;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProdutoJpaRepository extends JpaRepository<ProdutoEntity, Long> {
    Page<ProdutoEntity> findByAtivoTrue(Pageable pageable);
}
