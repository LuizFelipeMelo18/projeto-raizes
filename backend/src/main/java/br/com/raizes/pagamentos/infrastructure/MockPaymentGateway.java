package br.com.raizes.pagamentos.infrastructure;

import br.com.raizes.pagamentos.application.PaymentGateway;
import br.com.raizes.pagamentos.domain.StatusPagamento;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class MockPaymentGateway implements PaymentGateway {
    @Override
    public Resultado solicitar(Solicitacao solicitacao) {
        exigirForaDeTransacao();
        return switch (solicitacao.cenario()) {
            case APROVAR -> resultado(StatusPagamento.APROVADO, "MOCK-OK", "Pagamento simulado aprovado.", solicitacao);
            case RECUSAR -> resultado(StatusPagamento.RECUSADO, "MOCK-DECLINED", "Pagamento simulado recusado.", solicitacao);
            default -> resultado(StatusPagamento.INDETERMINADO, "MOCK-UNKNOWN",
                    "Resultado indeterminado; reserva retida até reconciliação.", solicitacao);
        };
    }

    @Override
    public Resultado consultar(Solicitacao solicitacao) {
        exigirForaDeTransacao();
        return switch (solicitacao.cenario()) {
            case APROVAR, INDETERMINADO_APROVAR -> resultado(StatusPagamento.APROVADO,
                    "MOCK-OK", "Pagamento confirmado na reconciliação.", solicitacao);
            case RECUSAR, INDETERMINADO_RECUSAR -> resultado(StatusPagamento.RECUSADO,
                    "MOCK-DECLINED", "Recusa confirmada na reconciliação.", solicitacao);
            default -> resultado(StatusPagamento.INDETERMINADO, "MOCK-UNKNOWN",
                    "Gateway ainda não confirmou o resultado.", solicitacao);
        };
    }

    private Resultado resultado(StatusPagamento estado, String codigo, String mensagem, Solicitacao solicitacao) {
        String payload = "{\"gateway\":\"mock\",\"codigo\":\"" + codigo + "\",\"estado\":\""
                + estado + "\",\"referencia\":\"" + solicitacao.chaveIdempotencia() + "\"}";
        return new Resultado(estado, codigo, mensagem, payload);
    }

    private void exigirForaDeTransacao() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Gateway não pode ser chamado com transação de banco aberta.");
        }
    }
}
