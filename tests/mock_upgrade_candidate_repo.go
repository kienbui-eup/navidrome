package tests

import (
	"errors"
	"sync"
	"time"

	"github.com/vi2play/vi2play/model"
	"github.com/vi2play/vi2play/model/id"
)

func CreateMockUpgradeCandidateRepo() *MockUpgradeCandidateRepo {
	return &MockUpgradeCandidateRepo{
		Data: make(map[string]*model.UpgradeCandidate),
	}
}

// MockUpgradeCandidateRepo is guarded by mu because the Upgrader drives it
// from more than one goroutine at once (the approving request and the
// sequential background worker, plus Recover's startup sweep), unlike most of
// the other mock repos in this package. Get/GetAll return copies (like a real
// SQL-backed repo, which builds a fresh struct per query) rather than the
// live pointer stored in Data, so a caller mutating its own result — the
// normal Get-then-Put pattern — cannot race with another goroutine reading
// the same candidate via GetAll in the meantime.
type MockUpgradeCandidateRepo struct {
	Data    map[string]*model.UpgradeCandidate
	Err     bool
	Options model.QueryOptions
	mu      sync.RWMutex
}

func (m *MockUpgradeCandidateRepo) SetError(err bool) {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.Err = err
}

func (m *MockUpgradeCandidateRepo) Put(c *model.UpgradeCandidate) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	if m.Err {
		return errors.New("unexpected error")
	}
	now := time.Now()
	if c.ID == "" {
		c.ID = id.NewRandom()
		c.CreatedAt = now
	}
	c.UpdatedAt = now
	stored := *c
	m.Data[c.ID] = &stored
	return nil
}

func (m *MockUpgradeCandidateRepo) Get(id string) (*model.UpgradeCandidate, error) {
	m.mu.RLock()
	defer m.mu.RUnlock()
	if m.Err {
		return nil, errors.New("unexpected error")
	}
	if d, ok := m.Data[id]; ok {
		cp := *d
		return &cp, nil
	}
	return nil, model.ErrNotFound
}

func (m *MockUpgradeCandidateRepo) GetAll(qo ...model.QueryOptions) (model.UpgradeCandidates, error) {
	m.mu.Lock()
	if len(qo) > 0 {
		m.Options = qo[0]
	}
	m.mu.Unlock()
	m.mu.RLock()
	defer m.mu.RUnlock()
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
	m.mu.Lock()
	defer m.mu.Unlock()
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
	m.mu.Lock()
	if len(qo) > 0 {
		m.Options = qo[0]
	}
	m.mu.Unlock()
	m.mu.RLock()
	defer m.mu.RUnlock()
	if m.Err {
		return 0, errors.New("unexpected error")
	}
	return int64(len(m.Data)), nil
}

func (m *MockUpgradeCandidateRepo) Exists(mediaFileID, source, sourceRef string) (bool, error) {
	m.mu.RLock()
	defer m.mu.RUnlock()
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
