package nativeapi

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"net/http/httptest"

	"github.com/vi2play/vi2play/conf/configtest"
	"github.com/vi2play/vi2play/core"
	"github.com/vi2play/vi2play/core/auth"
	"github.com/vi2play/vi2play/model"
	"github.com/vi2play/vi2play/server"
	"github.com/vi2play/vi2play/tests"
	. "github.com/onsi/ginkgo/v2"
	. "github.com/onsi/gomega"
)

// fakeMaintenance is a stub core.Maintenance recording the arguments of every
// delete call, with injectable return values (same role as fakeUpgrader in
// upgrade_test.go).
type fakeMaintenance struct {
	deleteMediaFilesErr   error
	deleteMediaFilesCalls int
	lastDeletedIDs        []string

	deleteAlbumErr   error
	deleteAlbumCalls int
	lastAlbumID      string
}

func (f *fakeMaintenance) DeleteMissingFiles(context.Context, []string) error { return nil }

func (f *fakeMaintenance) DeleteAllMissingFiles(context.Context) error { return nil }

func (f *fakeMaintenance) DeleteMediaFiles(_ context.Context, ids []string) error {
	f.deleteMediaFilesCalls++
	f.lastDeletedIDs = ids
	return f.deleteMediaFilesErr
}

func (f *fakeMaintenance) DeleteAlbum(_ context.Context, albumID string) error {
	f.deleteAlbumCalls++
	f.lastAlbumID = albumID
	return f.deleteAlbumErr
}

var _ core.Maintenance = (*fakeMaintenance)(nil)

