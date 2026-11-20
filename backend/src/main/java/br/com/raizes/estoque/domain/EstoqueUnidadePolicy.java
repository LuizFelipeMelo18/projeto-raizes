package br.com.raizes.estoque.domain;

import br.com.raizes.identidade.domain.Perfil;
import br.com.raizes.identidade.domain.UsuarioAutenticado;
import br.com.raizes.shared.domain.AcessoNegadoException;
import java.util.Objects;

public final class EstoqueUnidadePolicy {
    private EstoqueUnidadePolicy() {
    }

    public static void exigirGerencia(UsuarioAutenticado ator, Long unidadeId) {
        if (ator == null || (ator.perfil() != Perfil.GERENTE && ator.perfil() != Perfil.ADMIN)) {
            throw new AcessoNegadoException();
        }
        if (ator.perfil() == Perfil.GERENTE && !Objects.equals(ator.unidadeId(), unidadeId)) {
            throw new AcessoNegadoException();
        }
    }
}
