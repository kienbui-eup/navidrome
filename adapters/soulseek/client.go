package soulseek

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"path"
	"time"

	"github.com/vi2play/vi2play/log"
)

// Client is a REST API client for slskd.
type Client struct {
	baseURL string
	apiKey  string
	client  *http.Client
}

// NewClient creates a new slskd REST client.
func NewClient(baseURL, apiKey string) *Client {
	if baseURL == "" {
		baseURL = "http://localhost:5030"
	}
	return &Client{
		baseURL: baseURL,
		apiKey:  apiKey,
		client: &http.Client{
			Timeout: 15 * time.Second,
		},
	}
}

func (c *Client) request(ctx context.Context, method, apiPath string, body any) ([]byte, error) {
	u, err := url.Parse(c.baseURL)
	if err != nil {
		return nil, fmt.Errorf("invalid base URL: %w", err)
	}
	u.Path = path.Join(u.Path, apiPath)

	var bodyReader io.Reader
	if body != nil {
		data, err := json.Marshal(body)
		if err != nil {
			return nil, fmt.Errorf("marshaling request body: %w", err)
		}
		bodyReader = bytes.NewReader(data)
	}

	req, err := http.NewRequestWithContext(ctx, method, u.String(), bodyReader)
	if err != nil {
		return nil, fmt.Errorf("creating request: %w", err)
	}

	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Accept", "application/json")
	if c.apiKey != "" {
		req.Header.Set("X-API-Key", c.apiKey)
	}

	resp, err := c.client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("executing request: %w", err)
	}
	defer resp.Body.Close()

	respData, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("reading response: %w", err)
	}

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return nil, fmt.Errorf("API error (%d): %s", resp.StatusCode, string(respData))
	}

	return respData, nil
}

// SearchTriggerResponse is returned by the Search endpoint.
type SearchTriggerResponse struct {
	ID string `json:"id"`
}

// Search initiates a Soulseek search and returns the search ID.
func (c *Client) Search(ctx context.Context, query string) (string, error) {
	payload := map[string]string{"searchText": query}
	data, err := c.request(ctx, "POST", "/api/v0/searches", payload)
	if err != nil {
		return "", err
	}

	var resp SearchTriggerResponse
	if err := json.Unmarshal(data, &resp); err != nil {
		return "", fmt.Errorf("unmarshaling search response: %w", err)
	}

	return resp.ID, nil
}

// SearchFile represents a file result from a peer.
type SearchFile struct {
	Filename  string `json:"filename"`
	Size      int64  `json:"size"`
	BitRate   int    `json:"bitRate"`
	Extension string `json:"extension"`
	Length    int    `json:"length"` // duration in seconds
}

// SearchResult represents search results from a specific peer.
type SearchResult struct {
	Username string       `json:"username"`
	Files    []SearchFile `json:"files"`
}

// SearchResponse holds search details and results.
type SearchResponse struct {
	ID         string         `json:"id"`
	SearchText string         `json:"searchText"`
	Results    []SearchResult `json:"results"`
}

// GetResults retrieves search results for a given search ID.
func (c *Client) GetResults(ctx context.Context, searchID string) (*SearchResponse, error) {
	apiPath := fmt.Sprintf("/api/v0/searches/%s", searchID)
	data, err := c.request(ctx, "GET", apiPath, nil)
	if err != nil {
		return nil, err
	}

	var resp SearchResponse
	if err := json.Unmarshal(data, &resp); err != nil {
		return nil, fmt.Errorf("unmarshaling search results: %w", err)
	}

	return &resp, nil
}

// FileToDownload defines the file format for queuing a download.
type FileToDownload struct {
	Filename string `json:"filename"`
	Size     int64  `json:"size"`
}

// Download enqueues a file download from a user.
func (c *Client) Download(ctx context.Context, username, filename string, size int64) error {
	apiPath := fmt.Sprintf("/api/v0/transfers/downloads/%s", url.PathEscape(username))
	payload := []FileToDownload{
		{
			Filename: filename,
			Size:     size,
		},
	}

	_, err := c.request(ctx, "POST", apiPath, payload)
	return err
}

// DownloadItem represents a tracked download in slskd.
type DownloadItem struct {
	ID       string  `json:"id"`
	Username string  `json:"username"`
	Filename string  `json:"filename"` // newer slskd versions
	File     string  `json:"file"`     // older slskd versions
	State    string  `json:"state"`    // newer slskd versions
	Status   string  `json:"status"`   // older slskd versions
	Progress float64 `json:"progress"`
	Size     int64   `json:"size"`
}

// GetFilename returns the file path robustly regardless of API version.
func (item *DownloadItem) GetFilename() string {
	if item.Filename != "" {
		return item.Filename
	}
	return item.File
}

// GetState returns the status string robustly regardless of API version.
func (item *DownloadItem) GetState() string {
	if item.State != "" {
		return item.State
	}
	return item.Status
}

// GetDownloads retrieves the active/completed download transfers.
func (c *Client) GetDownloads(ctx context.Context) ([]DownloadItem, error) {
	data, err := c.request(ctx, "GET", "/api/v0/transfers/downloads", nil)
	if err != nil {
		return nil, err
	}

	// slskd can return either a JSON array of items or an object with an "enqueued" or "completed" lists.
	// Let's first try parsing as a direct array.
	var list []DownloadItem
	if err := json.Unmarshal(data, &list); err == nil {
		return list, nil
	}

	// Try parsing as a map wrapper (e.g. {"enqueued": [...], "completed": [...]})
	var mapWrapper struct {
		Enqueued  []DownloadItem `json:"enqueued"`
		Completed []DownloadItem `json:"completed"`
		Transfers []DownloadItem `json:"transfers"`
	}
	if err := json.Unmarshal(data, &mapWrapper); err == nil {
		var merged []DownloadItem
		merged = append(merged, mapWrapper.Enqueued...)
		merged = append(merged, mapWrapper.Completed...)
		merged = append(merged, mapWrapper.Transfers...)
		return merged, nil
	}

	log.Warn(ctx, "Soulseek Client: could not parse transfers response, content: "+string(data))
	return nil, fmt.Errorf("invalid transfers response format")
}
