package model

import (
	"path/filepath"

	"github.com/vi2play/vi2play/conf"
	"github.com/vi2play/vi2play/consts"
)

// UploadedImagePath returns the absolute filesystem path for a manually uploaded
// entity cover image. Returns empty string if filename is empty.
func UploadedImagePath(entityType, filename string) string {
	if filename == "" {
		return ""
	}
	return filepath.Join(conf.Server.DataFolder.String(), consts.ArtworkFolder, entityType, filename)
}
