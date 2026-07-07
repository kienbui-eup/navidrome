package nativeapi

import (
	"bytes"
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"time"

	"github.com/navidrome/navidrome/conf"
	"github.com/navidrome/navidrome/conf/configtest"
	"github.com/navidrome/navidrome/consts"
	"github.com/navidrome/navidrome/core/auth"
	"github.com/navidrome/navidrome/core/playlists"
	"github.com/navidrome/navidrome/model"
	"github.com/navidrome/navidrome/model/request"
	"github.com/navidrome/navidrome/server"
	"github.com/navidrome/navidrome/tests"
	. "github.com/onsi/ginkgo/v2"
	. "github.com/onsi/gomega"
)

// templateMockPlaylists is a minimal playlists.Playlists double that only
// overrides the two template-related methods exercised by these tests. It
// embeds the interface (like mockPlaylistsService in playlists_test.go) so it
// automatically satisfies any other methods added to the interface later.
type templateMockPlaylists struct {
	playlists.Playlists
	templates []playlists.Template
	createFn  func(ctx context.Context, templateID, name string) (*model.Playlist, error)
}

func (m *templateMockPlaylists) ListTemplates() []playlists.Template {
	return m.templates
}

func (m *templateMockPlaylists) CreateFromTemplate(ctx context.Context, templateID, name string) (*model.Playlist, error) {
	return m.createFn(ctx, templateID, name)
}

var _ = Describe("Playlist Template Endpoints", func() {
	var svc *templateMockPlaylists
	var w *httptest.ResponseRecorder

	BeforeEach(func() {
		svc = &templateMockPlaylists{templates: playlists.Templates}
		w = httptest.NewRecorder()
	})

	Describe("listPlaylistTemplates", func() {
		It("returns the catalog as {id, name, description} only (no criteria/rules leaked)", func() {
			handler := listPlaylistTemplates(svc)
			req := httptest.NewRequest("GET", "/playlist/template", nil)
			handler.ServeHTTP(w, req)

			Expect(w.Code).To(Equal(http.StatusOK))
			var resp []map[string]any
			err := json.Unmarshal(w.Body.Bytes(), &resp)
			Expect(err).ToNot(HaveOccurred())
			Expect(resp).To(HaveLen(len(playlists.Templates)))
			for _, entry := range resp {
				Expect(entry).To(HaveKey("id"))
				Expect(entry).To(HaveKey("name"))
				Expect(entry).To(HaveKey("description"))
				Expect(entry).ToNot(HaveKey("criteria"))
				Expect(entry).ToNot(HaveKey("rules"))
			}
			Expect(resp[0]["id"]).To(Equal(playlists.TemplateHeavyRotation))
		})
	})

	Describe("createPlaylistFromTemplate", func() {
		It("returns 400 when the body is not valid JSON", func() {
			handler := createPlaylistFromTemplate(svc)
			req := httptest.NewRequest("POST", "/playlist/template", bytes.NewBufferString("not json"))
			handler.ServeHTTP(w, req)
			Expect(w.Code).To(Equal(http.StatusBadRequest))
		})

		It("returns 400 when templateId is missing", func() {
			handler := createPlaylistFromTemplate(svc)
			req := httptest.NewRequest("POST", "/playlist/template", bytes.NewBufferString(`{}`))
			handler.ServeHTTP(w, req)
			Expect(w.Code).To(Equal(http.StatusBadRequest))
		})

		It("returns 404 when the template does not exist", func() {
			svc.createFn = func(_ context.Context, _, _ string) (*model.Playlist, error) {
				return nil, playlists.ErrTemplateNotFound
			}
			handler := createPlaylistFromTemplate(svc)
			req := httptest.NewRequest("POST", "/playlist/template", bytes.NewBufferString(`{"templateId":"nope"}`))
			handler.ServeHTTP(w, req)
			Expect(w.Code).To(Equal(http.StatusNotFound))
		})

		It("returns 500 when the service fails unexpectedly", func() {
			svc.createFn = func(_ context.Context, _, _ string) (*model.Playlist, error) {
				return nil, context.DeadlineExceeded
			}
			handler := createPlaylistFromTemplate(svc)
			req := httptest.NewRequest("POST", "/playlist/template", bytes.NewBufferString(`{"templateId":"heavy_rotation"}`))
			handler.ServeHTTP(w, req)
			Expect(w.Code).To(Equal(http.StatusInternalServerError))
		})

		It("returns 201 and the created playlist on success", func() {
			var gotTemplateID, gotName string
			svc.createFn = func(_ context.Context, templateID, name string) (*model.Playlist, error) {
				gotTemplateID, gotName = templateID, name
				return &model.Playlist{ID: "pls-1", Name: "My Playlist", OwnerID: "user-1"}, nil
			}
			handler := createPlaylistFromTemplate(svc)
			req := httptest.NewRequest("POST", "/playlist/template", bytes.NewBufferString(`{"templateId":"heavy_rotation","name":"My Playlist"}`))
			handler.ServeHTTP(w, req)

			Expect(w.Code).To(Equal(http.StatusCreated))
			Expect(gotTemplateID).To(Equal("heavy_rotation"))
			Expect(gotName).To(Equal("My Playlist"))

			var resp model.Playlist
			err := json.Unmarshal(w.Body.Bytes(), &resp)
			Expect(err).ToNot(HaveOccurred())
			Expect(resp.ID).To(Equal("pls-1"))
			Expect(resp.OwnerID).To(Equal("user-1"))
		})
	})
})

