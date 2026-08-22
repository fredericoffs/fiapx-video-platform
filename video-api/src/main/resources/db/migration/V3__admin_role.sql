ALTER TABLE video_api.users ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER';

-- Crio aqui o usuário admin seed — é o único jeito de virar admin neste projeto (não fiz
-- endpoint de promoção). Uso credenciais fixas pra ambiente local/demo, documentadas no Roteiro
-- de Verificação Manual. Gerei o hash com o mesmo BCryptPasswordEncoder do projeto
-- (senha: Admin@123).
INSERT INTO video_api.users (id, email, password_hash, role, created_at)
VALUES (
    gen_random_uuid(),
    'admin@fiapx.local',
    '$2a$10$nAi84X36v2ZA0Ufknqv9auT0sMlB6LEN.N1J9BVbLUQ.G8IhMJ05.',
    'ADMIN',
    now()
);
