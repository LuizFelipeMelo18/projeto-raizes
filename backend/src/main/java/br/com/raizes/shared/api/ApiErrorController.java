package br.com.raizes.shared.api;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ApiErrorController implements ErrorController {
    @RequestMapping("/error")
    public ResponseEntity<ApiError> handle(HttpServletRequest request) {
        Object raw = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int code = raw instanceof Integer integer ? integer : 500;
        HttpStatus status = HttpStatus.resolve(code);
        if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;
        String error = status == HttpStatus.NOT_FOUND ? "RECURSO_NAO_ENCONTRADO" : "REQUISICAO_FALHOU";
        String message = status == HttpStatus.NOT_FOUND ? "Recurso não encontrado." : "Requisição não concluída.";
        return ResponseEntity.status(status).body(ApiErrorFactory.create(request, error, message, List.of()));
    }
}
