package br.com.raizes.shared.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;

public final class RequestIds {
    private RequestIds() {
    }

    public static UUID from(HttpServletRequest request) {
        return UUID.fromString(request.getAttribute(ApiErrorFactory.REQUEST_ID_ATTRIBUTE).toString());
    }
}
