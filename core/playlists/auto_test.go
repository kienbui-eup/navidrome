package playlists_test

import (
	"context"

	"github.com/vi2play/vi2play/conf"
	"github.com/vi2play/vi2play/conf/configtest"
	"github.com/vi2play/vi2play/core"
	"github.com/vi2play/vi2play/core/playlists"
	"github.com/vi2play/vi2play/model"
	"github.com/vi2play/vi2play/tests"
	. "github.com/onsi/ginkgo/v2"
	. "github.com/onsi/gomega"
)

var _ = Describe("RunAutoPlaylists", func() {
	var ds *tests.MockDataStore
	var mockPlsRepo *tests.MockPlaylistRepo
	var mockUserRepo *tests.MockedUserRepo
	var ps playlists.Playlists
	ctx := context.Background()

	BeforeEach(func() {
		DeferCleanup(configtest.SetupConfig())

		mockPlsRepo = tests.CreateMockPlaylistRepo()
		mockUserRepo = tests.CreateMockUserRepo()
		ds = &tests.MockDataStore{MockedPlaylist: mockPlsRepo, MockedUser: mockUserRepo}
		ps = playlists.NewPlaylists(ds, core.NewImageUploadService())

		mockUserRepo.Data["alice"] = &model.User{ID: "user-alice", UserName: "alice"}
		mockUserRepo.Data["bob"] = &model.User{ID: "user-bob", UserName: "bob"}

		conf.Server.AutoPlaylists.Templates = []string{playlists.TemplateHeavyRotation, playlists.TemplateRediscover}
	})

	// commentsByOwner groups the Comment marker of every playlist currently in the
	// mock repo by OwnerID, so tests can assert per-user results without caring
	// about generated IDs.
	commentsByOwner := func() map[string][]string {
		result := map[string][]string{}
		for _, p := range mockPlsRepo.Data {
			result[p.OwnerID] = append(result[p.OwnerID], p.Comment)
		}
		return result
	}

	It("creates every configured template for every user, marked with the auto: comment convention", func() {
		Expect(ps.RunAutoPlaylists(ctx)).To(Succeed())

		Expect(mockPlsRepo.Data).To(HaveLen(4)) // 2 users x 2 templates
		byOwner := commentsByOwner()
		Expect(byOwner["user-alice"]).To(ConsistOf("auto:heavy_rotation", "auto:rediscover"))
		Expect(byOwner["user-bob"]).To(ConsistOf("auto:heavy_rotation", "auto:rediscover"))
	})

	It("creates playlists owned by the respective user, not the job runner", func() {
		Expect(ps.RunAutoPlaylists(ctx)).To(Succeed())

		for _, p := range mockPlsRepo.Data {
			Expect(p.OwnerID).To(BeElementOf("user-alice", "user-bob"))
			Expect(p.IsSmartPlaylist()).To(BeTrue())
		}
	})

	It("is idempotent: running it a second time creates nothing new", func() {
		Expect(ps.RunAutoPlaylists(ctx)).To(Succeed())
		Expect(mockPlsRepo.Data).To(HaveLen(4))

		Expect(ps.RunAutoPlaylists(ctx)).To(Succeed())
		Expect(mockPlsRepo.Data).To(HaveLen(4))
	})

	It("respects the configured Templates list", func() {
		conf.Server.AutoPlaylists.Templates = []string{playlists.TemplateRecentlyAdded}

		Expect(ps.RunAutoPlaylists(ctx)).To(Succeed())

		Expect(mockPlsRepo.Data).To(HaveLen(2)) // 2 users x 1 template
		for _, p := range mockPlsRepo.Data {
			Expect(p.Comment).To(Equal("auto:recently_added"))
		}
	})

	It("skips a user that already has the auto-playlist for a template", func() {
		mockPlsRepo.Data["existing"] = &model.Playlist{
			ID:      "existing",
			OwnerID: "user-alice",
			Comment: "auto:heavy_rotation",
		}
		conf.Server.AutoPlaylists.Templates = []string{playlists.TemplateHeavyRotation}

		Expect(ps.RunAutoPlaylists(ctx)).To(Succeed())

		// Alice already had one (untouched, no duplicate); Bob gets a new one.
		Expect(mockPlsRepo.Data).To(HaveLen(2))
		byOwner := commentsByOwner()
		Expect(byOwner["user-alice"]).To(ConsistOf("auto:heavy_rotation"))
		Expect(byOwner["user-bob"]).To(ConsistOf("auto:heavy_rotation"))
	})

	It("logs and skips an unknown template ID in config, instead of crashing", func() {
		conf.Server.AutoPlaylists.Templates = []string{"does-not-exist", playlists.TemplateHeavyRotation}

		err := ps.RunAutoPlaylists(ctx)

		Expect(err).ToNot(HaveOccurred())
		Expect(mockPlsRepo.Data).To(HaveLen(2)) // only the known template, for both users
		for _, p := range mockPlsRepo.Data {
			Expect(p.Comment).To(Equal("auto:heavy_rotation"))
		}
	})

	It("does not touch a manually-created playlist from the same template (no auto: comment)", func() {
		mockPlsRepo.Data["manual"] = &model.Playlist{
			ID:      "manual",
			OwnerID: "user-alice",
			Comment: "", // created via POST /playlist/template, not the auto job
		}

		Expect(ps.RunAutoPlaylists(ctx)).To(Succeed())

		byOwner := commentsByOwner()
		// Alice's manual playlist plus both auto templates now created for her.
		Expect(byOwner["user-alice"]).To(ConsistOf("", "auto:heavy_rotation", "auto:rediscover"))
	})
})

var _ = Describe("AutoPlaylists config defaults", func() {
	It("references only known template IDs", func() {
		// Mirrors the default set in conf/configuration.go's setViperDefaults
		// ("autoplaylists.templates"). Checked here, rather than by importing
		// core/playlists from the conf package, to avoid a conf -> core/playlists
		// import cycle (core/playlists already imports conf).
		defaults := []string{"heavy_rotation", "rediscover", "recently_added"}
		for _, id := range defaults {
			_, ok := playlists.FindTemplate(id)
			Expect(ok).To(BeTrue(), id)
		}
	})
})
