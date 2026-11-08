CREATE TABLE test_case_results (
    test_case_id UUID PRIMARY KEY REFERENCES test_cases(id) ON DELETE CASCADE,
    snippet_id UUID NOT NULL REFERENCES snippets(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL,
    actual_outputs TEXT NOT NULL DEFAULT '[]',
    errors TEXT NOT NULL DEFAULT '[]',
    executed_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_test_case_results_snippet ON test_case_results(snippet_id);
