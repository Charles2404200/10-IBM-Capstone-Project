-- Adds an optional client-supplied idempotency key to learner conversation
-- turns so a retried/duplicated "send message" request (double click, network
-- retry, React double-invoke, etc.) returns the already-persisted turn pair
-- instead of generating a second persona reply for the same learner message.
ALTER TABLE conversation_turns ADD COLUMN client_message_id VARCHAR(100);

-- One learner message per (meeting, clientMessageId) — persona turns never
-- carry a client_message_id (NULL), and NULLs are not constrained by a
-- unique index, so this only enforces idempotency for learner turns that
-- actually supplied a key.
CREATE UNIQUE INDEX idx_conversation_turns_meeting_client_msg
    ON conversation_turns (meeting_id, client_message_id)
    WHERE client_message_id IS NOT NULL;
