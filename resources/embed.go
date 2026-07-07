package resources

import (
	"embed"
	"io/fs"
	"os"
	"path"

	"github.com/vi2play/vi2play/conf"
	"github.com/vi2play/vi2play/utils/merge"
)

//go:embed *
var embedFS embed.FS

func FS() fs.FS {
	return merge.FS{
		Base:    embedFS,
		Overlay: os.DirFS(path.Join(conf.Server.DataFolder.String(), "resources")),
	}
}
