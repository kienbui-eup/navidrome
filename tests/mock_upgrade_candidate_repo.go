package tests

import (
	"errors"
	"time"

	"github.com/navidrome/navidrome/model"
	"github.com/navidrome/navidrome/model/id"
)

func CreateMockUpgradeCandidateRepo() *MockUpgradeCandidateRepo {
	return &MockUpgradeCandidateRepo{
		Data: make(map[string]*model.UpgradeCandidate),
	}
}

type MockUpgradeCandidateRepo struct {
	Data    map[string]*model.UpgradeCandidate
	Err     bool
	Options model.QueryOptions
}

func (m *MockUpgradeCandidateRepo) SetError(err bool) {
	m.Err = err
}

func (m *MockUpgradeCandidateRepo) Put(c *model.UpgradeCandidate) error {
	if m.Err {
		return errors.New("unexpected error")
	}
	now := time.Now()
	if c.ID == "" {
		c.ID = id.NewRandom()
		c.CreatedAt = now
	}
	c.UpdatedAt = now
	m.Data[c.ID] = c
	return nil
}

func (m *MockUpgradeCandidateRepo) Get(id string) (*model.UpgradeCandidate, error) {
	if m.Err {
		return nil, errors.New("unexpected error")
	}
	if d, ok := m.Data[id]; ok {
		return d, nil
	}
	return nil, model.ErrNotFound
}

func (m *MockUpgradeCandidateRepo) GetAll(qo ...model.QueryOptions) (model.UpgradeCandidates, error) {
	if len(qo) > 0 {
		m.Options = qo[0]
	}
	if m.Err {
		return nil, errors.New("unexpected error")
	}
	all := make(model.UpgradeCandidates, 0, len(m.Data))
	for _, c := range m.Data {
		all = append(all, *c)
	}
	return all, nil
}

func (m *MockUpgradeCandidateRepo) Delete(id string) error {
	if m.Err {
		return errors.New("unexpected error")
	}
	if _, ok := m.Data[id]; !ok {
		return model.ErrNotFound
	}
	delete(m.Data, id)
	return nil
}

func (m *MockUpgradeCandidateRepo) CountAll(qo ...model.QueryOptions) (int64, error) {
	if len(qo) > 0 {
		m.Options = qo[0]
	}
	if m.Err {
		return 0, errors.New("unexpected error")
	}
	return int64(len(m.Data)), nil
}

func (m *MockUpgradeCandidateRepo) Exists(mediaFileID, source, sourceRef string) (bool, error) {
	if m.Err {
		return false, errors.New("unexpected error")
	}
	for _, c := range m.Data {
		if c.MediaFileID == mediaFileID && c.Source == source && c.SourceRef == sourceRef {
			return true, nil
		}
	}
	return false, nil
}

var _ model.UpgradeCandidateRepository = (*MockUpgradeCandidateRepo)(nil)
