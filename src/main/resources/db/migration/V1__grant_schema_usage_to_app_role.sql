-- Runs as the schema owner. The application connects as a separate role that owns
-- nothing (ADR-0015); each table migration grants it the privileges it needs.
GRANT USAGE ON SCHEMA public TO "${appDbUser}";
