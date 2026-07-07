package core

import (
	"github.com/google/wire"
	"github.com/vi2play/vi2play/core/agents"
	"github.com/vi2play/vi2play/core/external"
	"github.com/vi2play/vi2play/core/ffmpeg"
	"github.com/vi2play/vi2play/core/lyrics"
	"github.com/vi2play/vi2play/core/matcher"
	"github.com/vi2play/vi2play/core/metrics"
	"github.com/vi2play/vi2play/core/playback"
	"github.com/vi2play/vi2play/core/playlists"
	"github.com/vi2play/vi2play/core/scrobbler"
	"github.com/vi2play/vi2play/core/stream"
)

var Set = wire.NewSet(
	stream.NewMediaStreamer,
	stream.GetTranscodingCache,
	NewArchiver,
	NewPlayers,
	NewShare,
	playlists.NewPlaylists,
	NewLibrary,
	NewUser,
	NewImporter,
	GetUpgrader,
	NewMaintenance,
	NewImageUploadService,
	wire.Bind(new(playlists.ImageUploadService), new(ImageUploadService)),
	stream.NewTranscodeDecider,
	agents.GetAgents,
	external.NewProvider,
	matcher.New,
	wire.Bind(new(external.Agents), new(*agents.Agents)),
	ffmpeg.New,
	scrobbler.GetPlayTracker,
	playback.GetInstance,
	metrics.GetInstance,
	lyrics.NewLyrics,
)
