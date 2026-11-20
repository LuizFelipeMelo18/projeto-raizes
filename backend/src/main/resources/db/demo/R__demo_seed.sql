-- Dados fictícios para os perfis local e test. Esta migration não integra o perfil de produção.
INSERT INTO unidade (id, nome, ativa) VALUES
    (1, 'Unidade Centro', TRUE),
    (2, 'Unidade Orla', TRUE)
ON CONFLICT (id) DO NOTHING;

INSERT INTO produto (id, nome, ativo) VALUES
    (10, 'Sanduíche regional', TRUE),
    (11, 'Suco de caju', TRUE),
    (12, 'Tapioca de queijo', TRUE)
ON CONFLICT (id) DO NOTHING;

-- Contas fictícias locais, com hash BCrypt da senha de demonstração publicada no README.
INSERT INTO usuario (id, unidade_id, nome, email, senha_hash, perfil, ativo) VALUES
    (100, NULL, 'Cliente de demonstração', 'cliente@raizes.invalid', '$2a$10$6svqpANyKBT6yEG5ObnZyu45ofQBGYxVwEbGlh6E7RTg7JDzQGo52', 'CLIENTE', TRUE),
    (101, 1, 'Totem de demonstração', 'totem@raizes.invalid', '$2a$10$6svqpANyKBT6yEG5ObnZyu45ofQBGYxVwEbGlh6E7RTg7JDzQGo52', 'TOTEM', TRUE),
    (102, 1, 'Atendente de demonstração', 'atendente@raizes.invalid', '$2a$10$6svqpANyKBT6yEG5ObnZyu45ofQBGYxVwEbGlh6E7RTg7JDzQGo52', 'ATENDENTE', TRUE),
    (103, 1, 'Cozinha de demonstração', 'cozinha@raizes.invalid', '$2a$10$6svqpANyKBT6yEG5ObnZyu45ofQBGYxVwEbGlh6E7RTg7JDzQGo52', 'COZINHA', TRUE),
    (104, 1, 'Gerente de demonstração', 'gerente@raizes.invalid', '$2a$10$6svqpANyKBT6yEG5ObnZyu45ofQBGYxVwEbGlh6E7RTg7JDzQGo52', 'GERENTE', TRUE),
    (105, NULL, 'Administrador de demonstração', 'admin@raizes.invalid', '$2a$10$6svqpANyKBT6yEG5ObnZyu45ofQBGYxVwEbGlh6E7RTg7JDzQGo52', 'ADMIN', TRUE),
    (106, 2, 'Atendente da Orla', 'atendente.orla@raizes.invalid', '$2a$10$6svqpANyKBT6yEG5ObnZyu45ofQBGYxVwEbGlh6E7RTg7JDzQGo52', 'ATENDENTE', TRUE),
    (107, 2, 'Gerente da Orla', 'gerente.orla@raizes.invalid', '$2a$10$6svqpANyKBT6yEG5ObnZyu45ofQBGYxVwEbGlh6E7RTg7JDzQGo52', 'GERENTE', TRUE),
    (108, NULL, 'Outro cliente de demonstração', 'cliente2@raizes.invalid', '$2a$10$6svqpANyKBT6yEG5ObnZyu45ofQBGYxVwEbGlh6E7RTg7JDzQGo52', 'CLIENTE', TRUE)
ON CONFLICT (id) DO NOTHING;

INSERT INTO cardapio_item (unidade_id, produto_id, preco, ativo) VALUES
    (1, 10, 39.90, TRUE),
    (1, 11, 12.50, TRUE),
    (1, 12, 24.00, TRUE),
    (2, 10, 42.00, TRUE),
    (2, 11, 13.00, TRUE)
ON CONFLICT (unidade_id, produto_id) DO NOTHING;

INSERT INTO estoque_saldo (unidade_id, produto_id, fisico, reservado, versao) VALUES
    (1, 10, 30, 0, 0),
    (1, 11, 50, 0, 0),
    (1, 12, 20, 0, 0),
    (2, 10, 25, 0, 0),
    (2, 11, 40, 0, 0)
ON CONFLICT (unidade_id, produto_id) DO NOTHING;

SELECT setval(pg_get_serial_sequence('unidade', 'id'), GREATEST((SELECT MAX(id) FROM unidade), 1), TRUE);
SELECT setval(pg_get_serial_sequence('produto', 'id'), GREATEST((SELECT MAX(id) FROM produto), 1), TRUE);
SELECT setval(pg_get_serial_sequence('usuario', 'id'), GREATEST((SELECT MAX(id) FROM usuario), 1), TRUE);
