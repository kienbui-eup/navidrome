package persistence

import (
	"context"
	"time"

	. "github.com/Masterminds/squirrel"
	"github.com/vi2play/vi2play/model"
	"github.com/vi2play/vi2play/model/id"
	"github.com/pocketbase/dbx"
)

type upgradeCandidateRepository struct {
	sqlRepository
}

func NewUpgradeCandidateRepository(ctx context.Context, db dbx.Builder) model.UpgradeCandidateRepository {
	r := &upgradeCandidateRepository{}
	r.ctx = ctx
	r.db = db
	r.tableName = "upgrade_candidate"
	return r
}

func (r *upgradeCandidateRepository) Put(c *model.UpgradeCandidate) error {
	c.UpdatedAt = time.Now()
	if c.ID == "" {
		c.ID = id.NewRandom()
		c.CreatedAt = c.UpdatedAt
	}
	_, err := r.put(c.ID, c)
	return err
}

func (r *upgradeCandidateRepository) Get(id string) (*model.UpgradeCandidate, error) {
	sel := r.newSelect().Where(Eq{"id": id}).Columns("*")
	var res model.UpgradeCandidate
	err := r.queryOne(sel, &res)
	return &res, err
}

func (r *upgradeCandidateRepository) GetAll(options ...model.QueryOptions) (model.UpgradeCandidates, error) {
	sel := r.newSelect(options...).Columns("*")
	res := model.UpgradeCandidates{}
	err := r.queryAll(sel, &res)
	return res, err
}

func (r *upgradeCandidateRepository) Delete(id string) error {
	return r.delete(Eq{"id": id})
}

func (r *upgradeCandidateRepository) CountAll(options ...model.QueryOptions) (int64, error) {
	return r.count(r.newSelect(), options...)
}

func (r *upgradeCandidateRepository) Exists(mediaFileID, source, sourceRef string) (bool, error) {
	return r.exists(And{
		Eq{"media_file_id": mediaFileID},
		Eq{"source": source},
		Eq{"source_ref": sourceRef},
	})
}

var _ model.UpgradeCandidateRepository = (*upgradeCandidateRepository)(nil)
