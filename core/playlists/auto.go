package playlists

import (
	"context"
	"fmt"
	"strings"

	"github.com/navidrome/navidrome/conf"
	"github.com/navidrome/navidrome/log"
	"github.com/navidrome/navidrome/model"
	"github.com/navidrome/navidrome/model/request"
)

// autoPlaylistCommentPrefix is the marker convention used to identify playlists
// created by the periodic auto-playlist job (Phase 2 of
// plans/01-library-admin-auto-playlists.md), without requiring a DB migration:
//
//	Playlist.Comment == "auto:" + templateID
//
// Detection (see existingAutoPlaylistComments below) fetches the target user's
// own playlists and checks this prefix/value in Go. The playlist repository does
// not expose a comment-based filter (persistence/playlist_repository.go
// registerModel only declares "q", a substring OR-search over name/comment, and
// "smart"), so a precise equality match on Comment is done application-side
// rather than by inventing a new named filter.
const autoPlaylistCommentPrefix = "auto:"

// autoPlaylistComment returns the Comment marker used to identify the
// auto-generated playlist for the given template ID.
func autoPlaylistComment(templateID string) string {
	return autoPlaylistCommentPrefix + templateID
}

// RunAutoPlaylists creates the configured auto-playlist templates
// (conf.Server.AutoPlaylists.Templates) for every user who doesn't already have
// them, per Phase 2. It is idempotent: users who already have an auto playlist
// for a given template (detected via the Comment marker convention above) are
// skipped, so calling this repeatedly - e.g. on every run of the configured
// cron schedule - never creates duplicates.
//
// Each user's playlist is created with that user as owner (never one user's
// stats producing another user's playlist): the DataStore calls below are made
// with a per-user context built via request.WithUser, so ownership and
// annotation scoping fall out of that user's own identity. This is unlike the
// admin-context background jobs in scanner/phase_4_playlists.go and
// core/artwork/cache_warmer.go, which deliberately run as an admin user
// instead of impersonating each real user.
//
// This job never evaluates or stores playlist tracks itself: smart playlists
// re-evaluate their track list lazily on access, governed by
// conf.Server.SmartPlaylistRefreshDelay. Creating the Playlist row with Rules set
// is the entirety of the job's job.
func (s *playlists) RunAutoPlaylists(ctx context.Context) error {
	users, err := s.ds.User(ctx).GetAll()
	if err != nil {
		return fmt.Errorf("loading users for auto-playlists: %w", err)
	}

	templateIDs := conf.Server.AutoPlaylists.Templates
	for _, u := range users {
		userCtx := request.WithUser(ctx, u)
		existing, err := s.existingAutoPlaylistComments(userCtx, u.ID)
		if err != nil {
			log.Error(ctx, "Error loading existing auto-playlists for user", "user", u.UserName, err)
			continue
		}
		for _, templateID := range templateIDs {
			if err := s.ensureAutoPlaylist(userCtx, u, templateID, existing); err != nil {
				log.Error(ctx, "Error creating auto-playlist", "user", u.UserName, "template", templateID, err)
			}
		}
	}
	return nil
}

// ensureAutoPlaylist creates the auto-playlist for templateID for user u, unless:
//   - templateID is not a known template: logged and skipped, not an error, so a
//     stale/typo'd entry in AutoPlaylists.Templates doesn't crash the job or block
//     other users/templates.
//   - existing already has this template's marker (idempotency). existing is the
//     set built once per user by existingAutoPlaylistComments, rather than a
//     fresh Playlist.GetAll per template.
func (s *playlists) ensureAutoPlaylist(ctx context.Context, u model.User, templateID string, existing map[string]bool) error {
	if _, ok := FindTemplate(templateID); !ok {
		log.Warn(ctx, "Skipping unknown auto-playlist template", "template", templateID, "user", u.UserName)
		return nil
	}

	if existing[autoPlaylistComment(templateID)] {
		return nil
	}

	if _, err := s.createFromTemplate(ctx, templateID, "", autoPlaylistComment(templateID)); err != nil {
		return fmt.Errorf("creating auto-playlist: %w", err)
	}
	log.Info(ctx, "Created auto-playlist", "user", u.UserName, "template", templateID)
	return nil
}

// existingAutoPlaylistComments returns the set of auto-playlist Comment markers
// (see autoPlaylistCommentPrefix) already owned by userID, using the Comment
// marker convention documented above. It is fetched once per user - rather than
// once per (user, template) pair, as a prior version of this function did - since
// for an admin user each Playlist.GetAll loads every playlist in the DB, making
// the per-template calls an O(users x templates) full-table-scan pattern. ctx
// must carry the target user (via request.WithUser), so GetAll is scoped to that
// user's own (+ public) playlists by the repository's userFilter; OwnerID is
// re-checked explicitly in Go as well, so a public playlist owned by a different
// user can never be mistaken for this user's auto-playlist.
func (s *playlists) existingAutoPlaylistComments(ctx context.Context, userID string) (map[string]bool, error) {
	all, err := s.ds.Playlist(ctx).GetAll()
	if err != nil {
		return nil, err
	}
	existing := make(map[string]bool, len(all))
	for _, p := range all {
		if p.OwnerID == userID && strings.HasPrefix(p.Comment, autoPlaylistCommentPrefix) {
			existing[p.Comment] = true
		}
	}
	return existing, nil
}
