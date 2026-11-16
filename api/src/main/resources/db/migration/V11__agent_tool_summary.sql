-- Nullable for existing rows and older application inserts.
ALTER TABLE chat_message ADD COLUMN tool_calls_json JSON NULL;
