package br.com.raizes.estoque.application;

import br.com.raizes.auditoria.application.RegistrarAuditoriaService;
import br.com.raizes.catalogo.infrastructure.jpa.CardapioItemId;
import br.com.raizes.catalogo.infrastructure.jpa.CardapioItemJpaRepository;
import br.com.raizes.catalogo.infrastructure.jpa.UnidadeJpaRepository;
import br.com.raizes.estoque.domain.EstoqueUnidadePolicy;
import br.com.raizes.estoque.domain.TipoMovimentoEstoque;
import br.com.raizes.estoque.infrastructure.jpa.EstoqueSaldoEntity;
import br.com.raizes.estoque.infrastructure.jpa.EstoqueSaldoId;
import br.com.raizes.estoque.infrastructure.jpa.EstoqueSaldoJpaRepository;
import br.com.raizes.estoque.infrastructure.jpa.MovimentoEstoqueEntity;
import br.com.raizes.estoque.infrastructure.jpa.MovimentoEstoqueJpaRepository;
import br.com.raizes.identidade.domain.UsuarioAutenticado;
import br.com.raizes.shared.domain.RecursoNaoEncontradoException;
import br.com.raizes.shared.domain.ValidacaoException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EstoqueService {
    private final UnidadeJpaRepository unidades;
    private final CardapioItemJpaRepository cardapio;
    private final EstoqueSaldoJpaRepository saldos;
    private final MovimentoEstoqueJpaRepository movimentos;
    private final RegistrarAuditoriaService auditoria;

    public EstoqueService(UnidadeJpaRepository unidades, CardapioItemJpaRepository cardapio,
                          EstoqueSaldoJpaRepository saldos, MovimentoEstoqueJpaRepository movimentos,
                          RegistrarAuditoriaService auditoria) {
        this.unidades = unidades;
        this.cardapio = cardapio;
        this.saldos = saldos;
        this.movimentos = movimentos;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public Page<Saldo> consultarSaldo(Long unidadeId, Long produtoId, int page, int limit,
                                      UsuarioAutenticado ator) {
        validarConsulta(unidadeId, produtoId, page, limit, ator);
        if (produtoId != null && !saldos.existsById(new EstoqueSaldoId(unidadeId, produtoId))) {
            throw new RecursoNaoEncontradoException("ESTOQUE_NAO_ENCONTRADO", "Produto fora do estoque da unidade.");
        }
        return saldos.listar(unidadeId, produtoId, PageRequest.of(page - 1, limit)).map(this::saldo);
    }

    @Transactional(readOnly = true)
    public Page<Movimento> consultarMovimentos(Long unidadeId, Long produtoId, int page, int limit,
                                               UsuarioAutenticado ator) {
        validarConsulta(unidadeId, produtoId, page, limit, ator);
        return movimentos.listar(unidadeId, produtoId, PageRequest.of(page - 1, limit))
                .map(m -> new Movimento(m.getId(), m.getUnidadeId(), m.getProdutoId(), m.getPedidoId(),
                        m.getTipo(), m.getQuantidade(), m.getMotivo(), m.getCriadoEm()));
    }

    @Transactional
    public MovimentoResultado movimentar(Long unidadeId, Long produtoId, String tipoTexto, int quantidade,
                                          String motivo, UsuarioAutenticado ator, UUID requestId) {
        validarIds(unidadeId, produtoId);
        EstoqueUnidadePolicy.exigirGerencia(ator, unidadeId);
        TipoMovimentoEstoque tipo = tipoManual(tipoTexto);
        if (quantidade <= 0 || motivo == null || motivo.isBlank() || motivo.length() > 200) {
            throw new ValidacaoException("MOVIMENTO_INVALIDO", "Quantidade positiva e motivo de até 200 caracteres são obrigatórios.");
        }
        unidadeAtiva(unidadeId);
        var item = cardapio.findById(new CardapioItemId(unidadeId, produtoId))
                .filter(c -> c.isAtivo() && c.getProduto().isAtivo())
                .orElseThrow(() -> new RecursoNaoEncontradoException("PRODUTO_NAO_ENCONTRADO",
                        "Produto fora do cardápio ativo da unidade."));
        var saldo = bloquear(unidadeId, item.getId().getProdutoId());
        if (tipo == TipoMovimentoEstoque.ENTRADA) {
            saldo.registrarEntrada(quantidade);
        } else {
            saldo.registrarSaidaAjuste(quantidade);
        }
        var movimento = movimentos.save(new MovimentoEstoqueEntity(unidadeId, produtoId, null, ator.id(),
                tipo, quantidade, motivo.trim()));
        auditoria.registrar(ator.id(), null, null, unidadeId, "MOVIMENTO_ESTOQUE",
                tipo.name(), "ESTOQUE", requestId);
        return new MovimentoResultado(movimento.getId(), unidadeId, produtoId, tipo, quantidade,
                saldo(saldo), movimento.getCriadoEm());
    }

    private void validarConsulta(Long unidadeId, Long produtoId, int page, int limit, UsuarioAutenticado ator) {
        validarIds(unidadeId, produtoId);
        EstoqueUnidadePolicy.exigirGerencia(ator, unidadeId);
        if (page < 1 || limit < 1 || limit > 100) {
            throw new ValidacaoException("PAGINACAO_INVALIDA", "page deve ser maior que zero e limit deve estar entre 1 e 100.");
        }
        unidadeAtiva(unidadeId);
    }

    private void validarIds(Long unidadeId, Long produtoId) {
        if (unidadeId == null || unidadeId < 1 || (produtoId != null && produtoId < 1)) {
            throw new ValidacaoException("IDENTIFICADOR_INVALIDO", "unidadeId deve ser positivo; produtoId, quando informado, também.");
        }
    }

    private void unidadeAtiva(Long unidadeId) {
        unidades.findByIdAndAtivaTrue(unidadeId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("UNIDADE_NAO_ENCONTRADA", "Unidade inexistente ou inativa."));
    }

    private TipoMovimentoEstoque tipoManual(String tipo) {
        if ("ENTRADA".equals(tipo)) return TipoMovimentoEstoque.ENTRADA;
        if ("SAIDA_AJUSTE".equals(tipo)) return TipoMovimentoEstoque.SAIDA_AJUSTE;
        throw new ValidacaoException("TIPO_MOVIMENTO_INVALIDO", "tipo deve ser ENTRADA ou SAIDA_AJUSTE.");
    }

    private EstoqueSaldoEntity bloquear(Long unidadeId, Long produtoId) {
        return saldos.bloquear(new EstoqueSaldoId(unidadeId, produtoId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("ESTOQUE_NAO_ENCONTRADO",
                        "Produto fora do estoque da unidade."));
    }

    private Saldo saldo(EstoqueSaldoEntity s) {
        return new Saldo(s.getId().getUnidadeId(), s.getId().getProdutoId(),
                s.getFisico(), s.getReservado(), s.disponivel());
    }

    public record Saldo(Long unidadeId, Long produtoId, int fisico, int reservado, int disponivel) { }
    public record Movimento(Long movimentoId, Long unidadeId, Long produtoId, Long pedidoId,
                            TipoMovimentoEstoque tipo, int quantidade, String motivo, Instant criadoEm) { }
    public record MovimentoResultado(Long movimentoId, Long unidadeId, Long produtoId,
                                     TipoMovimentoEstoque tipo, int quantidade, Saldo saldo, Instant criadoEm) { }
}
