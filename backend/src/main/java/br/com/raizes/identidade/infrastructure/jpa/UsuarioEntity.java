package br.com.raizes.identidade.infrastructure.jpa;

import br.com.raizes.identidade.domain.Perfil;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "usuario")
public class UsuarioEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "unidade_id")
    private Long unidadeId;
    @Column(nullable = false, length = 120)
    private String nome;
    @Column(nullable = false, length = 254, unique = true)
    private String email;
    @Column(name = "senha_hash", nullable = false, length = 100)
    private String senhaHash;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Perfil perfil;
    @Column(nullable = false)
    private boolean ativo;

    protected UsuarioEntity() {
    }

    public UsuarioEntity(String nome, String email, String senhaHash) {
        this.nome = nome;
        this.email = email;
        this.senhaHash = senhaHash;
        this.perfil = Perfil.CLIENTE;
        this.ativo = true;
    }

    public Long getId() { return id; }
    public Long getUnidadeId() { return unidadeId; }
    public String getNome() { return nome; }
    public String getEmail() { return email; }
    public String getSenhaHash() { return senhaHash; }
    public Perfil getPerfil() { return perfil; }
    public boolean isAtivo() { return ativo; }
}
