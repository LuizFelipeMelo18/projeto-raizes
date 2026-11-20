package br.com.raizes.shared.api;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ApiErrorFactory {
    public static final String REQUEST_ID_ATTRIBUTE = "raizes.requestId";

    private ApiErrorFactory() {
    }

    public static ApiError create(HttpServletRequest request, String code, String message,
                                  List<ApiError.Detail> details) {
        Object current = request.getAttribute(REQUEST_ID_ATTRIBUTE);
        String requestId = current == null ? UUID.randomUUID().toString() : current.toString();
        return new ApiError(code, message, details, Instant.now(), request.getRequestURI(), requestId);
    }
}
