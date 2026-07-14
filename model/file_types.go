package model

import (
	"mime"
	"path/filepath"
	"slices"
	"strings"
)

var excludeAudioType = []string{
	"audio/mpegurl",
	"audio/x-mpegurl",
	"audio/x-scpls",
}

var knownAudioExtensions = map[string]bool{
	".mp3":  true,
	".flac": true,
	".m4a":  true,
	".m4b":  true,
	".aac":  true,
	".ogg":  true,
	".oga":  true,
	".opus": true,
	".wav":  true,
	".wma":  true,
	".alac": true,
	".aiff": true,
	".aif":  true,
	".ape":  true,
	".wv":   true,
	".mpc":  true,
	".dsf":  true,
	".dff":  true,
}

func IsAudioFile(filePath string) bool {
	extension := strings.ToLower(filepath.Ext(filePath))
	if knownAudioExtensions[extension] {
		return true
	}
	mimeType := mime.TypeByExtension(extension)
	return mimeType != "" && !slices.Contains(excludeAudioType, mimeType) && strings.HasPrefix(mimeType, "audio/")
}

func IsImageFile(filePath string) bool {
	extension := filepath.Ext(filePath)
	return strings.HasPrefix(mime.TypeByExtension(extension), "image/")
}

func IsValidPlaylist(filePath string) bool {
	extension := strings.ToLower(filepath.Ext(filePath))
	return extension == ".m3u" || extension == ".m3u8" || extension == ".nsp"
}
