package player

import (
	"testing"
)

func TestBuildAssets(t *testing.T) {
	fsys := BuildAssets()

	f, err := fsys.Open("index.html")
	if err != nil {
		t.Fatalf("expected to open index.html in embedded player assets, got error: %v", err)
	}
	defer f.Close()

	info, err := f.Stat()
	if err != nil {
		t.Fatalf("expected to stat index.html, got error: %v", err)
	}
	if info.Size() == 0 {
		t.Fatal("expected index.html to be non-empty")
	}
}
