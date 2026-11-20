package br.com.raizes.catalogo.api;

import br.com.raizes.catalogo.application.ConsultarCatalogoService;
import br.com.raizes.catalogo.domain.Produto;
import br.com.raizes.shared.api.PageEnvelope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/produtos")
public class ProdutoController {
    private final ConsultarCatalogoService service;

    public ProdutoController(ConsultarCatalogoService service) {
        this.service = service;
    }

    @GetMapping
    public PageEnvelope<Produto> listar(@RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "20") int limit) {
        return PageEnvelope.from(service.listarProdutos(page, limit));
    }
}
