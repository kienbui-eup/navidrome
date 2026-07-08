package server

import (
	"io/fs"
	"net/http"
	"net/http/httptest"
	"strings"

	"github.com/navidrome/navidrome/conf"
	"github.com/navidrome/navidrome/conf/configtest"
	"github.com/navidrome/navidrome/consts"
	"github.com/navidrome/navidrome/model"
	"github.com/navidrome/navidrome/player"
	"github.com/navidrome/navidrome/tests"
	. "github.com/onsi/ginkgo/v2"
	. "github.com/onsi/gomega"
)

// firstEmbeddedPlayerAsset returns the path (relative to the embedded FS root,
// e.g. "assets/index-XXXX.js") of a real file shipped in player/dist/assets, so
// tests never need to hardcode a content hash that changes on every rebuild.
func firstEmbeddedPlayerAsset() string {
	assets := player.BuildAssets()
	entries, err := fs.ReadDir(assets, "assets")
	Expect(err).ToNot(HaveOccurred())
	Expect(entries).ToNot(BeEmpty())
	for _, e := range entries {
		if !e.IsDir() {
			return "assets/" + e.Name()
		}
	}
	Fail("no file found under embedded player assets/ directory")
	return ""
}

var _ = Describe("Player UI (/play)", func() {
	var ds model.DataStore

	BeforeEach(func() {
		ds = &tests.MockDataStore{}
		DeferCleanup(configtest.SetupConfig())
	})

	// newMountedServer builds a Server exactly like Run() does for the WebUI and
	// Player UI mounts, without opening a real TCP listener, so requests can be
	// dispatched directly against s.router.
	newMountedServer := func() *Server {
		s := New(ds, nil, nil)
		s.MountRouter("WebUI", consts.URLPathUI, s.frontendAssetsHandler())
		s.MountRouter("Player UI", consts.URLPathPlayer, s.playerAssetsHandler())
		return s
	}

	Describe("static assets", func() {
		It("serves index.html at /play/ with a no-cache header and the Vietnamese title", func() {
			s := newMountedServer()
			req := httptest.NewRequest(http.MethodGet, "/play/", nil)
			w := httptest.NewRecorder()
			s.router.ServeHTTP(w, req)

			Expect(w.Code).To(Equal(http.StatusOK))
			Expect(w.Header().Get("Content-Type")).To(ContainSubstring("text/html"))
			Expect(w.Header().Get("Cache-Control")).To(Equal("no-cache"))
			Expect(w.Body.String()).To(ContainSubstring("Trợ lý nhạc"))
		})

		It("serves /play/env-config.js as same-origin, Navidrome-typed JS", func() {
			s := newMountedServer()
			req := httptest.NewRequest(http.MethodGet, "/play/env-config.js", nil)
			w := httptest.NewRecorder()
			s.router.ServeHTTP(w, req)

			Expect(w.Code).To(Equal(http.StatusOK))
			Expect(w.Header().Get("Content-Type")).To(Equal("application/javascript"))
			Expect(w.Header().Get("Cache-Control")).To(Equal("no-cache"))
			body := w.Body.String()
			Expect(body).To(ContainSubstring(`SERVER_TYPE="navidrome"`))
			Expect(body).To(ContainSubstring(`HIDE_SERVER=true`))
			Expect(body).To(ContainSubstring(`SERVER_URL="";`))
		})

		It("serves a real embedded asset file under /play/assets", func() {
			s := newMountedServer()
			assetPath := firstEmbeddedPlayerAsset()

			req := httptest.NewRequest(http.MethodGet, "/play/"+assetPath, nil)
			w := httptest.NewRecorder()
			s.router.ServeHTTP(w, req)

			Expect(w.Code).To(Equal(http.StatusOK))
		})
	})

	Describe("root redirector", func() {
		It("redirects / to /app/ by default", func() {
			s := newMountedServer()
			req := httptest.NewRequest(http.MethodGet, "/", nil)
			w := httptest.NewRecorder()
			s.router.ServeHTTP(w, req)

			Expect(w.Code).To(Equal(http.StatusFound))
			Expect(w.Header().Get("Location")).To(Equal("/app/"))
		})

		It("redirects / to /play/ when DefaultUIPath is set to the player", func() {
			conf.Server.DefaultUIPath = consts.URLPathPlayer
			s := newMountedServer()
			req := httptest.NewRequest(http.MethodGet, "/", nil)
			w := httptest.NewRecorder()
			s.router.ServeHTTP(w, req)

			Expect(w.Code).To(Equal(http.StatusFound))
			Expect(w.Header().Get("Location")).To(Equal("/play/"))
		})
	})

	Describe("BasePath", func() {
		It("mounts /play under the configured BasePath without double-prefixing", func() {
			conf.Server.BasePath = "/music"
			s := newMountedServer()

			req := httptest.NewRequest(http.MethodGet, "/music/play/", nil)
			w := httptest.NewRecorder()
			s.router.ServeHTTP(w, req)
			Expect(w.Code).To(Equal(http.StatusOK))
			Expect(w.Body.String()).To(ContainSubstring("Trợ lý nhạc"))

			// The unprefixed path must not resolve to the player.
			reqNoPrefix := httptest.NewRequest(http.MethodGet, "/play/", nil)
			wNoPrefix := httptest.NewRecorder()
			s.router.ServeHTTP(wNoPrefix, reqNoPrefix)
			Expect(wNoPrefix.Code).ToNot(Equal(http.StatusOK))
		})

		It("redirects / to the BasePath-prefixed default UI", func() {
			conf.Server.BasePath = "/music"
			conf.Server.DefaultUIPath = consts.URLPathPlayer
			s := newMountedServer()

			req := httptest.NewRequest(http.MethodGet, "/music/", nil)
			w := httptest.NewRecorder()
			s.router.ServeHTTP(w, req)

			Expect(w.Code).To(Equal(http.StatusFound))
			Expect(w.Header().Get("Location")).To(Equal("/music/play/"))
			Expect(strings.HasPrefix(w.Header().Get("Location"), "/music")).To(BeTrue())
		})
	})
})
