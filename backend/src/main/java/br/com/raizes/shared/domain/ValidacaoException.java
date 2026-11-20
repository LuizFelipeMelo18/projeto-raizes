package br.com.raizes.shared.domain;

public class ValidacaoException extends RuntimeException {
    private final String code;

    public ValidacaoException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
