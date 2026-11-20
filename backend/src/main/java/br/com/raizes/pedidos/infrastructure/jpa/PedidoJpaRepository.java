package br.com.raizes.pedidos.infrastructure.jpa;

import br.com.raizes.pedidos.domain.CanalPedido;
import br.com.raizes.pedidos.domain.StatusPedido;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import org.springframework.data.repository.query.Param;

public interface PedidoJpaRepository extends JpaRepository<PedidoEntity, Long> {
    Optional<PedidoEntity> findByChaveIdempotencia(UUID chaveIdempotencia);
    Optional<PedidoEntity> findByCodigoAcompanhamento(String codigoAcompanhamento);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PedidoEntity p where p.id = :id")
    Optional<PedidoEntity> bloquear(@Param("id") Long id);

    @Query("select p.id from PedidoEntity p where p.status = br.com.raizes.pedidos.domain.StatusPedido.AGUARDANDO_PAGAMENTO "
            + "and p.reservaExpiraEm <= :agora "
            + "and not exists (select g.id from PagamentoEntity g where g.pedidoId = p.id) "
            + "order by p.reservaExpiraEm, p.id")
    List<Long> expirados(@Param("agora") Instant agora, org.springframework.data.domain.Pageable pageable);

    @Query("select p from PedidoEntity p where (:clienteId is null or p.clienteId = :clienteId) "
            + "and (:unidadeId is null or p.unidadeId = :unidadeId) "
            + "and (:canal is null or p.canalPedido = :canal) "
            + "and (:status is null or p.status = :status)")
    Page<PedidoEntity> filtrar(@Param("clienteId") Long clienteId, @Param("unidadeId") Long unidadeId,
                               @Param("canal") CanalPedido canal, @Param("status") StatusPedido status,
                               Pageable pageable);

    @Query("select p.unidadeId, p.canalPedido, count(p), coalesce(sum(p.total + p.descontoFidelidade), 0), "
            + "coalesce(sum(p.descontoFidelidade), 0), coalesce(sum(p.total), 0), "
            + "coalesce(sum(p.pontosResgatados), 0) "
            + "from PedidoEntity p where (:unidadeId is null or p.unidadeId = :unidadeId) "
            + "and (:canal is null or p.canalPedido = :canal) "
            + "and (:status is null or p.status = :status) "
            + "group by p.unidadeId, p.canalPedido order by p.unidadeId, p.canalPedido")
    List<Object[]> agregarPorUnidadeECanal(@Param("unidadeId") Long unidadeId,
                                           @Param("canal") CanalPedido canal,
                                           @Param("status") StatusPedido status);
}
