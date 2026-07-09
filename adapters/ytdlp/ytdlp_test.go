package ytdlp

import (
	"context"
	"testing"
)

func TestSearchSongsReal(t *testing.T) {
	// Skip or run fast check
	ctx := context.Background()
	songs, err := SearchSongs(ctx, "hello", 1)
	if err != nil {
		t.Skipf("Skipping real search test (perhaps network issue or yt-dlp missing): %v", err)
		return
	}

	if len(songs) == 0 {
		t.Error("expected at least one song, got zero")
		return
	}

	song := songs[0]
	if song.ID == "" {
		t.Error("expected non-empty song ID")
	}
	if song.Title == "" {
		t.Error("expected non-empty song Title")
	}
}
