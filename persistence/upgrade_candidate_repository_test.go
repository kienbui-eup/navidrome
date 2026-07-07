package persistence

import (
	"context"

	"github.com/vi2play/vi2play/log"
	"github.com/vi2play/vi2play/model"
	"github.com/vi2play/vi2play/model/request"
	. "github.com/onsi/ginkgo/v2"
	. "github.com/onsi/gomega"
)

var _ = Describe("UpgradeCandidateRepository", func() {
	var repo model.UpgradeCandidateRepository

	BeforeEach(func() {
		ctx := log.NewContext(context.TODO())
		ctx = request.WithUser(ctx, adminUser)
		repo = NewUpgradeCandidateRepository(ctx, GetDBXBuilder())
	})

	AfterEach(func() {
		all, err := repo.GetAll()
		Expect(err).ToNot(HaveOccurred())
		for _, c := range all {
			Expect(repo.Delete(c.ID)).To(Succeed())
		}
	})

	newCandidate := func() *model.UpgradeCandidate {
		return &model.UpgradeCandidate{
			MediaFileID: songDayInALife.ID,
			LibraryID:   1,
			Source:      "archive",
			SourceRef:   "identifier/track.flac",
			Title:       "A Day In A Life (FLAC)",
			Format:      "flac",
			EstBitRate:  0,
			EstSize:     123456,
			MatchScore:  85,
			Status:      model.UpgradeCandidateStatusPending,
		}
	}

	Describe("CRUD roundtrip", func() {
		It("creates, reads, updates and deletes a candidate", func() {
			c := newCandidate()
			Expect(repo.Put(c)).To(Succeed())
			Expect(c.ID).ToNot(BeEmpty())
			Expect(c.CreatedAt).ToNot(BeZero())
			Expect(c.UpdatedAt).ToNot(BeZero())

			stored, err := repo.Get(c.ID)
			Expect(err).ToNot(HaveOccurred())
			Expect(stored.MediaFileID).To(Equal(c.MediaFileID))
			Expect(stored.Source).To(Equal("archive"))
			Expect(stored.SourceRef).To(Equal("identifier/track.flac"))
			Expect(stored.MatchScore).To(Equal(85))
			Expect(stored.Status).To(Equal(model.UpgradeCandidateStatusPending))

			count, err := repo.CountAll()
			Expect(err).ToNot(HaveOccurred())
			Expect(count).To(Equal(int64(1)))

			all, err := repo.GetAll()
			Expect(err).ToNot(HaveOccurred())
			Expect(all).To(HaveLen(1))

			stored.Status = model.UpgradeCandidateStatusApproved
			stored.ReviewedBy = adminUser.ID
			Expect(repo.Put(stored)).To(Succeed())

			updated, err := repo.Get(c.ID)
			Expect(err).ToNot(HaveOccurred())
			Expect(updated.Status).To(Equal(model.UpgradeCandidateStatusApproved))
			Expect(updated.ReviewedBy).To(Equal(adminUser.ID))
			Expect(updated.ID).To(Equal(c.ID))

			Expect(repo.Delete(c.ID)).To(Succeed())
			_, err = repo.Get(c.ID)
			Expect(err).To(MatchError(model.ErrNotFound))

			count, err = repo.CountAll()
			Expect(err).ToNot(HaveOccurred())
			Expect(count).To(Equal(int64(0)))
		})

		It("returns ErrNotFound for a nonexistent id", func() {
			_, err := repo.Get("does-not-exist")
			Expect(err).To(MatchError(model.ErrNotFound))
		})

		It("does not error when deleting a nonexistent id", func() {
			// Delete is a plain DELETE ... WHERE id = ?; matching zero rows is not an error
			// (consistent with other simple repositories in this package).
			err := repo.Delete("does-not-exist")
			Expect(err).ToNot(HaveOccurred())
		})
	})

	Describe("Exists / unique index dedup", func() {
		It("reports false before insert and true after", func() {
			c := newCandidate()
			exists, err := repo.Exists(c.MediaFileID, c.Source, c.SourceRef)
			Expect(err).ToNot(HaveOccurred())
			Expect(exists).To(BeFalse())

			Expect(repo.Put(c)).To(Succeed())

			exists, err = repo.Exists(c.MediaFileID, c.Source, c.SourceRef)
			Expect(err).ToNot(HaveOccurred())
			Expect(exists).To(BeTrue())
		})

		It("does not match a different source or source_ref", func() {
			c := newCandidate()
			Expect(repo.Put(c)).To(Succeed())

			exists, err := repo.Exists(c.MediaFileID, "drive", c.SourceRef)
			Expect(err).ToNot(HaveOccurred())
			Expect(exists).To(BeFalse())

			exists, err = repo.Exists(c.MediaFileID, c.Source, "some/other/ref")
			Expect(err).ToNot(HaveOccurred())
			Expect(exists).To(BeFalse())
		})

		It("rejects a second insert with the same (media_file_id, source, source_ref)", func() {
			first := newCandidate()
			Expect(repo.Put(first)).To(Succeed())

			second := newCandidate()
			err := repo.Put(second)
			Expect(err).To(HaveOccurred())

			count, err := repo.CountAll()
			Expect(err).ToNot(HaveOccurred())
			Expect(count).To(Equal(int64(1)))
		})

		It("still reports existing after the candidate would have been rejected (no re-insert)", func() {
			c := newCandidate()
			c.Status = model.UpgradeCandidateStatusRejected
			Expect(repo.Put(c)).To(Succeed())

			exists, err := repo.Exists(c.MediaFileID, c.Source, c.SourceRef)
			Expect(err).ToNot(HaveOccurred())
			Expect(exists).To(BeTrue())
		})
	})
})
