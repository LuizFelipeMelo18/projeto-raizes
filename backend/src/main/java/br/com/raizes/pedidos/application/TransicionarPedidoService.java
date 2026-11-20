package br.com.raizes.pedidos.application;

import br.com.raizes.auditoria.application.RegistrarAuditoriaService;
import br.com.raizes.estoque.application.ReservaEstoqueService;
import br.com.raizes.fidelidade.application.FidelidadeService;
import br.com.raizes.identidade.domain.Perfil;
import br.com.raizes.identidade.domain.UsuarioAutenticado;
import br.com.raizes.pagamentos.infrastructure.jpa.PagamentoJpaRepository;
import br.com.raizes.pedidos.domain.PedidoAccessPolicy;
import br.com.raizes.pedidos.domain.PedidoStatusMachine;
import br.com.raizes.pedidos.domain.StatusPedido;
import br.com.raizes.pedidos.infrastructure.jpa.ItemPedidoJpaRepository;
import br.com.raizes.pedidos.infrastructure.jpa.PedidoEntity;
import br.com.raizes.pedidos.infrastructure.jpa.PedidoJpaRepository;
import br.com.raizes.shared.domain.AcessoNegadoException;
import br.com.raizes.shared.domain.ConflitoException;
import br.com.raizes.shared.domain.RecursoNaoEncontradoException;
import br.com.raizes.shared.domain.ValidacaoException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransicionarPedidoService {
    private final PedidoJpaRepository pedidos;
    private final PagamentoJpaRepository pagamentos;
    private final ItemPedidoJpaRepository itens;
    private final ReservaEstoqueService estoque;
    private final FidelidadeService fidelidade;
    private final RegistrarAuditoriaService auditoria;

    public TransicionarPedidoService(PedidoJpaRepository pedidos, PagamentoJpaRepository pagamentos,
                                     ItemPedidoJpaRepository itens, ReservaEstoqueService estoque,
                                     FidelidadeService fidelidade, RegistrarAuditoriaService auditoria) {
        this.pedidos = pedidos;
        this.pagamentos = pagamentos;
        this.itens = itens;
        this.estoque = estoque;
        this.fidelidade = fidelidade;
        this.auditoria = auditoria;
    }

    @Transactional
    public Resposta mudar(Long pedidoId, String destinoTexto, UsuarioAutenticado ator, UUID requestId) {
        StatusPedido destino;
        try {
            destino = StatusPedido.valueOf(destinoTexto);
        } catch (RuntimeException ex) {
            throw new ValidacaoException("STATUS_PEDIDO_INVALIDO", "Status obrigatório ou inválido.");
        }
        var pedido = bloquear(pedidoId);
        exigirOperador(ator, pedido, destino);
        StatusPedido anterior = pedido.getStatus();
        PedidoStatusMachine.exigirTransicaoOperacional(anterior, destino);
        pedido.mudarStatus(destino);
        if (destino == StatusPedido.ENTREGUE) {
            fidelidade.acumularNaEntregaSeAplicavel(pedido, ator.id(), requestId);
        }
        auditoria.registrar(ator.id(), pedido.getClienteId(), pedidoId, pedido.getUnidadeId(),
                "TRANSICAO_PEDIDO", destino.name(), "PEDIDO", requestId);
        return new Resposta(pedidoId, anterior, destino, false, pedido.getAtualizadoEm());
    }

    @Transactional
    public Resposta cancelar(Long pedidoId, String motivo, UsuarioAutenticado ator, UUID requestId) {
        if (motivo != null && motivo.length() > 200) {
            throw new ValidacaoException("MOTIVO_INVALIDO", "Motivo deve ter até 200 caracteres.");
        }
        var pedido = bloquear(pedidoId);
        PedidoAccessPolicy.exigirPagamento(ator, pedido.escopo());
        if (pedido.getStatus() != StatusPedido.AGUARDANDO_PAGAMENTO
                || pagamentos.findByPedidoId(pedidoId).isPresent()) {
            throw new ConflitoException("TRANSICAO_INVALIDA", "Cancelamento permitido somente antes da tentativa de pagamento.");
        }
        var quantidades = itens.findByPedidoIdOrderById(pedidoId).stream()
                .collect(Collectors.toMap(i -> i.getProdutoId(), i -> i.getQuantidade()));
        estoque.liberar(pedido.getUnidadeId(), quantidades, pedidoId, ator.id());
        pedido.mudarStatus(StatusPedido.CANCELADO);
        fidelidade.devolverResgateSeAplicavel(pedido, ator.id(), requestId);
        auditoria.registrar(ator.id(), pedido.getClienteId(), pedidoId, pedido.getUnidadeId(),
                "CANCELAMENTO_PEDIDO", "SUCESSO", "PEDIDO", requestId);
        return new Resposta(pedidoId, StatusPedido.AGUARDANDO_PAGAMENTO, StatusPedido.CANCELADO,
                true, pedido.getAtualizadoEm());
    }

    private PedidoEntity bloquear(Long pedidoId) {
        return pedidos.bloquear(pedidoId).orElseThrow(() ->
                new RecursoNaoEncontradoException("PEDIDO_NAO_ENCONTRADO", "Pedido não encontrado."));
    }

    private void exigirOperador(UsuarioAutenticado ator, PedidoEntity pedido, StatusPedido destino) {
        if (ator.perfil() == Perfil.ADMIN) return;
        if (!Objects.equals(ator.unidadeId(), pedido.getUnidadeId())) throw new AcessoNegadoException();
        boolean permitido = ator.perfil() == Perfil.GERENTE
                || (ator.perfil() == Perfil.COZINHA && destino == StatusPedido.PRONTO)
                || (ator.perfil() == Perfil.ATENDENTE && destino == StatusPedido.ENTREGUE);
        if (!permitido) throw new AcessoNegadoException();
    }

    public record Resposta(Long pedidoId, StatusPedido statusAnterior, StatusPedido status,
                          boolean reservaLiberada, Instant atualizadoEm) { }
}
