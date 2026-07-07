package core

import (
	"context"
	"errors"
	"os"
	"path/filepath"

	"github.com/vi2play/vi2play/model"
	"github.com/vi2play/vi2play/model/request"
	"github.com/vi2play/vi2play/tests"
	. "github.com/onsi/ginkgo/v2"
	. "github.com/onsi/gomega"
)

var _ = Describe("Maintenance (Delete)", func() {
	var ds *tests.MockDataStore
	var mfRepo *extendedMediaFileRepo
	var candRepo *tests.MockUpgradeCandidateRepo
	var service Maintenance
	var ctx context.Context
	var libDir string

	// writeTrack creates a real audio file on disk (so os.Remove has
	// something to actually delete) and returns a MediaFile row pointing at
	// it via LibraryPath+Path (AbsolutePath()).
	writeTrack := func(id, albumID, title string) model.MediaFile {
		rel := id + ".flac"
		abs := filepath.Join(libDir, rel)
		Expect(os.WriteFile(abs, []byte("fake audio data for "+id), 0o644)).To(Succeed())
		return model.MediaFile{ID: id, AlbumID: albumID, Title: title, LibraryPath: libDir, Path: rel}
	}

	// writeUnremovableTrack creates a *directory* (with a file inside, so
	// it's non-empty) at the track's AbsolutePath. os.Remove on a non-empty
	// directory fails regardless of process privileges, giving a portable
	// "file cannot be removed" case without relying on permission bits.
	writeUnremovableTrack := func(id, albumID, title string) model.MediaFile {
		rel := id + ".flac"
		abs := filepath.Join(libDir, rel)
		Expect(os.MkdirAll(abs, 0o755)).To(Succeed())
		Expect(os.WriteFile(filepath.Join(abs, "nested"), []byte("x"), 0o644)).To(Succeed())
		return model.MediaFile{ID: id, AlbumID: albumID, Title: title, LibraryPath: libDir, Path: rel}
	}

	BeforeEach(func() {
		ctx = context.Background()
		ctx = request.WithUser(ctx, model.User{ID: "user1", IsAdmin: true})
		libDir = GinkgoT().TempDir()

		ds = createTestDataStore()
		mfRepo = ds.MockedMediaFile.(*extendedMediaFileRepo)
		candRepo = tests.CreateMockUpgradeCandidateRepo()
		ds.MockedUpgradeCandidate = candRepo
		service = NewMaintenance(ds)
	})

	Describe("DeleteMediaFiles", func() {
		It("deletes one song of a multi-song album, leaving the other song untouched", func() {
			mf1 := writeTrack("mf1", "album1", "Song One")
			mf2 := writeTrack("mf2", "album1", "Song Two")
			mfRepo.SetData(model.MediaFiles{mf1, mf2})

			err := service.DeleteMediaFiles(ctx, []string{"mf1"})
			Expect(err).ToNot(HaveOccurred())

			_, statErr := os.Stat(mf1.AbsolutePath())
			Expect(os.IsNotExist(statErr)).To(BeTrue(), "mf1's file should have been removed")

			_, statErr = os.Stat(mf2.AbsolutePath())
			Expect(statErr).ToNot(HaveOccurred(), "mf2's file should still exist")

			Expect(mfRepo.deleteMissingCalled).To(BeTrue())
			Expect(mfRepo.deletedIDs).To(Equal([]string{"mf1"}))
			Expect(ds.GCCalled).To(BeTrue())

			_, ok := mfRepo.Data["mf1"]
			Expect(ok).To(BeFalse(), "mf1's DB row should be gone")
			_, ok = mfRepo.Data["mf2"]
			Expect(ok).To(BeTrue(), "mf2's DB row should remain")
		})

		It("treats an already-absent file as success (idempotent)", func() {
			mf1 := model.MediaFile{ID: "mf1", AlbumID: "album1", Title: "Ghost", LibraryPath: libDir, Path: "does-not-exist.flac"}
			mfRepo.SetData(model.MediaFiles{mf1})

			err := service.DeleteMediaFiles(ctx, []string{"mf1"})
			Expect(err).ToNot(HaveOccurred())

			Expect(mfRepo.deleteMissingCalled).To(BeTrue())
			Expect(mfRepo.deletedIDs).To(Equal([]string{"mf1"}))
		})

		It("silently skips ids that no longer exist in the DB", func() {
			mf1 := writeTrack("mf1", "album1", "Song One")
			mfRepo.SetData(model.MediaFiles{mf1})

			err := service.DeleteMediaFiles(ctx, []string{"mf1", "does-not-exist-id"})
			Expect(err).ToNot(HaveOccurred())
			Expect(mfRepo.deletedIDs).To(Equal([]string{"mf1"}))
		})

		It("keeps the DB row and reports an error for a file that cannot be removed, while still deleting the rest of the batch", func() {
			mf1 := writeUnremovableTrack("mf1", "album1", "Stuck Song")
			mf2 := writeTrack("mf2", "album1", "Removable Song")
			mfRepo.SetData(model.MediaFiles{mf1, mf2})

			err := service.DeleteMediaFiles(ctx, []string{"mf1", "mf2"})
			Expect(err).To(HaveOccurred())
			Expect(err.Error()).To(ContainSubstring("mf1"))

			// mf1's on-disk directory is still there (removal failed).
			_, statErr := os.Stat(mf1.AbsolutePath())
			Expect(statErr).ToNot(HaveOccurred())

			// mf2 was removed from disk and from the DB.
			_, statErr = os.Stat(mf2.AbsolutePath())
			Expect(os.IsNotExist(statErr)).To(BeTrue())

			Expect(mfRepo.deletedIDs).To(Equal([]string{"mf2"}))

			_, ok := mfRepo.Data["mf1"]
			Expect(ok).To(BeTrue(), "mf1's DB row should be kept since its file could not be removed")
			_, ok = mfRepo.Data["mf2"]
			Expect(ok).To(BeFalse())
		})

		Context("when an upgrade is in progress", func() {
			It("blocks the whole batch and removes zero files when a target has an 'approved' candidate", func() {
				mf1 := writeTrack("mf1", "album1", "Song One")
				mf2 := writeTrack("mf2", "album1", "Song Two")
				mfRepo.SetData(model.MediaFiles{mf1, mf2})
				Expect(candRepo.Put(&model.UpgradeCandidate{
					MediaFileID: "mf1", Title: "Song One (better rip)",
					Status: model.UpgradeCandidateStatusApproved,
				})).To(Succeed())

				err := service.DeleteMediaFiles(ctx, []string{"mf1", "mf2"})
				Expect(err).To(HaveOccurred())
				Expect(errors.Is(err, ErrUpgradeInProgress)).To(BeTrue())
				Expect(err.Error()).To(ContainSubstring("mf1"))

				_, statErr := os.Stat(mf1.AbsolutePath())
				Expect(statErr).ToNot(HaveOccurred(), "mf1's file must not be touched")
				_, statErr = os.Stat(mf2.AbsolutePath())
				Expect(statErr).ToNot(HaveOccurred(), "mf2's file must not be touched either (whole batch fails clean)")

				Expect(mfRepo.deleteMissingCalled).To(BeFalse())
				Expect(ds.GCCalled).To(BeFalse())
			})

			It("blocks the batch when a target has a 'downloading' candidate", func() {
				mf1 := writeTrack("mf1", "album1", "Song One")
				mfRepo.SetData(model.MediaFiles{mf1})
				Expect(candRepo.Put(&model.UpgradeCandidate{
					MediaFileID: "mf1", Title: "Song One (better rip)",
					Status: model.UpgradeCandidateStatusDownloading,
				})).To(Succeed())

				err := service.DeleteMediaFiles(ctx, []string{"mf1"})
				Expect(err).To(HaveOccurred())
				Expect(errors.Is(err, ErrUpgradeInProgress)).To(BeTrue())

				_, statErr := os.Stat(mf1.AbsolutePath())
				Expect(statErr).ToNot(HaveOccurred())
				Expect(mfRepo.deleteMissingCalled).To(BeFalse())
			})

			It("proceeds normally when the only candidate is 'pending'", func() {
				mf1 := writeTrack("mf1", "album1", "Song One")
				mfRepo.SetData(model.MediaFiles{mf1})
				Expect(candRepo.Put(&model.UpgradeCandidate{
					MediaFileID: "mf1", Title: "Song One (maybe better)",
					Status: model.UpgradeCandidateStatusPending,
				})).To(Succeed())

				err := service.DeleteMediaFiles(ctx, []string{"mf1"})
				Expect(err).ToNot(HaveOccurred())

				_, statErr := os.Stat(mf1.AbsolutePath())
				Expect(os.IsNotExist(statErr)).To(BeTrue())
				Expect(mfRepo.deleteMissingCalled).To(BeTrue())
			})

			It("proceeds normally when the only candidate is 'needs_review'", func() {
				mf1 := writeTrack("mf1", "album1", "Song One")
				mfRepo.SetData(model.MediaFiles{mf1})
				Expect(candRepo.Put(&model.UpgradeCandidate{
					MediaFileID: "mf1", Title: "Song One (needs review)",
					Status: model.UpgradeCandidateStatusNeedsReview,
				})).To(Succeed())

				err := service.DeleteMediaFiles(ctx, []string{"mf1"})
				Expect(err).ToNot(HaveOccurred())
				Expect(mfRepo.deleteMissingCalled).To(BeTrue())
			})
		})
	})

	Describe("DeleteAlbum", func() {
		It("removes every track belonging to the album", func() {
			mf1 := writeTrack("mf1", "album1", "Song One")
			mf2 := writeTrack("mf2", "album1", "Song Two")
			other := writeTrack("mf3", "album2", "Unrelated Song")
			mfRepo.SetData(model.MediaFiles{mf1, mf2, other})

			err := service.DeleteAlbum(ctx, "album1")
			Expect(err).ToNot(HaveOccurred())

			Expect(mfRepo.deletedIDs).To(Equal([]string{"mf1", "mf2"}))

			_, statErr := os.Stat(mf1.AbsolutePath())
			Expect(os.IsNotExist(statErr)).To(BeTrue())
			_, statErr = os.Stat(mf2.AbsolutePath())
			Expect(os.IsNotExist(statErr)).To(BeTrue())

			// Track from a different album is untouched.
			_, statErr = os.Stat(other.AbsolutePath())
			Expect(statErr).ToNot(HaveOccurred())
			_, ok := mfRepo.Data["mf3"]
			Expect(ok).To(BeTrue())
		})

		It("is a no-op when the album has no tracks", func() {
			other := writeTrack("mf3", "album2", "Unrelated Song")
			mfRepo.SetData(model.MediaFiles{other})

			err := service.DeleteAlbum(ctx, "empty-album")
			Expect(err).ToNot(HaveOccurred())
			Expect(mfRepo.deleteMissingCalled).To(BeFalse())
			Expect(ds.GCCalled).To(BeFalse())
		})

		It("blocks deletion when a track in the album has an in-progress upgrade", func() {
			mf1 := writeTrack("mf1", "album1", "Song One")
			mf2 := writeTrack("mf2", "album1", "Song Two")
			mfRepo.SetData(model.MediaFiles{mf1, mf2})
			Expect(candRepo.Put(&model.UpgradeCandidate{
				MediaFileID: "mf2", Title: "Song Two (better rip)",
				Status: model.UpgradeCandidateStatusApproved,
			})).To(Succeed())

			err := service.DeleteAlbum(ctx, "album1")
			Expect(err).To(HaveOccurred())
			Expect(errors.Is(err, ErrUpgradeInProgress)).To(BeTrue())

			_, statErr := os.Stat(mf1.AbsolutePath())
			Expect(statErr).ToNot(HaveOccurred())
			_, statErr = os.Stat(mf2.AbsolutePath())
			Expect(statErr).ToNot(HaveOccurred())
			Expect(mfRepo.deleteMissingCalled).To(BeFalse())
		})
	})
})
