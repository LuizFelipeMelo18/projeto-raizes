package br.com.raizes.identidade.application;

import br.com.raizes.auditoria.application.RegistrarAuditoriaService;
import br.com.raizes.identidade.domain.CredenciaisInvalidasException;
import br.com.raizes.identidade.domain.Perfil;
import br.com.raizes.identidade.domain.UsuarioAutenticado;
import br.com.raizes.identidade.infrastructure.jpa.ConsentimentoEntity;
import br.com.raizes.identidade.infrastructure.jpa.ConsentimentoJpaRepository;
import br.com.raizes.identidade.infrastructure.jpa.UsuarioEntity;
import br.com.raizes.identidade.infrastructure.jpa.UsuarioJpaRepository;
import br.com.raizes.identidade.infrastructure.security.JwtService;
import br.com.raizes.shared.domain.AcessoNegadoException;
import br.com.raizes.shared.domain.ConflitoException;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentidadeService {
    private static final String DUMMY_HASH = "$2a$10$6svqpANyKBT6yEG5ObnZyu45ofQBGYxVwEbGlh6E7RTg7JDzQGo52";
    private final UsuarioJpaRepository usuarios;
    private final ConsentimentoJpaRepository consentimentos;
    private final RegistrarAuditoriaService auditoria;
    private final PasswordEncoder passwords;
    private final JwtService jwt;

    public IdentidadeService(UsuarioJpaRepository usuarios, ConsentimentoJpaRepository consentimentos,
                             RegistrarAuditoriaService auditoria, PasswordEncoder passwords, JwtService jwt) {
        this.usuarios = usuarios;
        this.consentimentos = consentimentos;
        this.auditoria = auditoria;
        this.passwords = passwords;
        this.jwt = jwt;
    }

    @Transactional
    public Cadastro cadastrar(String nome, String email, String senha, Boolean escolhaFidelidade, UUID requestId) {
        String normalizado = email.trim().toLowerCase(Locale.ROOT);
        if (usuarios.existsByEmailIgnoreCase(normalizado)) {
            throw new ConflitoException("EMAIL_JA_CADASTRADO", "Email já cadastrado.");
        }
        UsuarioEntity usuario;
        try {
            usuario = usuarios.saveAndFlush(new UsuarioEntity(nome.trim(), normalizado, passwords.encode(senha)));
        } catch (DataIntegrityViolationException ex) {
            throw new ConflitoException("EMAIL_JA_CADASTRADO", "Email já cadastrado.");
        }
        if (escolhaFidelidade != null) {
            consentimentos.save(new ConsentimentoEntity(usuario.getId(), escolhaFidelidade, requestId));
        }
        auditoria.registrar(usuario.getId(), usuario.getId(), null,
                "CADASTRO_CLIENTE", "SUCESSO", "USUARIO", requestId);
        return new Cadastro(usuario.getId(), usuario.getNome(), usuario.getEmail(),
                usuario.getPerfil(), Boolean.TRUE.equals(escolhaFidelidade));
    }

    @Transactional
    public Login login(String email, String senha, UUID requestId) {
        var encontrado = usuarios.findByEmailIgnoreCase(email.trim());
        String hash = encontrado.map(UsuarioEntity::getSenhaHash).orElse(DUMMY_HASH);
        boolean senhaCorreta = passwords.matches(senha, hash);
        if (encontrado.isEmpty() || !encontrado.get().isAtivo() || !senhaCorreta) {
            throw new CredenciaisInvalidasException();
        }
        var usuario = encontrado.get();
        var principal = new UsuarioAutenticado(usuario.getId(), usuario.getPerfil(), usuario.getUnidadeId());
        String token = jwt.emitir(principal);
        auditoria.registrar(usuario.getId(), usuario.getId(), null, usuario.getUnidadeId(),
                "LOGIN", "SUCESSO", "USUARIO", requestId);
        return new Login(token, "Bearer", jwt.expiresInSeconds(),
                new UsuarioResumo(usuario.getId(), usuario.getNome(), usuario.getPerfil(), usuario.getUnidadeId()));
    }

    @Transactional
    public PerfilPublico meuPerfil(UsuarioAutenticado principal, UUID requestId) {
        var usuario = ativo(principal);
        boolean fidelidade = consentimentos
                .findFirstByUsuarioIdAndFinalidadeOrderByIdDesc(usuario.getId(), "FIDELIDADE")
                .map(ConsentimentoEntity::isConsentido).orElse(false);
        auditoria.registrar(principal.id(), usuario.getId(), null, usuario.getUnidadeId(),
                "ACESSO_DADO_PESSOAL", "SUCESSO", "USUARIO", requestId);
        return new PerfilPublico(usuario.getId(), usuario.getNome(), usuario.getEmail(),
                usuario.getPerfil(), usuario.getUnidadeId(), fidelidade);
    }

    @Transactional
    public ConsentimentoEstado consultarConsentimento(UsuarioAutenticado principal, UUID requestId) {
        if (principal.perfil() != Perfil.CLIENTE) {
            throw new AcessoNegadoException();
        }
        ativo(principal);
        auditoria.registrar(principal.id(), principal.id(), null,
                "ACESSO_DADO_PESSOAL", "SUCESSO", "CONSENTIMENTO", requestId);
        return consentimentos.findFirstByUsuarioIdAndFinalidadeOrderByIdDesc(principal.id(), "FIDELIDADE")
                .map(e -> new ConsentimentoEstado(e.isConsentido(), e.getVersaoTexto(), e.getRegistradoEm()))
                .orElse(new ConsentimentoEstado(false, "v1", null));
    }

    @Transactional
    public ConsentimentoEstado registrarConsentimento(UsuarioAutenticado principal, boolean consentido, UUID requestId) {
        if (principal.perfil() != Perfil.CLIENTE) {
            throw new AcessoNegadoException();
        }
        ativo(principal);
        var evento = consentimentos.save(new ConsentimentoEntity(principal.id(), consentido, requestId));
        auditoria.registrar(principal.id(), principal.id(), null,
                "CONSENTIMENTO_FIDELIDADE", consentido ? "CONCEDIDO" : "REVOGADO", "CONSENTIMENTO", requestId);
        return new ConsentimentoEstado(evento.isConsentido(), evento.getVersaoTexto(), evento.getRegistradoEm());
    }

    private UsuarioEntity ativo(UsuarioAutenticado principal) {
        return usuarios.findById(principal.id()).filter(UsuarioEntity::isAtivo)
                .orElseThrow(CredenciaisInvalidasException::new);
    }

    public record Cadastro(Long id, String nome, String email, Perfil perfil, boolean consentimentoFidelidade) { }
    public record UsuarioResumo(Long id, String nome, Perfil perfil, Long unidadeId) { }
    public record Login(String accessToken, String tokenType, long expiresIn, UsuarioResumo user) { }
    public record PerfilPublico(Long id, String nome, String email, Perfil perfil, Long unidadeId,
                               boolean consentimentoFidelidade) { }
    public record ConsentimentoEstado(boolean consentido, String versaoTexto, Instant registradoEm) { }
}
