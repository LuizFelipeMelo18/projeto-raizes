package br.com.raizes.pedidos.infrastructure.jpa;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemPedidoJpaRepository extends JpaRepository<ItemPedidoEntity, Long> {
    List<ItemPedidoEntity> findByPedidoIdOrderById(Long pedidoId);
}
