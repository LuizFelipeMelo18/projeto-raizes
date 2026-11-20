package br.com.raizes.shared.api;

import java.time.Instant;
import java.util.List;

public record ApiError(String error, String message, List<Detail> details, Instant timestamp,
                       String path, String requestId) {
    public record Detail(String field, String issue) {
    }
}
