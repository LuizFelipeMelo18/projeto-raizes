package br.com.raizes.shared.domain;

public class ServicoIndisponivelException extends RuntimeException {
    private final String code;

    public ServicoIndisponivelException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
