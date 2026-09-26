-- Item 14 da revisão crítica: JWT é stateless por padrão — trocar a senha ou excluir o usuário
-- não invalidava tokens já emitidos (sensível pro caso de um admin removido continuar operando
-- com o token antigo até ele expirar sozinho). tokens_valid_after é a linha de corte: um token
-- só é aceito se o seu "issued at" não for anterior a este timestamp (ver JwtAuthenticationFilter
-- e User#hasValidToken). DEFAULT now() também revoga, de uma vez só, qualquer token emitido
-- antes deste deploy — efeito colateral aceitável, fecha a lacuna pra quem já estava logado
-- antes desta correção existir.
ALTER TABLE video_api.users ADD COLUMN tokens_valid_after TIMESTAMPTZ NOT NULL DEFAULT now();
