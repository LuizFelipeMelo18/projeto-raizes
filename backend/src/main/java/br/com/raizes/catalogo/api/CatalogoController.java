package br.com.raizes.catalogo.api;

import br.com.raizes.catalogo.application.ConsultarCatalogoService;
import br.com.raizes.catalogo.domain.ProdutoCardapio;
import br.com.raizes.catalogo.domain.Unidade;
import br.com.raizes.shared.api.PageEnvelope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/unidades")
public class CatalogoController {
    private final ConsultarCatalogoService service;

    public CatalogoController(ConsultarCatalogoService service) {
        this.service = service;
    }

    @GetMapping
    public PageEnvelope<Unidade> listar(@RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "20") int limit) {
        return PageEnvelope.from(service.listarUnidades(page, limit));
    }

    @GetMapping("/{id}/cardapio")
    public PageEnvelope<ProdutoCardapio> cardapio(@PathVariable Long id,
                                                  @RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "20") int limit) {
        return PageEnvelope.from(service.consultarCardapio(id, page, limit));
    }
}
