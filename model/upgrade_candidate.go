package model

import "time"

// UpgradeCandidate statuses, tracking the lifecycle of a candidate from discovery
// to replacement (or rejection/failure). See docs/superpowers/specs/2026-07-06-quality-upgrader-design.md.
const (
	UpgradeCandidateStatusPending     = "pending"
	UpgradeCandidateStatusApproved    = "approved"
	UpgradeCandidateStatusRejected    = "rejected"
	UpgradeCandidateStatusDownloading = "downloading"
	UpgradeCandidateStatusNeedsReview = "needs_review"
	UpgradeCandidateStatusReplaced    = "replaced"
	UpgradeCandidateStatusFailed      = "failed"
)

type UpgradeCandidate struct {
	ID          string    `structs:"id"            json:"id"`
	MediaFileID string    `structs:"media_file_id" json:"mediaFileId"`
	LibraryID   int       `structs:"library_id"    json:"libraryId"`
	Source      string    `structs:"source"        json:"source"`
	SourceRef   string    `structs:"source_ref"    json:"sourceRef"`
	Title       string    `structs:"title"         json:"title"`
	Format      string    `structs:"format"        json:"format"`
	EstBitRate  int       `structs:"est_bitrate"   json:"estBitRate"`
	EstSize     int64     `structs:"est_size"      json:"estSize"`
	MatchScore  int       `structs:"match_score"   json:"matchScore"`
	Status      string    `structs:"status"        json:"status"`
	VerifyInfo  string    `structs:"verify_info"   json:"verifyInfo,omitempty"`
	Error       string    `structs:"error"         json:"error,omitempty"`
	ReviewedBy  string    `structs:"reviewed_by"   json:"reviewedBy,omitempty"`
	CreatedAt   time.Time `structs:"created_at"    json:"createdAt"`
	UpdatedAt   time.Time `structs:"updated_at"    json:"updatedAt"`
}

type UpgradeCandidates []UpgradeCandidate

type UpgradeCandidateRepository interface {
	Put(c *UpgradeCandidate) error
	Get(id string) (*UpgradeCandidate, error)
	GetAll(options ...QueryOptions) (UpgradeCandidates, error)
	Delete(id string) error
	CountAll(options ...QueryOptions) (int64, error)
	// Exists checks the (media_file_id, source, source_ref) unique index, used to skip
	// re-inserting a candidate already in the queue (including ones already rejected).
	Exists(mediaFileID, source, sourceRef string) (bool, error)
}
