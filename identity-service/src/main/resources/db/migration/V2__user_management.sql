ALTER TABLE users ADD COLUMN username TEXT;
CREATE UNIQUE INDEX idx_users_username ON users(username) WHERE username IS NOT NULL;

INSERT OR IGNORE INTO roles (code, name) VALUES ('MEMBER', 'Member');
INSERT OR IGNORE INTO roles (code, name) VALUES ('LIBRARIAN', 'Librarian');
INSERT OR IGNORE INTO roles (code, name) VALUES ('ADMIN', 'Administrator');

CREATE TABLE admin_audit_logs (
    id TEXT NOT NULL PRIMARY KEY,
    actor_user_id TEXT NOT NULL,
    action TEXT NOT NULL,
    target_user_id TEXT NOT NULL,
    outcome TEXT NOT NULL,
    trace_id TEXT,
    created_at TEXT NOT NULL
);
CREATE INDEX idx_admin_audit_logs_actor ON admin_audit_logs(actor_user_id);
CREATE INDEX idx_admin_audit_logs_target ON admin_audit_logs(target_user_id);
