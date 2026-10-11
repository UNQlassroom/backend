CREATE TABLE IF NOT EXISTS webhook_events (
    id UUID PRIMARY KEY,
    payload TEXT NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    processed_at TIMESTAMP,
    error_message TEXT
);

CREATE INDEX IF NOT EXISTS idx_webhook_events_status_created_at 
ON webhook_events (status, created_at);
