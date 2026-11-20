package br.com.raizes.identidade.infrastructure.jpa;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsentimentoJpaRepository extends JpaRepository<ConsentimentoEntity, Long> {
    Optional<ConsentimentoEntity> findFirstByUsuarioIdAndFinalidadeOrderByIdDesc(Long usuarioId, String finalidade);
}
