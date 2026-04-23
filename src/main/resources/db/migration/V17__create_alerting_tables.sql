CREATE TABLE known_email_addresses (
    id          BIGSERIAL PRIMARY KEY,
    label       VARCHAR(255) NOT NULL,
    email       VARCHAR(255) NOT NULL,
    is_default  BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE TABLE alert_event_configs (
    id          BIGSERIAL PRIMARY KEY,
    event_type  VARCHAR(50)  NOT NULL UNIQUE,
    strategy    VARCHAR(50)  NOT NULL,
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE alert_event_config_recipients (
    config_id   BIGINT NOT NULL REFERENCES alert_event_configs (id) ON DELETE CASCADE,
    email_id    BIGINT NOT NULL REFERENCES known_email_addresses (id) ON DELETE CASCADE,
    PRIMARY KEY (config_id, email_id)
);
