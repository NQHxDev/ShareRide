-- Table: roles
CREATE TABLE IF NOT EXISTS roles (
    role_id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    role_code VARCHAR(64) NOT NULL,
    role_name VARCHAR(128) NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(64),
    updated_by VARCHAR(64),
    CONSTRAINT uq_roles_role_code UNIQUE (role_code)
);

CREATE INDEX IF NOT EXISTS idx_roles_role_code ON roles(role_code);

-- Table: permissions
CREATE TABLE IF NOT EXISTS permissions (
    permission_id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    permission_code VARCHAR(64) NOT NULL,
    permission_name VARCHAR(128) NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(64),
    updated_by VARCHAR(64),
    CONSTRAINT uq_permissions_permission_code UNIQUE (permission_code)
);

CREATE INDEX IF NOT EXISTS idx_permissions_permission_code ON permissions(permission_code);

-- Table: role_permissions
CREATE TABLE IF NOT EXISTS role_permissions (
    role_id UUID NOT NULL,
    permission_id UUID NOT NULL,
    assigned_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    assigned_by VARCHAR(64),
    CONSTRAINT pk_role_permissions PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES roles(role_id) ON DELETE CASCADE,
    CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions(permission_id) ON DELETE CASCADE
);

-- Table: accounts
CREATE TABLE IF NOT EXISTS accounts (
    account_id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    email VARCHAR(128) NOT NULL,
    phone_number VARCHAR(32) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(64),
    updated_by VARCHAR(64),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    deleted_by VARCHAR(64),
    CONSTRAINT uq_accounts_email UNIQUE (email),
    CONSTRAINT uq_accounts_phone UNIQUE (phone_number)
);

CREATE INDEX IF NOT EXISTS idx_accounts_email ON accounts(email);
CREATE INDEX IF NOT EXISTS idx_accounts_phone ON accounts(phone_number);
CREATE INDEX IF NOT EXISTS idx_accounts_status ON accounts(status);
CREATE INDEX IF NOT EXISTS idx_accounts_deleted ON accounts(deleted);

-- Table: account_roles
CREATE TABLE IF NOT EXISTS account_roles (
    account_id UUID NOT NULL,
    role_id UUID NOT NULL,
    assigned_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    assigned_by VARCHAR(64),
    CONSTRAINT pk_account_roles PRIMARY KEY (account_id, role_id),
    CONSTRAINT fk_account_roles_account FOREIGN KEY (account_id) REFERENCES accounts(account_id) ON DELETE CASCADE,
    CONSTRAINT fk_account_roles_role FOREIGN KEY (role_id) REFERENCES roles(role_id) ON DELETE CASCADE
);

