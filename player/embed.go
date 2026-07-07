package player

import (
	"embed"
	"io/fs"
)

//go:embed dist/*
var filesystem embed.FS

func BuildAssets() fs.FS {
	build, _ := fs.Sub(filesystem, "dist")
	return build
}
