-- Merge physical tables into one open session. A session keeps its primary table_id; the tables
-- attached later live in a JSON list, and the kitchen order mirrors their numbers for its label.
-- Idempotent (prod Flyway is not baselined, see V9/V11). Prod runs ddl-auto=validate.
ALTER TABLE sessions ADD COLUMN IF NOT EXISTS linked_tables jsonb NOT NULL DEFAULT '[]'::jsonb;
ALTER TABLE kitchen_orders ADD COLUMN IF NOT EXISTS linked_table_numbers jsonb NOT NULL DEFAULT '[]'::jsonb;