// Full-router integration test proving the routes are wired into
// addPlaylistRoute and are reachable by a non-admin authenticated user (the
// endpoints must NOT be admin-only, per Phase 1 of the plan).
var _ = Describe("Playlist Template Routes (full router)", func() {
	var router http.Handler
	var svc *templateMockPlaylists
	var userRepo *tests.MockedUserRepo

	BeforeEach(func() {
		DeferCleanup(configtest.SetupConfig())
		conf.Server.SessionTimeout = time.Minute

		svc = &templateMockPlaylists{templates: playlists.Templates}
		userRepo = tests.CreateMockUserRepo()

		ds := &tests.MockDataStore{
			MockedUser:     userRepo,
			MockedProperty: &tests.MockedPropertyRepo{},
		}
		auth.Init(ds)

		testUser := model.User{
			ID:          "user-1",
			UserName:    "testuser",
			Name:        "Test User",
			IsAdmin:     false,
			NewPassword: "testpass",
		}
		Expect(userRepo.Put(&testUser)).To(Succeed())

		nativeRouter := New(ds, nil, svc, nil, tests.NewMockLibraryService(), tests.NewMockUserService(), nil, nil, nil, nil, nil)
		router = server.JWTVerifier(nativeRouter)
	})

	authenticatedRequest := func(method, path string, body []byte) *http.Request {
		var req *http.Request
		if body != nil {
			req = httptest.NewRequest(method, path, bytes.NewReader(body))
		} else {
			req = httptest.NewRequest(method, path, nil)
		}
		testUser := model.User{ID: "user-1", UserName: "testuser"}
		token, err := auth.CreateToken(&testUser)
		Expect(err).ToNot(HaveOccurred())
		req.Header.Set(consts.UIAuthorizationHeader, "Bearer "+token)
		return req
	}

	It("allows a non-admin authenticated user to list templates", func() {
		req := authenticatedRequest("GET", "/playlist/template", nil)
		w := httptest.NewRecorder()
		router.ServeHTTP(w, req)
		Expect(w.Code).To(Equal(http.StatusOK))
	})

	It("allows a non-admin authenticated user to create a playlist from a template", func() {
		svc.createFn = func(ctx context.Context, templateID, name string) (*model.Playlist, error) {
			usr, _ := request.UserFrom(ctx)
			return &model.Playlist{ID: "pls-1", Name: "x", OwnerID: usr.ID}, nil
		}
		req := authenticatedRequest("POST", "/playlist/template", []byte(`{"templateId":"heavy_rotation"}`))
		w := httptest.NewRecorder()
		router.ServeHTTP(w, req)
		Expect(w.Code).To(Equal(http.StatusCreated))

		var resp model.Playlist
		Expect(json.Unmarshal(w.Body.Bytes(), &resp)).To(Succeed())
		Expect(resp.OwnerID).To(Equal("user-1"))
	})
})
