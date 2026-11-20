package br.com.raizes.pedidos.api;

import br.com.raizes.identidade.domain.UsuarioAutenticado;
import br.com.raizes.pedidos.application.ConsultarPedidoService;
import br.com.raizes.pedidos.application.RelatorioPedidosService;
import br.com.raizes.shared.api.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pedidos")
public class PedidoConsultaController {
    private final ConsultarPedidoService service;
    private final RelatorioPedidosService relatorio;

    public PedidoConsultaController(ConsultarPedidoService service, RelatorioPedidosService relatorio) {
        this.service = service;
        this.relatorio = relatorio;
    }

    @GetMapping("/relatorios/unidades-canais")
    public RelatorioPedidosService.Relatorio relatorioPorUnidadeECanal(
            @RequestParam(required = false) Long unidadeId,
            @RequestParam(required = false) String canalPedido,
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal UsuarioAutenticado principal) {
        return relatorio.consultar(unidadeId, canalPedido, status, principal);
    }

    @GetMapping("/{id}")
    public ConsultarPedidoService.PedidoDetalhe consultar(@PathVariable Long id,
                                                          @AuthenticationPrincipal UsuarioAutenticado principal,
                                                          HttpServletRequest request) {
        return service.consultar(id, principal, RequestIds.from(request));
    }
}