-- Table: sessions
CREATE TABLE IF NOT EXISTS sessions (
    session_id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    account_id UUID NOT NULL,
    device_id VARCHAR(128),
    device_name VARCHAR(128),
    device_type VARCHAR(32),
    ip_address VARCHAR(45),
    location VARCHAR(255),
    user_agent TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    last_active_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(64),
    updated_by VARCHAR(64),
    CONSTRAINT fk_sessions_account FOREIGN KEY (account_id) REFERENCES accounts(account_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_sessions_account ON sessions(account_id);
CREATE INDEX IF NOT EXISTS idx_sessions_account_status ON sessions(account_id, status);
CREATE INDEX IF NOT EXISTS idx_sessions_device ON sessions(account_id, device_id);
CREATE INDEX IF NOT EXISTS idx_sessions_expires_at ON sessions(expires_at);

-- Table: outbox_messages
CREATE TABLE IF NOT EXISTS outbox_messages (
    outbox_message_id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    payload TEXT NOT NULL,
    trace_id VARCHAR(64),
    status VARCHAR(20) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE,
    error_message VARCHAR(1000)
);

CREATE INDEX IF NOT EXISTS idx_outbox_status_created ON outbox_messages(status, created_at);
CREATE INDEX IF NOT EXISTS idx_outbox_aggregate ON outbox_messages(aggregate_type, aggregate_id);

-- Deterministic UUIDs for standard roles and permissions to ensure consistency across environments
INSERT INTO roles (role_id, version, role_code, role_name, description, created_at, updated_at, created_by)
VALUES
    ('01a1c34e-ac19-7478-8962-9a25e4dedd89', 0, 'ROLE_RIDER', 'Rider Role', 'Khách hàng sử dụng dịch vụ đặt xe', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('01a1c34e-d3bf-732f-b000-623c85392a58', 0, 'ROLE_DRIVER', 'Driver Role', 'Tài xế đối tác nhận chuyến xe', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('01a1c34f-0723-70db-8b6d-bece2031d8af', 0, 'ROLE_ADMIN', 'Administrator Role', 'Quản trị viên toàn hệ thống', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('01a1c34f-1e16-7582-a8c3-3f902537f0e9', 0, 'ROLE_SUPPORT', 'Customer Support Role', 'Nhân viên hỗ trợ khách hàng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (role_code) DO NOTHING;

INSERT INTO permissions (permission_id, version, permission_code, permission_name, description, created_at, updated_at, created_by)
VALUES
    ('01a1c34f-4b42-745d-aeeb-dd5ad94e656c', 0, 'ride:create', 'Create Ride Request', 'Cho phép tạo chuyến xe mới', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('01a1c34f-628c-7457-b5a8-13d6ce32bb7a', 0, 'ride:read', 'Read Ride Info', 'Cho phép xem chi tiết chuyến xe', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('01a1c34f-8d2f-74cf-af98-2d107d5d0030', 0, 'ride:accept', 'Accept Ride', 'Cho phép tài xế nhận chuyến xe', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('01a1c34f-a7aa-758a-ba0c-09814aa82929', 0, 'account:read', 'Read Account Details', 'Cho phép xem thông tin tài khoản', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('01a1c34f-d0c9-739a-bfff-a015918d8372', 0, 'account:write', 'Update Account', 'Cho phép chỉnh sửa thông tin tài khoản', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('01a1c34f-ef79-7299-a5aa-5ff348bc51f2', 0, 'session:read', 'Read Sessions', 'Cho phép xem danh sách thiết bị đang đăng nhập', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('01a1c350-0dda-70bb-b3bc-ba2d636a22da', 0, 'session:revoke', 'Revoke Session', 'Cho phép thu hồi phiên đăng nhập từ xa', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system')
ON CONFLICT (permission_code) DO NOTHING;

-- Grant standard permissions to ROLE_RIDER
INSERT INTO role_permissions (role_id, permission_id, assigned_at, assigned_by)
SELECT r.role_id, p.permission_id, CURRENT_TIMESTAMP, 'system'
FROM roles r, permissions p
WHERE r.role_code = 'ROLE_RIDER' AND p.permission_code IN ('ride:create', 'ride:read', 'account:read', 'account:write', 'session:read', 'session:revoke')
ON CONFLICT DO NOTHING;

-- Grant standard permissions to ROLE_DRIVER
INSERT INTO role_permissions (role_id, permission_id, assigned_at, assigned_by)
SELECT r.role_id, p.permission_id, CURRENT_TIMESTAMP, 'system'
FROM roles r, permissions p
WHERE r.role_code = 'ROLE_DRIVER' AND p.permission_code IN ('ride:read', 'ride:accept', 'account:read', 'account:write', 'session:read', 'session:revoke')
ON CONFLICT DO NOTHING;

-- Grant ALL permissions to ROLE_ADMIN
INSERT INTO role_permissions (role_id, permission_id, assigned_at, assigned_by)
SELECT r.role_id, p.permission_id, CURRENT_TIMESTAMP, 'system'
FROM roles r, permissions p
WHERE r.role_code = 'ROLE_ADMIN'
ON CONFLICT DO NOTHING;
