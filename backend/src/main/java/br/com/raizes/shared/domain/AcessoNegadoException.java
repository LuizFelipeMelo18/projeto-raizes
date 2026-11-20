package br.com.raizes.shared.domain;

public class AcessoNegadoException extends RuntimeException {
    public AcessoNegadoException() {
        super("Acesso não permitido.");
    }
}
