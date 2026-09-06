-- 掌上律师 MVP 数据库初始化脚本
-- 执行方式: docker-compose up -d 后自动执行

-- 启用 pgvector 扩展
CREATE EXTENSION IF NOT EXISTS vector;

-- 用户表
CREATE TABLE IF NOT EXISTS app_user (
    user_id        BIGSERIAL PRIMARY KEY,
    openid         VARCHAR(64) UNIQUE NOT NULL,
    nickname       VARCHAR(128),
    avatar_url     VARCHAR(512),
    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 案件档案
CREATE TABLE IF NOT EXISTS case_profile (
    case_id        BIGSERIAL PRIMARY KEY,
    user_id        BIGINT REFERENCES app_user(user_id),
    domain_type    VARCHAR(32) NOT NULL DEFAULT 'LABOR',
    status         VARCHAR(32) NOT NULL DEFAULT 'COLLECTING',
    title          VARCHAR(256),
    facts_json     JSONB NOT NULL DEFAULT '{}',
    issues_json    JSONB NOT NULL DEFAULT '[]',
    laws_json      JSONB NOT NULL DEFAULT '[]',
    timeline_json  JSONB NOT NULL DEFAULT '[]',
    evidence_refs  JSONB NOT NULL DEFAULT '[]',
    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 对话会话
CREATE TABLE IF NOT EXISTS chat_session (
    session_id     BIGSERIAL PRIMARY KEY,
    case_id        BIGINT REFERENCES case_profile(case_id),
    user_id        BIGINT REFERENCES app_user(user_id),
    summary        VARCHAR(512),
    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 对话消息
CREATE TABLE IF NOT EXISTS chat_message (
    message_id     BIGSERIAL PRIMARY KEY,
    session_id     BIGINT REFERENCES chat_session(session_id),
    role           VARCHAR(16) NOT NULL,
    content        TEXT NOT NULL,
    evidence_refs  JSONB,
    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 法律之星查询缓存
CREATE TABLE IF NOT EXISTS law_cache (
    cache_key      VARCHAR(128) PRIMARY KEY,
    source         VARCHAR(32) NOT NULL,
    response_json  JSONB NOT NULL,
    expires_at     TIMESTAMP NOT NULL,
    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 向量表
-- embedding_text 为实体当前实际读写的文本向量列（与 H2 schema.sql 对齐）；
-- embedding (pgvector) 为后续向量检索预留，当前无代码写入。
CREATE TABLE IF NOT EXISTS domain_knowledge_vector (
    id             BIGSERIAL PRIMARY KEY,
    domain_type    VARCHAR(32) NOT NULL,
    content_type   VARCHAR(32) NOT NULL,
    content_id     VARCHAR(64) NOT NULL,
    text_chunk     TEXT NOT NULL,
    embedding_text TEXT,
    embedding      vector(1536),
    metadata_json  JSONB DEFAULT '{}',
    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 时间轴事件表
CREATE TABLE IF NOT EXISTS timeline_event (
    event_id       VARCHAR(36) PRIMARY KEY,
    case_id        BIGINT REFERENCES case_profile(case_id),
    event_time     VARCHAR(32),
    description    TEXT,
    type           VARCHAR(32) DEFAULT 'USER_ACTION',
    is_deadline    BOOLEAN DEFAULT FALSE,
    reminder_text  VARCHAR(512),
    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_tle_case ON timeline_event(case_id);

-- 索引
CREATE INDEX IF NOT EXISTS idx_dkv_domain ON domain_knowledge_vector(domain_type);
CREATE INDEX IF NOT EXISTS idx_dkv_content_type ON domain_knowledge_vector(content_type);
CREATE INDEX IF NOT EXISTS idx_dkv_embedding ON domain_knowledge_vector USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
CREATE INDEX IF NOT EXISTS idx_case_profile_user ON case_profile(user_id);
CREATE INDEX IF NOT EXISTS idx_case_profile_domain ON case_profile(domain_type);
CREATE INDEX IF NOT EXISTS idx_chat_session_case ON chat_session(case_id);
CREATE INDEX IF NOT EXISTS idx_chat_message_session ON chat_message(session_id);
CREATE INDEX IF NOT EXISTS idx_law_cache_expires ON law_cache(expires_at) WHERE expires_at > CURRENT_TIMESTAMP;
