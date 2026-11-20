package br.com.raizes.saude.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class VerificarSaudeService {
    private final JdbcTemplate jdbc;

    public VerificarSaudeService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean bancoDisponivel() {
        Integer result = jdbc.queryForObject("SELECT 1", Integer.class);
        return Integer.valueOf(1).equals(result);
    }
}
