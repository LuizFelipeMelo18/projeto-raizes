package br.com.raizes.pagamentos.application;

import br.com.raizes.pagamentos.domain.CenarioMock;
import br.com.raizes.pagamentos.domain.StatusPagamento;
import java.math.BigDecimal;
import java.util.UUID;

public interface PaymentGateway {
    Resultado solicitar(Solicitacao solicitacao);
    Resultado consultar(Solicitacao solicitacao);

    record Solicitacao(Long pedidoId, BigDecimal valor, UUID chaveIdempotencia, CenarioMock cenario) { }
    record Resultado(StatusPagamento status, String codigo, String mensagem, String payload) { }
}
