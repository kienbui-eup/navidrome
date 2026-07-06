package playlists

import (
	"context"
	"errors"

	"github.com/navidrome/navidrome/model"
	"github.com/navidrome/navidrome/model/criteria"
	"github.com/navidrome/navidrome/model/request"
)

// Built-in smart playlist template IDs. See plans/01-library-admin-auto-playlists.md,
// Phase 1, for the source table these were derived from.
const (
	TemplateHeavyRotation = "heavy_rotation"
	TemplateOnRepeat      = "on_repeat"
	TemplateRediscover    = "rediscover"
	TemplateRecentlyAdded = "recently_added"
	TemplateFavoritesMix  = "favorites_mix"
	TemplateTopRated      = "top_rated"
)

// Day windows, play-count thresholds and result limits used by the built-in
// templates below. Named here instead of scattered as magic numbers, per Phase 1.
const (
	heavyRotationWindowDays = 30 // "played recently" window for Heavy Rotation
	heavyRotationMinPlays   = 3  // playcount must be greater than this
	heavyRotationLimit      = 25

	onRepeatWindowDays = 7 // "played recently" window for On Repeat
	onRepeatMinPlays   = 2 // playcount must be greater than this
	onRepeatLimit      = 25

	rediscoverMinPlays  = 5  // playcount must be greater than this
	rediscoverStaleDays = 90 // lastplayed must NOT be within this many days
	rediscoverLimit     = 50

	recentlyAddedWindowDays = 14 // dateadded must be within this many days
	recentlyAddedLimit      = 100

	favoritesMixLimit = 50

	topRatedMinRating = 3 // rating must be greater than this
	topRatedLimit     = 50
)

// ErrTemplateNotFound is returned when a caller references a template ID that
// does not exist in the Templates catalog.
var ErrTemplateNotFound = errors.New("smart playlist template not found")

// Template is a built-in smart playlist archetype: display metadata plus a
// fixed criteria.Criteria describing the underlying rule.
type Template struct {
	ID          string
	Name        string
	Description string
	Criteria    criteria.Criteria
}

// Templates is the hard-coded catalog of built-in smart playlist templates, in
// display order. Field names used in the expressions below (playcount,
// lastplayed, dateadded, loved, rating) all exist in model/criteria/fields.go.
var Templates = []Template{
	{
		ID:          TemplateHeavyRotation,
		Name:        "Nghe nhiều gần đây",
		Description: "Bài hát nghe nhiều lần và vẫn đang được nghe trong thời gian gần đây",
		Criteria: criteria.Criteria{
			Expression: criteria.All{
				criteria.Gt{"playcount": heavyRotationMinPlays},
				criteria.InTheLast{"lastplayed": heavyRotationWindowDays},
			},
			Sort:  "playcount",
			Order: "desc",
			Limit: heavyRotationLimit,
		},
	},
	{
		ID:          TemplateOnRepeat,
		Name:        "Đang nghe lặp lại",
		Description: "Bài hát bạn đang nghe đi nghe lại trong những ngày gần đây",
		Criteria: criteria.Criteria{
			Expression: criteria.All{
				criteria.InTheLast{"lastplayed": onRepeatWindowDays},
				criteria.Gt{"playcount": onRepeatMinPlays},
			},
			Sort:  "lastplayed",
			Order: "desc",
			Limit: onRepeatLimit,
		},
	},
	{
		ID:          TemplateRediscover,
		Name:        "Tái khám phá",
		Description: "Bài hát từng nghe nhiều nhưng đã lâu chưa nghe lại",
		Criteria: criteria.Criteria{
			Expression: criteria.All{
				criteria.Gt{"playcount": rediscoverMinPlays},
				criteria.NotInTheLast{"lastplayed": rediscoverStaleDays},
			},
			Sort:  "random",
			Limit: rediscoverLimit,
		},
	},
	{
		ID:          TemplateRecentlyAdded,
		Name:        "Mới thêm vào",
		Description: "Bài hát mới được thêm vào thư viện gần đây",
		Criteria: criteria.Criteria{
			Expression: criteria.All{
				criteria.InTheLast{"dateadded": recentlyAddedWindowDays},
			},
			Sort:  "dateadded",
			Order: "desc",
			Limit: recentlyAddedLimit,
		},
	},
	{
		ID:          TemplateFavoritesMix,
		Name:        "Yêu thích trộn ngẫu nhiên",
		Description: "Trộn ngẫu nhiên các bài hát bạn đã đánh dấu yêu thích",
		Criteria: criteria.Criteria{
			Expression: criteria.All{
				criteria.Is{"loved": true},
			},
			Sort:  "random",
			Limit: favoritesMixLimit,
		},
	},
	{
		ID:          TemplateTopRated,
		Name:        "Đánh giá cao",
		Description: "Bài hát được bạn đánh giá cao",
		Criteria: criteria.Criteria{
			Expression: criteria.All{
				criteria.Gt{"rating": topRatedMinRating},
			},
			Sort:  "rating",
			Order: "desc",
			Limit: topRatedLimit,
		},
	},
}

// FindTemplate returns the template with the given ID, and whether it was found.
func FindTemplate(id string) (Template, bool) {
	for _, t := range Templates {
		if t.ID == id {
			return t, true
		}
	}
	return Template{}, false
}

// ListTemplates returns the catalog of built-in smart playlist templates.
func (s *playlists) ListTemplates() []Template {
	return Templates
}

// CreateFromTemplate creates a new smart playlist for the current user (owner
// derived from context, mirroring savePlaylist in rest_adapter.go) using the
// given template's criteria as Rules. If name is empty, the template's display
// name is used. Persistence goes through the same DataStore.Playlist(ctx).Put
// path used by the regular POST /playlist flow (see rest_adapter.go savePlaylist).
func (s *playlists) CreateFromTemplate(ctx context.Context, templateID string, name string) (*model.Playlist, error) {
	tpl, ok := FindTemplate(templateID)
	if !ok {
		return nil, ErrTemplateNotFound
	}
	if name == "" {
		name = tpl.Name
	}
	usr, _ := request.UserFrom(ctx)
	rules := tpl.Criteria
	pls := &model.Playlist{
		Name:    name,
		OwnerID: usr.ID,
		Rules:   &rules,
	}
	if err := s.ds.Playlist(ctx).Put(pls); err != nil {
		return nil, err
	}
	return pls, nil
}
