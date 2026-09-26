-- V3 deixava uma senha fixa e versionada (Admin@123) — qualquer um lendo o repositório
-- conseguia logar como admin antes de qualquer troca de senha. Troco o hash por um bcrypt de
-- um valor aleatório descartado (não documentado em lugar nenhum, gerado só pra criar este
-- hash) — ninguém consegue logar com ele. A senha real é definida em runtime por
-- AdminPasswordSeeder, lida de ADMIN_SEED_PASSWORD (SSM /fiapx/admin/password via Terraform),
-- só enquanto must_change_password continuar true.
UPDATE video_api.users
SET password_hash = '$2a$10$IXpcEF7vts1NOrdY0ipeQuae1TXG.mUIkm9PdUxxGN/K6Jj.piDIG'
WHERE email = 'admin@fiapx.local';
