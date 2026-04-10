CREATE TYPE audit_initiator AS ENUM ('USER', 'ADMIN', 'SYSTEM');

CREATE TABLE audit_log (
    id              BIGSERIAL       PRIMARY KEY,
    user_id         BIGINT          REFERENCES users(id) ON DELETE SET NULL,
    action          VARCHAR(100)    NOT NULL,
    entity_type     VARCHAR(100),
    entity_id       BIGINT,
    initiated_by    audit_initiator NOT NULL DEFAULT 'USER',
    details         TEXT,
    timestamp       TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_log_timestamp ON audit_log(timestamp DESC);
CREATE INDEX idx_audit_log_user_id ON audit_log(user_id);