var _ = Describe("Deletion API", func() {
	var ds *tests.MockDataStore
	var router http.Handler
	var upgrader *fakeUpgrader
	var maintenance *fakeMaintenance
	var mediaFileRepo *tests.MockMediaFileRepo
	var adminToken, regularToken string

	BeforeEach(func() {
		DeferCleanup(configtest.SetupConfig())
		upgrader = &fakeUpgrader{}
		maintenance = &fakeMaintenance{}
		mediaFileRepo = tests.CreateMockMediaFileRepo()
		ds = &tests.MockDataStore{MockedMediaFile: mediaFileRepo}
		auth.Init(ds)
		nativeRouter := New(ds, nil, nil, nil, tests.NewMockLibraryService(), tests.NewMockUserService(), nil, upgrader, maintenance, nil, nil)
		router = server.JWTVerifier(nativeRouter)

		adminUser := model.User{ID: "admin-1", UserName: "admin", IsAdmin: true, NewPassword: "adminpass"}
		regularUser := model.User{ID: "user-1", UserName: "regular", IsAdmin: false, NewPassword: "userpass"}
		Expect(ds.User(context.TODO()).Put(&adminUser)).To(Succeed())
		Expect(ds.User(context.TODO()).Put(&regularUser)).To(Succeed())

		var err error
		adminToken, err = auth.CreateToken(&adminUser)
		Expect(err).ToNot(HaveOccurred())
		regularToken, err = auth.CreateToken(&regularUser)
		Expect(err).ToNot(HaveOccurred())
	})

	doReq := func(method, path, token string) *httptest.ResponseRecorder {
		req := createAuthenticatedRequest(method, path, nil, token)
		w := httptest.NewRecorder()
		router.ServeHTTP(w, req)
		return w
	}

	Describe("admin-only access", func() {
		DescribeTable("returns 403 for non-admin users, without deleting anything",
			func(method, path string) {
				w := doReq(method, path, regularToken)
				Expect(w.Code).To(Equal(http.StatusForbidden))
				Expect(maintenance.deleteMediaFilesCalls).To(BeZero())
				Expect(maintenance.deleteAlbumCalls).To(BeZero())
			},
			Entry("single song", "DELETE", "/song/mf-1"),
			Entry("batch songs", "DELETE", "/song?id=mf-1&id=mf-2"),
			Entry("album", "DELETE", "/album/al-1"),
		)

		It("returns 401 for unauthenticated requests", func() {
			req := createUnauthenticatedRequest("DELETE", "/song/mf-1", nil)
			w := httptest.NewRecorder()
			router.ServeHTTP(w, req)
			Expect(w.Code).To(Equal(http.StatusUnauthorized))
		})
	})

	Describe("DELETE /song/{id}", func() {
		It("deletes the song and returns its id", func() {
			w := doReq("DELETE", "/song/mf-1", adminToken)

			Expect(w.Code).To(Equal(http.StatusOK))
			Expect(w.Body.String()).To(MatchJSON(`{"ids":["mf-1"]}`))
			Expect(maintenance.deleteMediaFilesCalls).To(Equal(1))
			Expect(maintenance.lastDeletedIDs).To(Equal([]string{"mf-1"}))
		})

		It("triggers a staging sweep after a successful delete", func() {
			w := doReq("DELETE", "/song/mf-1", adminToken)

			Expect(w.Code).To(Equal(http.StatusOK))
			Eventually(upgrader.sweepStagingCalls.Load).Should(Equal(int32(1)))
		})

		It("returns 200 for an unknown id (idempotent, maintenance semantics)", func() {
			// The fake mirrors core.Maintenance's contract: ids that don't
			// exist are skipped silently, so the handler still reports success.
			w := doReq("DELETE", "/song/does-not-exist", adminToken)

			Expect(w.Code).To(Equal(http.StatusOK))
			Expect(w.Body.String()).To(MatchJSON(`{"ids":["does-not-exist"]}`))
		})

		It("returns 409 with the blocked-tracks message when an upgrade is in progress", func() {
			maintenance.deleteMediaFilesErr = fmt.Errorf("%w: Track A (mf-1)", core.ErrUpgradeInProgress)

			w := doReq("DELETE", "/song/mf-1", adminToken)

			Expect(w.Code).To(Equal(http.StatusConflict))
			var body map[string]string
			Expect(json.Unmarshal(w.Body.Bytes(), &body)).To(Succeed())
			Expect(body["error"]).To(ContainSubstring("Track A (mf-1)"))
			Expect(body["error"]).To(ContainSubstring(core.ErrUpgradeInProgress.Error()))
		})

		It("returns 500 with the aggregate message for other errors", func() {
			maintenance.deleteMediaFilesErr = errors.New("deleted 1/2 files, 1 failed: Track B (mf-2): permission denied")

			w := doReq("DELETE", "/song/mf-1", adminToken)

			Expect(w.Code).To(Equal(http.StatusInternalServerError))
			var body map[string]string
			Expect(json.Unmarshal(w.Body.Bytes(), &body)).To(Succeed())
			Expect(body["error"]).To(ContainSubstring("1 failed"))
		})

		It("does not sweep staging when the delete fails", func() {
			maintenance.deleteMediaFilesErr = fmt.Errorf("%w: Track A (mf-1)", core.ErrUpgradeInProgress)

			w := doReq("DELETE", "/song/mf-1", adminToken)

			Expect(w.Code).To(Equal(http.StatusConflict))
			Consistently(upgrader.sweepStagingCalls.Load, "100ms", "20ms").Should(BeZero())
		})

		It("does not panic when the upgrader is nil", func() {
			nativeRouter := New(ds, nil, nil, nil, tests.NewMockLibraryService(), tests.NewMockUserService(), nil, nil, maintenance, nil, nil)
			nilUpgraderRouter := server.JWTVerifier(nativeRouter)

			req := createAuthenticatedRequest("DELETE", "/song/mf-1", nil, adminToken)
			w := httptest.NewRecorder()
			nilUpgraderRouter.ServeHTTP(w, req)

			Expect(w.Code).To(Equal(http.StatusOK))
			Expect(w.Body.String()).To(MatchJSON(`{"ids":["mf-1"]}`))
		})
	})

	Describe("DELETE /song (batch via query params)", func() {
		It("deletes all the requested ids", func() {
			w := doReq("DELETE", "/song?id=mf-1&id=mf-2&id=mf-3", adminToken)

			Expect(w.Code).To(Equal(http.StatusOK))
			Expect(w.Body.String()).To(MatchJSON(`{"ids":["mf-1","mf-2","mf-3"]}`))
			Expect(maintenance.deleteMediaFilesCalls).To(Equal(1))
			Expect(maintenance.lastDeletedIDs).To(Equal([]string{"mf-1", "mf-2", "mf-3"}))
			Eventually(upgrader.sweepStagingCalls.Load).Should(Equal(int32(1)))
		})

		It("returns 400 when no ids are given", func() {
			w := doReq("DELETE", "/song", adminToken)

			Expect(w.Code).To(Equal(http.StatusBadRequest))
			Expect(maintenance.deleteMediaFilesCalls).To(BeZero())
			Consistently(upgrader.sweepStagingCalls.Load, "100ms", "20ms").Should(BeZero())
		})

		It("returns 400 when the only ids given are empty strings", func() {
			w := doReq("DELETE", "/song?id=&id=", adminToken)

			Expect(w.Code).To(Equal(http.StatusBadRequest))
			Expect(maintenance.deleteMediaFilesCalls).To(BeZero())
		})

		It("fails the whole batch with 409 when any track has an upgrade in progress", func() {
			maintenance.deleteMediaFilesErr = fmt.Errorf("%w: Track B (mf-2)", core.ErrUpgradeInProgress)

			w := doReq("DELETE", "/song?id=mf-1&id=mf-2", adminToken)

			Expect(w.Code).To(Equal(http.StatusConflict))
			var body map[string]string
			Expect(json.Unmarshal(w.Body.Bytes(), &body)).To(Succeed())
			Expect(body["error"]).To(ContainSubstring("Track B (mf-2)"))
		})
	})

	Describe("DELETE /album/{id}", func() {
		It("deletes the album and returns its id", func() {
			w := doReq("DELETE", "/album/al-1", adminToken)

			Expect(w.Code).To(Equal(http.StatusOK))
			Expect(w.Body.String()).To(MatchJSON(`{"ids":["al-1"]}`))
			Expect(maintenance.deleteAlbumCalls).To(Equal(1))
			Expect(maintenance.lastAlbumID).To(Equal("al-1"))
			Eventually(upgrader.sweepStagingCalls.Load).Should(Equal(int32(1)))
		})

		It("returns 409 when a track of the album has an upgrade in progress", func() {
			maintenance.deleteAlbumErr = fmt.Errorf("%w: Track A (mf-1)", core.ErrUpgradeInProgress)

			w := doReq("DELETE", "/album/al-1", adminToken)

			Expect(w.Code).To(Equal(http.StatusConflict))
			var body map[string]string
			Expect(json.Unmarshal(w.Body.Bytes(), &body)).To(Succeed())
			Expect(body["error"]).To(ContainSubstring("Track A (mf-1)"))
			Consistently(upgrader.sweepStagingCalls.Load, "100ms", "20ms").Should(BeZero())
		})

		It("returns 500 for other errors", func() {
			maintenance.deleteAlbumErr = errors.New("boom")

			w := doReq("DELETE", "/album/al-1", adminToken)

			Expect(w.Code).To(Equal(http.StatusInternalServerError))
		})
	})

	Describe("coexistence with the /song and /album resource mounts", func() {
		// The DELETE routes are siblings of the r.Route("/song"|"/album")
		// mounts registered by api.R — guard that adding them does not shadow
		// the mounted read-only endpoints (see "Adjustments" in
		// plans/02-admin-delete.md for the chi v5.3.0 probe this replicates).
		BeforeEach(func() {
			mediaFileRepo.SetData(model.MediaFiles{{ID: "mf-1", Title: "Track A"}})
		})

		It("still serves GET /song/{id} from the resource repository", func() {
			w := doReq("GET", "/song/mf-1", regularToken)

			Expect(w.Code).To(Equal(http.StatusOK))
			var mf model.MediaFile
			Expect(json.Unmarshal(w.Body.Bytes(), &mf)).To(Succeed())
			Expect(mf.ID).To(Equal("mf-1"))
			Expect(maintenance.deleteMediaFilesCalls).To(BeZero())
		})

		It("still serves GET /song from the resource repository", func() {
			w := doReq("GET", "/song", regularToken)

			Expect(w.Code).To(Equal(http.StatusOK))
		})
	})
})
