package br.com.raizes.shared.api;

import java.util.List;
import org.springframework.data.domain.Page;

public record PageEnvelope<T>(List<T> data, int page, int limit, long totalItems, int totalPages) {
    public static <T> PageEnvelope<T> from(Page<T> source) {
        return new PageEnvelope<>(source.getContent(), source.getNumber() + 1, source.getSize(),
                source.getTotalElements(), source.getTotalPages());
    }
}
