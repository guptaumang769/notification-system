-- V1: core schema for the notification platform.
-- Flyway owns the schema; Hibernate runs with ddl-auto=validate against it.

-- ---------------------------------------------------------------------------
-- templates: reusable messages with {{placeholder}} tokens, one row per channel.
-- ---------------------------------------------------------------------------
CREATE TABLE templates (
    id            BIGSERIAL PRIMARY KEY,
    template_key  VARCHAR(100)  NOT NULL,
    channel       VARCHAR(20)   NOT NULL,
    subject       VARCHAR(255),
    body          VARCHAR(2000) NOT NULL,
    created_at    TIMESTAMPTZ   NOT NULL,
    updated_at    TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uq_templates_key_channel UNIQUE (template_key, channel)
);

-- ---------------------------------------------------------------------------
-- user_preferences: per-channel opt-in + optional quiet-hours window.
-- ---------------------------------------------------------------------------
CREATE TABLE user_preferences (
    id                BIGSERIAL PRIMARY KEY,
    user_id           VARCHAR(100) NOT NULL,
    email_enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    sms_enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    push_enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    quiet_hours_start INTEGER,
    quiet_hours_end   INTEGER,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_user_preferences_user UNIQUE (user_id)
);

-- ---------------------------------------------------------------------------
-- notifications: the durable per-channel delivery record + state machine.
-- The unique idempotency_key is the durable backstop to the Redis SETNX dedupe,
-- so a duplicate event can never create a second row (effectively-once).
-- ---------------------------------------------------------------------------
CREATE TABLE notifications (
    id               BIGSERIAL PRIMARY KEY,
    user_id          VARCHAR(100)  NOT NULL,
    event_key        VARCHAR(100)  NOT NULL,
    channel          VARCHAR(20)   NOT NULL,
    template_key     VARCHAR(100)  NOT NULL,
    params_json      VARCHAR(2000),
    rendered_subject VARCHAR(255),
    rendered_body    VARCHAR(2000),
    status           VARCHAR(20)   NOT NULL,
    failure_reason   VARCHAR(255),
    attempts         INTEGER       NOT NULL DEFAULT 0,
    idempotency_key  VARCHAR(150)  NOT NULL,
    send_at          TIMESTAMPTZ,
    sent_at          TIMESTAMPTZ,
    created_at       TIMESTAMPTZ   NOT NULL,
    updated_at       TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uq_notifications_idempotency_key UNIQUE (idempotency_key)
);

-- History reads: newest-first per user (keyset pagination rides this index).
CREATE INDEX idx_notifications_user_id ON notifications (user_id, id DESC);
-- Scheduler poll: find due SCHEDULED rows cheaply.
CREATE INDEX idx_notifications_status_send_at ON notifications (status, send_at);
