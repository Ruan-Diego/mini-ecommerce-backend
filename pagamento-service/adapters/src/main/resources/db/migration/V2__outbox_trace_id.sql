-- Trace id da requisição que gerou o evento (cabeçalho X-Trace-Id). O relay o repassa na mensagem
-- (cabeçalho x-trace-id), para ligar o log de quem publica ao log de quem consome.
ALTER TABLE outbox ADD COLUMN trace_id VARCHAR(64) NULL;
