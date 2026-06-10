-- V3: Answer feedback table — thumbs up/down on every AI response (Phase 1)
-- rating: 1 = helpful, -1 = not helpful

CREATE TABLE IF NOT EXISTS answer_feedback (
    id              UUID       PRIMARY KEY DEFAULT gen_random_uuid(),
    chat_message_id UUID       NOT NULL,
    user_id         UUID       NOT NULL,
    rating          SMALLINT   NOT NULL CHECK (rating IN (-1, 1)),
    feedback_text   TEXT,
    created_at      TIMESTAMP  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_feedback_message ON answer_feedback(chat_message_id);
CREATE INDEX IF NOT EXISTS idx_feedback_user    ON answer_feedback(user_id);
CREATE INDEX IF NOT EXISTS idx_feedback_created ON answer_feedback(created_at);
