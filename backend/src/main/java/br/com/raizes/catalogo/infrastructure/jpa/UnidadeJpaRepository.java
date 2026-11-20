package br.com.raizes.catalogo.infrastructure.jpa;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnidadeJpaRepository extends JpaRepository<UnidadeEntity, Long> {
    Page<UnidadeEntity> findByAtivaTrue(Pageable pageable);
    Optional<UnidadeEntity> findByIdAndAtivaTrue(Long id);
}
