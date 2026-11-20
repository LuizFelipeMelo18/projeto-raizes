package br.com.raizes.pedidos.infrastructure;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class IdempotenciaPedidoLock {
    private final JdbcTemplate jdbc;

    public IdempotenciaPedidoLock(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void bloquear(UUID chave) {
        // Lock transacional do PostgreSQL: a consulta da chave acontece depois do bloqueio.
        long valor = chave.getMostSignificantBits() ^ chave.getLeastSignificantBits();
        jdbc.execute((java.sql.Connection connection) -> {
            try (var statement = connection.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
                statement.setLong(1, valor);
                statement.execute();
            }
            return null;
        });
    }
}
