package br.com.raizes.pagamentos.infrastructure.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PagamentoEventoJpaRepository extends JpaRepository<PagamentoEventoEntity, Long> { }
