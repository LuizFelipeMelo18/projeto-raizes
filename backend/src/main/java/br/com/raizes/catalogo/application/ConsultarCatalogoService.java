package br.com.raizes.catalogo.application;

import br.com.raizes.catalogo.domain.ProdutoCardapio;
import br.com.raizes.catalogo.domain.Produto;
import br.com.raizes.catalogo.domain.Unidade;
import br.com.raizes.catalogo.infrastructure.jpa.CardapioItemJpaRepository;
import br.com.raizes.catalogo.infrastructure.jpa.UnidadeJpaRepository;
import br.com.raizes.catalogo.infrastructure.jpa.ProdutoJpaRepository;
import br.com.raizes.estoque.infrastructure.jpa.EstoqueSaldoJpaRepository;
import br.com.raizes.shared.domain.RecursoNaoEncontradoException;
import br.com.raizes.shared.domain.ValidacaoException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ConsultarCatalogoService {
    private final UnidadeJpaRepository unidades;
    private final ProdutoJpaRepository produtos;
    private final CardapioItemJpaRepository cardapio;
    private final EstoqueSaldoJpaRepository saldos;

    public ConsultarCatalogoService(UnidadeJpaRepository unidades, ProdutoJpaRepository produtos,
                                    CardapioItemJpaRepository cardapio,
                                    EstoqueSaldoJpaRepository saldos) {
        this.unidades = unidades;
        this.produtos = produtos;
        this.cardapio = cardapio;
        this.saldos = saldos;
    }

    public Page<Unidade> listarUnidades(int page, int limit) {
        validarPagina(page, limit);
        return unidades.findByAtivaTrue(PageRequest.of(page - 1, limit, Sort.by("id")))
                .map(u -> new Unidade(u.getId(), u.getNome(), u.isAtiva()));
    }

    public Page<Produto> listarProdutos(int page, int limit) {
        validarPagina(page, limit);
        return produtos.findByAtivoTrue(PageRequest.of(page - 1, limit, Sort.by("id")))
                .map(p -> new Produto(p.getId(), p.getNome(), p.isAtivo()));
    }

    public Page<ProdutoCardapio> consultarCardapio(Long unidadeId, int page, int limit) {
        validarPagina(page, limit);
        unidades.findByIdAndAtivaTrue(unidadeId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("UNIDADE_NAO_ENCONTRADA", "Unidade inexistente ou inativa."));
        var pagina = cardapio.ativosDaUnidade(unidadeId, PageRequest.of(page - 1, limit));
        var ids = pagina.getContent().stream().map(item -> item.getId().getProdutoId()).toList();
        Map<Long, br.com.raizes.estoque.infrastructure.jpa.EstoqueSaldoEntity> porProduto = ids.isEmpty()
                ? Map.of()
                : saldos.todosDaPagina(unidadeId, ids).stream()
                .collect(Collectors.toMap(s -> s.getId().getProdutoId(), Function.identity()));
        return pagina
                .map(item -> {
                    var saldo = porProduto.get(item.getId().getProdutoId());
                    boolean disponivel = saldo != null && saldo.disponivel() > 0;
                    return new ProdutoCardapio(item.getId().getProdutoId(), item.getProduto().getNome(),
                            item.getPreco(), disponivel);
                });
    }

    private void validarPagina(int page, int limit) {
        if (page < 1 || limit < 1 || limit > 100) {
            throw new ValidacaoException("PAGINACAO_INVALIDA", "page deve ser maior que zero e limit deve estar entre 1 e 100.");
        }
    }
}
