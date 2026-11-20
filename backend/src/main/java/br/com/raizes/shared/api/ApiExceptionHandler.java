package br.com.raizes.shared.api;

import br.com.raizes.identidade.domain.CredenciaisInvalidasException;
import br.com.raizes.shared.domain.AcessoNegadoException;
import br.com.raizes.shared.domain.ConflitoException;
import br.com.raizes.shared.domain.RecursoNaoEncontradoException;
import br.com.raizes.shared.domain.ServicoIndisponivelException;
import br.com.raizes.shared.domain.ValidacaoException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(CredenciaisInvalidasException.class)
    public ResponseEntity<ApiError> invalidCredentials(CredenciaisInvalidasException ex, HttpServletRequest request) {
        return error(HttpStatus.UNAUTHORIZED, request, "CREDENCIAIS_INVALIDAS", ex.getMessage(), List.of());
    }

    @ExceptionHandler(AcessoNegadoException.class)
    public ResponseEntity<ApiError> forbidden(AcessoNegadoException ex, HttpServletRequest request) {
        return error(HttpStatus.FORBIDDEN, request, "ACESSO_NEGADO", ex.getMessage(), List.of());
    }

    @ExceptionHandler(ConflitoException.class)
    public ResponseEntity<ApiError> conflict(ConflitoException ex, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, request, ex.code(), ex.getMessage(), List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> beanValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        var details = ex.getBindingResult().getFieldErrors().stream()
                .map(field -> new ApiError.Detail(field.getField(), field.getDefaultMessage()))
                .toList();
        return error(HttpStatus.UNPROCESSABLE_ENTITY, request, "VALIDACAO_INVALIDA", "Campos inválidos.", details);
    }

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<ApiError> notFound(RecursoNaoEncontradoException ex, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, request, ex.code(), ex.getMessage(), List.of());
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ApiError> unknownPath(Exception ex, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, request, "RECURSO_NAO_ENCONTRADO", "Recurso não encontrado.", List.of());
    }

    @ExceptionHandler(ValidacaoException.class)
    public ResponseEntity<ApiError> validation(ValidacaoException ex, HttpServletRequest request) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, request, ex.code(), ex.getMessage(), List.of());
    }

    @ExceptionHandler(ServicoIndisponivelException.class)
    public ResponseEntity<ApiError> unavailable(ServicoIndisponivelException ex, HttpServletRequest request) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, request, ex.code(), ex.getMessage(), List.of());
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class,
            HttpMessageNotReadableException.class})
    public ResponseEntity<ApiError> malformed(Exception ex, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, request, "REQUISICAO_INVALIDA", "Requisição malformada ou parâmetro ilegível.",
                List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception ex, HttpServletRequest request) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, request, "ERRO_INTERNO", "Falha interna ao processar a requisição.",
                List.of());
    }

    private ResponseEntity<ApiError> error(HttpStatus status, HttpServletRequest request, String code,
                                           String message, List<ApiError.Detail> details) {
        return ResponseEntity.status(status).body(ApiErrorFactory.create(request, code, message, details));
    }
}
