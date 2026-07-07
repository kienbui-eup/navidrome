package migrations

import (
	"context"
	"database/sql"

	"github.com/pressly/goose/v3"
)

func init() {
	goose.AddMigrationContext(upCreateUpgradeCandidateTable, downCreateUpgradeCandidateTable)
}

func upCreateUpgradeCandidateTable(ctx context.Context, tx *sql.Tx) error {
	_, err := tx.ExecContext(ctx, `
CREATE TABLE upgrade_candidate (
	id VARCHAR(255) NOT NULL PRIMARY KEY,
	media_file_id VARCHAR(255) NOT NULL
		REFERENCES media_file(id)
			ON DELETE CASCADE
			ON UPDATE CASCADE,
	library_id INTEGER NOT NULL
		REFERENCES library(id)
			ON DELETE CASCADE,
	source VARCHAR(20) NOT NULL,
	source_ref VARCHAR(255) NOT NULL,
	title VARCHAR(255) NOT NULL DEFAULT '',
	format VARCHAR(20) NOT NULL DEFAULT '',
	est_bitrate INTEGER NOT NULL DEFAULT 0,
	est_size INTEGER NOT NULL DEFAULT 0,
	match_score INTEGER NOT NULL DEFAULT 0,
	status VARCHAR(20) NOT NULL DEFAULT 'pending',
	verify_info JSONB,
	error TEXT NOT NULL DEFAULT '',
	reviewed_by VARCHAR(255) NOT NULL DEFAULT '',
	created_at DATETIME NOT NULL,
	updated_at DATETIME NOT NULL
);

CREATE UNIQUE INDEX upgrade_candidate_media_file_source_ref ON upgrade_candidate (media_file_id, source, source_ref);
CREATE INDEX upgrade_candidate_status ON upgrade_candidate (status);
CREATE INDEX upgrade_candidate_library_id ON upgrade_candidate (library_id);
`)
	return err
}

func downCreateUpgradeCandidateTable(ctx context.Context, tx *sql.Tx) error {
	_, err := tx.ExecContext(ctx, `DROP TABLE IF EXISTS upgrade_candidate;`)
	return err
}
