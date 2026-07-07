package nativeapi

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"sync/atomic"
	"time"

	"github.com/Masterminds/squirrel"
	"github.com/vi2play/vi2play/conf/configtest"
	"github.com/vi2play/vi2play/core"
	"github.com/vi2play/vi2play/core/auth"
	"github.com/vi2play/vi2play/model"
	"github.com/vi2play/vi2play/server"
	"github.com/vi2play/vi2play/tests"
	. "github.com/onsi/ginkgo/v2"
	. "github.com/onsi/gomega"
)

// fakeUpgrader is a stub core.Upgrader recording the arguments of every call,
// with injectable return values (same role as the stubImporter used by the
// core upgrader tests).
type fakeUpgrader struct {
	startScanErr error
	lastLibrary  int
	lastMediaIDs []string
	scanCalls    int

	running               bool
	scanned, total, found int
	cancelCalls           int

	approveErr   error
	lastApproved string
	lastForce    bool

	rejectErr    error
	lastRejected string

	batchAccepted []string
	batchErr      error
	lastBatchIDs  []string

	// Incremented from a goroutine by the deletion handlers' fire-and-forget
	// staging sweep and polled with Eventually from the test goroutine (see
	// deletion_test.go) — hence atomic.
	sweepStagingCalls atomic.Int32
}

func (f *fakeUpgrader) StartScan(_ context.Context, libraryID int, mediaFileIDs []string) error {
	f.scanCalls++
	f.lastLibrary = libraryID
	f.lastMediaIDs = mediaFileIDs
	return f.startScanErr
}

func (f *fakeUpgrader) ScanStatus() (bool, int, int, int) {
	return f.running, f.scanned, f.total, f.found
}

func (f *fakeUpgrader) CancelScan() { f.cancelCalls++ }

func (f *fakeUpgrader) Approve(_ context.Context, candidateID string, force bool) error {
	f.lastApproved = candidateID
	f.lastForce = force
	return f.approveErr
}

func (f *fakeUpgrader) Reject(_ context.Context, candidateID string) error {
	f.lastRejected = candidateID
	return f.rejectErr
}

func (f *fakeUpgrader) ApproveBatch(_ context.Context, ids []string) ([]string, error) {
	f.lastBatchIDs = ids
	return f.batchAccepted, f.batchErr
}

func (f *fakeUpgrader) Recover(context.Context) error { return nil }

func (f *fakeUpgrader) SweepStaging(context.Context) { f.sweepStagingCalls.Add(1) }

var _ core.Upgrader = (*fakeUpgrader)(nil)

