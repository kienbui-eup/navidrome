package playlists_test

import (
	"context"
	"encoding/json"

	"github.com/navidrome/navidrome/core"
	"github.com/navidrome/navidrome/core/playlists"
	"github.com/navidrome/navidrome/model"
	"github.com/navidrome/navidrome/model/criteria"
	"github.com/navidrome/navidrome/model/request"
	"github.com/navidrome/navidrome/tests"
	. "github.com/onsi/ginkgo/v2"
	. "github.com/onsi/gomega"
)

// allTemplateIDs mirrors the catalog documented in Phase 1 of
// plans/01-library-admin-auto-playlists.md.
var allTemplateIDs = []string{
	playlists.TemplateHeavyRotation,
	playlists.TemplateOnRepeat,
	playlists.TemplateRediscover,
	playlists.TemplateRecentlyAdded,
	playlists.TemplateFavoritesMix,
	playlists.TemplateTopRated,
}

// fieldNamesUsedIn walks a criteria expression tree and collects every field
// name referenced, using the same exported Walk/Fields helpers the
// persistence layer uses to compute required SQL joins (see
// persistence/criteria_sql.go ExpressionJoins).
func fieldNamesUsedIn(expr criteria.Expression) []string {
	var names []string
	_ = criteria.Walk(expr, func(e criteria.Expression) error {
		for name := range criteria.Fields(e) {
			names = append(names, name)
		}
		return nil
	})
	return names
}

var _ = Describe("Templates", func() {
	Describe("FindTemplate", func() {
		It("finds a known template by ID", func() {
			tpl, ok := playlists.FindTemplate(playlists.TemplateHeavyRotation)
			Expect(ok).To(BeTrue())
			Expect(tpl.ID).To(Equal(playlists.TemplateHeavyRotation))
		})

		It("returns false for an unknown ID", func() {
			_, ok := playlists.FindTemplate("does-not-exist")
			Expect(ok).To(BeFalse())
		})
	})

	Describe("Templates catalog", func() {
		It("has exactly the six documented templates, each with display metadata", func() {
			ids := make([]string, len(playlists.Templates))
			for i, t := range playlists.Templates {
				ids[i] = t.ID
				Expect(t.Name).ToNot(BeEmpty(), t.ID)
				Expect(t.Description).ToNot(BeEmpty(), t.ID)
				Expect(t.Criteria.Expression).ToNot(BeNil(), t.ID)
			}
			Expect(ids).To(ConsistOf(allTemplateIDs[0], allTemplateIDs[1], allTemplateIDs[2],
				allTemplateIDs[3], allTemplateIDs[4], allTemplateIDs[5]))
		})

		DescribeTable("every field referenced exists in the criteria field map",
			func(id string) {
				tpl, ok := playlists.FindTemplate(id)
				Expect(ok).To(BeTrue())

				names := fieldNamesUsedIn(tpl.Criteria.Expression)
				Expect(names).ToNot(BeEmpty())

				for _, name := range names {
					_, ok := criteria.LookupField(name)
					Expect(ok).To(BeTrue(), "field %q not found in criteria fieldMap", name)
				}
			},
			Entry(playlists.TemplateHeavyRotation, playlists.TemplateHeavyRotation),
			Entry(playlists.TemplateOnRepeat, playlists.TemplateOnRepeat),
			Entry(playlists.TemplateRediscover, playlists.TemplateRediscover),
			Entry(playlists.TemplateRecentlyAdded, playlists.TemplateRecentlyAdded),
			Entry(playlists.TemplateFavoritesMix, playlists.TemplateFavoritesMix),
			Entry(playlists.TemplateTopRated, playlists.TemplateTopRated),
		)

		DescribeTable("criteria JSON marshal/unmarshal round-trips without error",
			func(id string) {
				tpl, ok := playlists.FindTemplate(id)
				Expect(ok).To(BeTrue())

				data, err := json.Marshal(tpl.Criteria)
				Expect(err).ToNot(HaveOccurred())

				var roundTripped criteria.Criteria
				err = json.Unmarshal(data, &roundTripped)
				Expect(err).ToNot(HaveOccurred())

				// Re-marshal and compare JSON strings (not Go structs): unmarshal
				// normalizes numeric literals to float64, so this mirrors the
				// "is reversible to/from JSON" idiom in model/criteria/criteria_test.go.
				dataAgain, err := json.Marshal(roundTripped)
				Expect(err).ToNot(HaveOccurred())
				Expect(string(dataAgain)).To(Equal(string(data)))
			},
			Entry(playlists.TemplateHeavyRotation, playlists.TemplateHeavyRotation),
			Entry(playlists.TemplateOnRepeat, playlists.TemplateOnRepeat),
			Entry(playlists.TemplateRediscover, playlists.TemplateRediscover),
			Entry(playlists.TemplateRecentlyAdded, playlists.TemplateRecentlyAdded),
			Entry(playlists.TemplateFavoritesMix, playlists.TemplateFavoritesMix),
			Entry(playlists.TemplateTopRated, playlists.TemplateTopRated),
		)
	})

	Describe("CreateFromTemplate", func() {
		var ds *tests.MockDataStore
		var mockPlsRepo *tests.MockPlaylistRepo
		var ps playlists.Playlists
		ctx := context.Background()

		BeforeEach(func() {
			mockPlsRepo = tests.CreateMockPlaylistRepo()
			ds = &tests.MockDataStore{MockedPlaylist: mockPlsRepo}
			ps = playlists.NewPlaylists(ds, core.NewImageUploadService())
			ctx = request.WithUser(ctx, model.User{ID: "user-1"})
		})

		It("creates a playlist owned by the current user with the template's rules", func() {
			pl, err := ps.CreateFromTemplate(ctx, playlists.TemplateHeavyRotation, "")
			Expect(err).ToNot(HaveOccurred())
			Expect(pl.OwnerID).To(Equal("user-1"))
			Expect(pl.Name).To(Equal("Nghe nhiều gần đây"))
			Expect(pl.IsSmartPlaylist()).To(BeTrue())
			Expect(mockPlsRepo.Last.OwnerID).To(Equal("user-1"))
		})

		It("uses the given name instead of the template default when provided", func() {
			pl, err := ps.CreateFromTemplate(ctx, playlists.TemplateOnRepeat, "My Custom Name")
			Expect(err).ToNot(HaveOccurred())
			Expect(pl.Name).To(Equal("My Custom Name"))
		})

		It("returns ErrTemplateNotFound for an unknown template ID", func() {
			_, err := ps.CreateFromTemplate(ctx, "nope", "")
			Expect(err).To(MatchError(playlists.ErrTemplateNotFound))
		})
	})
})
