package br.com.raizes.shared.domain;

public class RecursoNaoEncontradoException extends RuntimeException {
    private final String code;

    public RecursoNaoEncontradoException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
