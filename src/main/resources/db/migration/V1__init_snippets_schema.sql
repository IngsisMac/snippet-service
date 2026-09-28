CREATE TABLE snippets (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    owner_id VARCHAR(255) NOT NULL,
    language VARCHAR(50) NOT NULL,
    version VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE snippet_statuses (
    snippet_id UUID PRIMARY KEY REFERENCES snippets(id) ON DELETE CASCADE,
    status VARCHAR(50) NOT NULL,
    rules_version INT NOT NULL DEFAULT 0,
    findings_count INT NOT NULL DEFAULT 0,
    last_checked_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE test_cases (
    id UUID PRIMARY KEY,
    snippet_id UUID NOT NULL REFERENCES snippets(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    inputs TEXT NOT NULL,
    expected_outputs TEXT NOT NULL,
    env TEXT
);

CREATE INDEX idx_snippets_owner ON snippets(owner_id);
CREATE INDEX idx_test_cases_snippet ON test_cases(snippet_id);
