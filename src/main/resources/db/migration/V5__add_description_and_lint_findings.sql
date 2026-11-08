ALTER TABLE snippets ADD COLUMN description VARCHAR(1000);

ALTER TABLE snippet_statuses ADD COLUMN findings TEXT NOT NULL DEFAULT '[]';
