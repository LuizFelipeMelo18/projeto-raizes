package br.com.raizes.pagamentos.infrastructure.jpa;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PagamentoJpaRepository extends JpaRepository<PagamentoEntity, Long> {
    Optional<PagamentoEntity> findByPedidoId(Long pedidoId);
    Optional<PagamentoEntity> findByChaveIdempotencia(UUID chaveIdempotencia);
}
