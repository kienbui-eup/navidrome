package soulseek

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestSearch(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "POST" {
			t.Errorf("expected POST, got %s", r.Method)
		}
		if r.URL.Path != "/api/v0/searches" {
			t.Errorf("expected path /api/v0/searches, got %s", r.URL.Path)
		}
		if r.Header.Get("X-API-Key") != "secret" {
			t.Errorf("expected apiKey 'secret', got %s", r.Header.Get("X-API-Key"))
		}

		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		w.Write([]byte(`{"id":"test-uuid-123"}`))
	}))
	defer server.Close()

	client := NewClient(server.URL, "secret")
	id, err := client.Search(context.Background(), "test artist")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if id != "test-uuid-123" {
		t.Errorf("expected id 'test-uuid-123', got %s", id)
	}
}

func TestGetResults(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "GET" {
			t.Errorf("expected GET, got %s", r.Method)
		}
		if r.URL.Path != "/api/v0/searches/test-id" {
			t.Errorf("expected path /api/v0/searches/test-id, got %s", r.URL.Path)
		}

		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		w.Write([]byte(`{
			"id": "test-id",
			"searchText": "test query",
			"results": [
				{
					"username": "peer1",
					"files": [
						{"filename": "song.flac", "size": 100, "bitRate": 1000, "extension": "flac", "length": 210}
					]
				}
			]
		}`))
	}))
	defer server.Close()

	client := NewClient(server.URL, "")
	res, err := client.GetResults(context.Background(), "test-id")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if len(res.Results) != 1 {
		t.Fatalf("expected 1 result, got %d", len(res.Results))
	}
	if res.Results[0].Username != "peer1" {
		t.Errorf("expected peer1, got %s", res.Results[0].Username)
	}
	if res.Results[0].Files[0].Filename != "song.flac" {
		t.Errorf("expected song.flac, got %s", res.Results[0].Files[0].Filename)
	}
}

func TestDownload(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "POST" {
			t.Errorf("expected POST, got %s", r.Method)
		}
		if r.URL.Path != "/api/v0/transfers/downloads/peer1" {
			t.Errorf("expected path /api/v0/transfers/downloads/peer1, got %s", r.URL.Path)
		}

		var payload []FileToDownload
		if err := json.NewDecoder(r.Body).Decode(&payload); err != nil {
			t.Fatalf("failed decoding body: %v", err)
		}
		if len(payload) != 1 || payload[0].Filename != "song.flac" || payload[0].Size != 100 {
			t.Errorf("unexpected payload content: %+v", payload)
		}

		w.WriteHeader(http.StatusAccepted)
	}))
	defer server.Close()

	client := NewClient(server.URL, "")
	err := client.Download(context.Background(), "peer1", "song.flac", 100)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
}

func TestGetDownloads(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		// We'll return the object structure to verify fallback logic
		w.Write([]byte(`{
			"enqueued": [
				{"id": "id-1", "username": "p1", "file": "song.flac", "status": "Queued", "progress": 0.1, "size": 100}
			],
			"completed": [
				{"id": "id-2", "username": "p2", "filename": "other.flac", "state": "Completed", "progress": 1.0, "size": 200}
			]
		}`))
	}))
	defer server.Close()

	client := NewClient(server.URL, "")
	items, err := client.GetDownloads(context.Background())
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if len(items) != 2 {
		t.Fatalf("expected 2 items, got %d", len(items))
	}

	// Verify alternate JSON key mappings
	item1 := items[0]
	if item1.GetFilename() != "song.flac" {
		t.Errorf("expected song.flac, got %s", item1.GetFilename())
	}
	if item1.GetState() != "Queued" {
		t.Errorf("expected Queued, got %s", item1.GetState())
	}

	item2 := items[1]
	if item2.GetFilename() != "other.flac" {
		t.Errorf("expected other.flac, got %s", item2.GetFilename())
	}
	if item2.GetState() != "Completed" {
		t.Errorf("expected Completed, got %s", item2.GetState())
	}
}
