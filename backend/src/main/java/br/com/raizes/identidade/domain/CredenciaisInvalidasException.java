package br.com.raizes.identidade.domain;

public class CredenciaisInvalidasException extends RuntimeException {
    public CredenciaisInvalidasException() {
        super("Credenciais inválidas.");
    }
}