var _ = Describe("Upgrade API", func() {
	var ds *tests.MockDataStore
	var router http.Handler
	var upgrader *fakeUpgrader
	var candidateRepo *tests.MockUpgradeCandidateRepo
	var mediaFileRepo *tests.MockMediaFileRepo
	var adminToken, regularToken string

	BeforeEach(func() {
		DeferCleanup(configtest.SetupConfig())
		upgrader = &fakeUpgrader{}
		candidateRepo = tests.CreateMockUpgradeCandidateRepo()
		mediaFileRepo = tests.CreateMockMediaFileRepo()
		ds = &tests.MockDataStore{
			MockedUpgradeCandidate: candidateRepo,
			MockedMediaFile:        mediaFileRepo,
		}
		auth.Init(ds)
		nativeRouter := New(ds, nil, nil, nil, tests.NewMockLibraryService(), tests.NewMockUserService(), nil, upgrader, nil, nil, nil)
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

	doReq := func(method, path string, body any, token string) *httptest.ResponseRecorder {
		var buf *bytes.Buffer
		if body != nil {
			b, err := json.Marshal(body)
			Expect(err).ToNot(HaveOccurred())
			buf = bytes.NewBuffer(b)
		}
		req := createAuthenticatedRequest(method, path, buf, token)
		w := httptest.NewRecorder()
		router.ServeHTTP(w, req)
		return w
	}

	Describe("admin-only access", func() {
		DescribeTable("returns 403 for non-admin users",
			func(method, path string) {
				w := doReq(method, path, map[string]any{}, regularToken)
				Expect(w.Code).To(Equal(http.StatusForbidden))
			},
			Entry("scan", "POST", "/upgrade/scan"),
			Entry("scan cancel", "POST", "/upgrade/scan/cancel"),
			Entry("status", "GET", "/upgrade/status"),
			Entry("candidates", "GET", "/upgrade/candidates"),
			Entry("approve", "POST", "/upgrade/candidates/c-1/approve"),
			Entry("reject", "POST", "/upgrade/candidates/c-1/reject"),
			Entry("approve-batch", "POST", "/upgrade/candidates/approve-batch"),
		)

		It("returns 401 for unauthenticated requests", func() {
			req := createUnauthenticatedRequest("GET", "/upgrade/status", nil)
			w := httptest.NewRecorder()
			router.ServeHTTP(w, req)
			Expect(w.Code).To(Equal(http.StatusUnauthorized))
		})
	})

	Describe("POST /upgrade/scan", func() {
		It("starts a scan and returns 202", func() {
			w := doReq("POST", "/upgrade/scan", map[string]any{"libraryId": 3, "mediaFileIds": []string{"mf-1"}}, adminToken)

			Expect(w.Code).To(Equal(http.StatusAccepted))
			Expect(w.Body.String()).To(MatchJSON(`{"status":"started"}`))
			Expect(upgrader.scanCalls).To(Equal(1))
			Expect(upgrader.lastLibrary).To(Equal(3))
			Expect(upgrader.lastMediaIDs).To(Equal([]string{"mf-1"}))
		})

		It("accepts an empty body (scan everything)", func() {
			w := doReq("POST", "/upgrade/scan", nil, adminToken)

			Expect(w.Code).To(Equal(http.StatusAccepted))
			Expect(upgrader.lastLibrary).To(Equal(0))
			Expect(upgrader.lastMediaIDs).To(BeEmpty())
		})

		It("returns 409 when a scan is already in progress", func() {
			upgrader.startScanErr = core.ErrUpgradeScanInProgress

			w := doReq("POST", "/upgrade/scan", map[string]any{}, adminToken)

			Expect(w.Code).To(Equal(http.StatusConflict))
		})

		It("returns 403 when the quality upgrader is disabled (M2 kill switch)", func() {
			upgrader.startScanErr = core.ErrUpgradeDisabled

			w := doReq("POST", "/upgrade/scan", map[string]any{}, adminToken)

			Expect(w.Code).To(Equal(http.StatusForbidden))
		})

		It("returns 400 for a malformed body", func() {
			req := createAuthenticatedRequest("POST", "/upgrade/scan", bytes.NewBufferString("{not json"), adminToken)
			w := httptest.NewRecorder()
			router.ServeHTTP(w, req)

			Expect(w.Code).To(Equal(http.StatusBadRequest))
			Expect(upgrader.scanCalls).To(BeZero())
		})
	})

	Describe("POST /upgrade/scan/cancel", func() {
		It("cancels the running scan", func() {
			w := doReq("POST", "/upgrade/scan/cancel", nil, adminToken)

			Expect(w.Code).To(Equal(http.StatusOK))
			Expect(upgrader.cancelCalls).To(Equal(1))
		})
	})

	Describe("GET /upgrade/status", func() {
		It("reports the current scan progress", func() {
			upgrader.running = true
			upgrader.scanned = 5
			upgrader.total = 10
			upgrader.found = 2

			w := doReq("GET", "/upgrade/status", nil, adminToken)

			Expect(w.Code).To(Equal(http.StatusOK))
			Expect(w.Body.String()).To(MatchJSON(`{"running":true,"scanned":5,"total":10,"found":2}`))
		})
	})

	Describe("GET /upgrade/candidates", func() {
		var created, updated time.Time

		BeforeEach(func() {
			created = time.Date(2026, 7, 6, 10, 0, 0, 0, time.UTC)
			updated = time.Date(2026, 7, 6, 12, 0, 0, 0, time.UTC)
			candidateRepo.Data["c-1"] = &model.UpgradeCandidate{
				ID:          "c-1",
				MediaFileID: "mf-1",
				LibraryID:   1,
				Source:      "archive",
				SourceRef:   "some-item/track.flac",
				Title:       "Track (FLAC)",
				Format:      "flac",
				EstBitRate:  900,
				EstSize:     42000000,
				MatchScore:  95,
				Status:      model.UpgradeCandidateStatusPending,
				VerifyInfo:  `{"actualBitRate":900,"missingTags":[]}`,
				Error:       "",
				ReviewedBy:  "admin-1",
				CreatedAt:   created,
				UpdatedAt:   updated,
			}
			mediaFileRepo.SetData(model.MediaFiles{{
				ID:      "mf-1",
				Title:   "Track",
				Artist:  "Artist",
				Suffix:  "mp3",
				BitRate: 128,
			}})
		})

		It("returns candidates with the embedded current-track info", func() {
			w := doReq("GET", "/upgrade/candidates?status=pending&_start=0&_end=50", nil, adminToken)

			Expect(w.Code).To(Equal(http.StatusOK))
			var list []map[string]any
			Expect(json.Unmarshal(w.Body.Bytes(), &list)).To(Succeed())
			Expect(list).To(HaveLen(1))
			c := list[0]
			Expect(c["id"]).To(Equal("c-1"))
			Expect(c["mediaFileId"]).To(Equal("mf-1"))
			Expect(c["libraryId"]).To(BeNumerically("==", 1))
			Expect(c["source"]).To(Equal("archive"))
			Expect(c["sourceRef"]).To(Equal("some-item/track.flac"))
			Expect(c["title"]).To(Equal("Track (FLAC)"))
			Expect(c["format"]).To(Equal("flac"))
			Expect(c["estBitRate"]).To(BeNumerically("==", 900))
			Expect(c["estSize"]).To(BeNumerically("==", 42000000))
			Expect(c["matchScore"]).To(BeNumerically("==", 95))
			Expect(c["status"]).To(Equal("pending"))
			// verifyInfo is passed through as the raw stored JSON string
			Expect(c["verifyInfo"]).To(Equal(`{"actualBitRate":900,"missingTags":[]}`))
			Expect(c["error"]).To(Equal(""))
			Expect(c["reviewedBy"]).To(Equal("admin-1"))
			Expect(c["createdAt"]).To(Equal(created.Format(time.RFC3339)))
			Expect(c["updatedAt"]).To(Equal(updated.Format(time.RFC3339)))
			Expect(c["currentTitle"]).To(Equal("Track"))
			Expect(c["currentArtist"]).To(Equal("Artist"))
			Expect(c["currentFormat"]).To(Equal("mp3"))
			Expect(c["currentBitRate"]).To(BeNumerically("==", 128))
		})

		It("falls back to empty current-track info when the media file is gone", func() {
			mediaFileRepo.SetData(model.MediaFiles{}) // media file deleted

			w := doReq("GET", "/upgrade/candidates?status=pending", nil, adminToken)

			Expect(w.Code).To(Equal(http.StatusOK))
			var list []map[string]any
			Expect(json.Unmarshal(w.Body.Bytes(), &list)).To(Succeed())
			Expect(list).To(HaveLen(1))
			Expect(list[0]["currentTitle"]).To(Equal(""))
			Expect(list[0]["currentArtist"]).To(Equal(""))
			Expect(list[0]["currentFormat"]).To(Equal(""))
			Expect(list[0]["currentBitRate"]).To(BeNumerically("==", 0))
		})

		It("maps _start/_end to offset/limit and sorts by updatedAt desc", func() {
			w := doReq("GET", "/upgrade/candidates?status=pending&_start=50&_end=100", nil, adminToken)

			Expect(w.Code).To(Equal(http.StatusOK))
			Expect(candidateRepo.Options.Offset).To(Equal(50))
			Expect(candidateRepo.Options.Max).To(Equal(50))
			Expect(candidateRepo.Options.Sort).To(Equal("updated_at"))
			Expect(candidateRepo.Options.Order).To(Equal("desc"))
			Expect(candidateRepo.Options.Filters).To(Equal(squirrel.Eq{"status": "pending"}))
		})

		It("returns an empty JSON array (not null) when there are no candidates", func() {
			delete(candidateRepo.Data, "c-1")

			w := doReq("GET", "/upgrade/candidates?status=replaced", nil, adminToken)

			Expect(w.Code).To(Equal(http.StatusOK))
			Expect(w.Body.String()).To(MatchJSON(`[]`))
		})
	})

	Describe("POST /upgrade/candidates/{id}/approve", func() {
		It("approves a candidate", func() {
			w := doReq("POST", "/upgrade/candidates/c-1/approve", map[string]any{"force": false}, adminToken)

			Expect(w.Code).To(Equal(http.StatusOK))
			Expect(w.Body.String()).To(MatchJSON(`{"status":"ok"}`))
			Expect(upgrader.lastApproved).To(Equal("c-1"))
			Expect(upgrader.lastForce).To(BeFalse())
		})

		It("passes force through", func() {
			w := doReq("POST", "/upgrade/candidates/c-2/approve", map[string]any{"force": true}, adminToken)

			Expect(w.Code).To(Equal(http.StatusOK))
			Expect(upgrader.lastApproved).To(Equal("c-2"))
			Expect(upgrader.lastForce).To(BeTrue())
		})

		It("returns 404 when the candidate does not exist", func() {
			upgrader.approveErr = model.ErrNotFound

			w := doReq("POST", "/upgrade/candidates/nope/approve", map[string]any{}, adminToken)

			Expect(w.Code).To(Equal(http.StatusNotFound))
		})

		It("returns 409 when the candidate status does not allow approval (wrapped error)", func() {
			upgrader.approveErr = fmt.Errorf("%w: status is %q", core.ErrUpgradeInvalidStatus, "replaced")

			w := doReq("POST", "/upgrade/candidates/c-1/approve", map[string]any{}, adminToken)

			Expect(w.Code).To(Equal(http.StatusConflict))
		})
	})

	Describe("POST /upgrade/candidates/{id}/reject", func() {
		It("rejects a candidate", func() {
			w := doReq("POST", "/upgrade/candidates/c-1/reject", nil, adminToken)

			Expect(w.Code).To(Equal(http.StatusOK))
			Expect(w.Body.String()).To(MatchJSON(`{"status":"ok"}`))
			Expect(upgrader.lastRejected).To(Equal("c-1"))
		})

		It("returns 404 when the candidate does not exist", func() {
			upgrader.rejectErr = model.ErrNotFound

			w := doReq("POST", "/upgrade/candidates/nope/reject", nil, adminToken)

			Expect(w.Code).To(Equal(http.StatusNotFound))
		})

		It("returns 409 when the candidate status does not allow rejection", func() {
			upgrader.rejectErr = fmt.Errorf("%w: status is %q", core.ErrUpgradeInvalidStatus, "replaced")

			w := doReq("POST", "/upgrade/candidates/c-1/reject", nil, adminToken)

			Expect(w.Code).To(Equal(http.StatusConflict))
		})
	})

	Describe("POST /upgrade/candidates/approve-batch", func() {
		It("approves the batch and returns the accepted ids", func() {
			upgrader.batchAccepted = []string{"c-1", "c-2"}

			w := doReq("POST", "/upgrade/candidates/approve-batch", map[string]any{"ids": []string{"c-1", "c-2", "c-3"}}, adminToken)

			Expect(w.Code).To(Equal(http.StatusOK))
			Expect(w.Body.String()).To(MatchJSON(`{"accepted":["c-1","c-2"]}`))
			Expect(upgrader.lastBatchIDs).To(Equal([]string{"c-1", "c-2", "c-3"}))
		})

		It("still returns 200 with the accepted ids when some candidates fail", func() {
			upgrader.batchAccepted = []string{"c-1"}
			upgrader.batchErr = fmt.Errorf("c-2: boom")

			w := doReq("POST", "/upgrade/candidates/approve-batch", map[string]any{"ids": []string{"c-1", "c-2"}}, adminToken)

			Expect(w.Code).To(Equal(http.StatusOK))
			Expect(w.Body.String()).To(MatchJSON(`{"accepted":["c-1"]}`))
		})

		It("returns an empty accepted list (not null) when nothing was approved", func() {
			upgrader.batchAccepted = nil

			w := doReq("POST", "/upgrade/candidates/approve-batch", map[string]any{"ids": []string{}}, adminToken)

			Expect(w.Code).To(Equal(http.StatusOK))
			Expect(w.Body.String()).To(MatchJSON(`{"accepted":[]}`))
		})

		It("returns 400 for a missing body", func() {
			w := doReq("POST", "/upgrade/candidates/approve-batch", nil, adminToken)

			Expect(w.Code).To(Equal(http.StatusBadRequest))
		})
	})
})
