package br.com.raizes.fidelidade.infrastructure.jpa;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FidelidadeContaJpaRepository extends JpaRepository<FidelidadeContaEntity, Long> {
    @Modifying
    @Query(value = "INSERT INTO fidelidade_conta (usuario_id, saldo, versao, atualizado_em) "
            + "VALUES (:usuarioId, 0, 0, CURRENT_TIMESTAMP) ON CONFLICT (usuario_id) DO NOTHING", nativeQuery = true)
    int criarSeAusente(@Param("usuarioId") Long usuarioId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from FidelidadeContaEntity c where c.usuarioId = :usuarioId")
    Optional<FidelidadeContaEntity> bloquear(@Param("usuarioId") Long usuarioId);
}

