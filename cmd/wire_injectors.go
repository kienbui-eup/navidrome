//go:build wireinject

package cmd

import (
	"context"

	"github.com/google/wire"
	"github.com/vi2play/vi2play/adapters/lastfm"
	"github.com/vi2play/vi2play/adapters/listenbrainz"
	"github.com/vi2play/vi2play/core"
	"github.com/vi2play/vi2play/core/agents"
	"github.com/vi2play/vi2play/core/artwork"
	"github.com/vi2play/vi2play/core/lyrics"
	"github.com/vi2play/vi2play/core/metrics"
	"github.com/vi2play/vi2play/core/playback"
	"github.com/vi2play/vi2play/core/scrobbler"
	"github.com/vi2play/vi2play/core/sonic"
	"github.com/vi2play/vi2play/db"
	"github.com/vi2play/vi2play/model"
	"github.com/vi2play/vi2play/persistence"
	"github.com/vi2play/vi2play/plugins"
	"github.com/vi2play/vi2play/scanner"
	"github.com/vi2play/vi2play/server"
	"github.com/vi2play/vi2play/server/events"
	"github.com/vi2play/vi2play/server/nativeapi"
	"github.com/vi2play/vi2play/server/public"
	"github.com/vi2play/vi2play/server/subsonic"
)

var allProviders = wire.NewSet(
	core.Set,
	artwork.Set,
	server.New,
	subsonic.New,
	nativeapi.New,
	public.New,
	persistence.New,
	lastfm.NewRouter,
	listenbrainz.NewRouter,
	events.GetBroker,
	scanner.New,
	scanner.GetWatcher,
	metrics.GetPrometheusInstance,
	db.Db,
	plugins.GetManager,
	sonic.New,
	wire.Bind(new(agents.PluginLoader), new(*plugins.Manager)),
	wire.Bind(new(scrobbler.PluginLoader), new(*plugins.Manager)),
	wire.Bind(new(lyrics.PluginLoader), new(*plugins.Manager)),
	wire.Bind(new(sonic.PluginLoader), new(*plugins.Manager)),
	wire.Bind(new(nativeapi.PluginManager), new(*plugins.Manager)),
	wire.Bind(new(core.PluginUnloader), new(*plugins.Manager)),
	wire.Bind(new(plugins.PluginMetricsRecorder), new(metrics.Metrics)),
	wire.Bind(new(core.Watcher), new(scanner.Watcher)),
)

func CreateDataStore() model.DataStore {
	panic(wire.Build(
		allProviders,
	))
}

func CreateServer() *server.Server {
	panic(wire.Build(
		allProviders,
	))
}

func CreateNativeAPIRouter(ctx context.Context) *nativeapi.Router {
	panic(wire.Build(
		allProviders,
	))
}

func CreateSubsonicAPIRouter(ctx context.Context) *subsonic.Router {
	panic(wire.Build(
		allProviders,
	))
}

func CreatePublicRouter() *public.Router {
	panic(wire.Build(
		allProviders,
	))
}

func CreateLastFMRouter() *lastfm.Router {
	panic(wire.Build(
		allProviders,
	))
}

func CreateListenBrainzRouter() *listenbrainz.Router {
	panic(wire.Build(
		allProviders,
	))
}

func CreateInsights() metrics.Insights {
	panic(wire.Build(
		allProviders,
	))
}

func CreatePrometheus() metrics.Metrics {
	panic(wire.Build(
		allProviders,
	))
}

func CreateScanner(ctx context.Context) model.Scanner {
	panic(wire.Build(
		allProviders,
	))
}

func CreateScanWatcher(ctx context.Context) scanner.Watcher {
	panic(wire.Build(
		allProviders,
	))
}

func CreateUpgrader(ctx context.Context) core.Upgrader {
	panic(wire.Build(
		allProviders,
	))
}

func GetPlaybackServer() playback.PlaybackServer {
	panic(wire.Build(
		allProviders,
	))
}

func getPluginManager() *plugins.Manager {
	panic(wire.Build(
		allProviders,
	))
}

func GetPluginManager(ctx context.Context) *plugins.Manager {
	manager := getPluginManager()
	manager.SetSubsonicRouter(CreateSubsonicAPIRouter(ctx))
	return manager
}
