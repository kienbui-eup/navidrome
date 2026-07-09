package deezer

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
)

// TrackMetadata is the public track structure returned by Deezer API.
type TrackMetadata struct {
	ID                    int    `json:"id"`
	Title                 string `json:"title"`
	TitleShort            string `json:"title_short"`
	Duration              int    `json:"duration"`
	Preview               string `json:"preview"`
	MD5Origin             string `json:"md5_image"` // Some public tracks expose md5_image or md5_origin
	Artist                struct {
		Name string `json:"name"`
	} `json:"artist"`
	Album struct {
		Title string `json:"title"`
	} `json:"album"`
}

// FetchTrackMetadata calls public api.deezer.com to fetch the track details.
func FetchTrackMetadata(ctx context.Context, trackID string) (*TrackMetadata, error) {
	url := fmt.Sprintf("https://api.deezer.com/track/%s", trackID)
	req, err := http.NewRequestWithContext(ctx, "GET", url, nil)
	if err != nil {
		return nil, err
	}

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != 200 {
		return nil, fmt.Errorf("public api returned status: %s", resp.Status)
	}

	var meta TrackMetadata
	if err := json.NewDecoder(resp.Body).Decode(&meta); err != nil {
		return nil, err
	}
	return &meta, nil
}

// SearchTracks searches public Deezer catalog for tracks.
func SearchTracks(ctx context.Context, query string, limit int) ([]TrackMetadata, error) {
	if limit <= 0 {
		limit = 10
	}
	apiURL := fmt.Sprintf("https://api.deezer.com/search?q=%s&limit=%d", url.QueryEscape(query), limit)
	req, err := http.NewRequestWithContext(ctx, "GET", apiURL, nil)
	if err != nil {
		return nil, err
	}

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != 200 {
		return nil, fmt.Errorf("search api returned status: %s", resp.Status)
	}

	var data struct {
		Data []TrackMetadata `json:"data"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&data); err != nil {
		return nil, err
	}
	return data.Data, nil
}

// GetUserSession Handshakes with gw-light.php to fetch the api_token (checkForm).
func GetUserSession(ctx context.Context, arl string) (string, string, error) {
	req, err := http.NewRequestWithContext(ctx, "POST", "https://www.deezer.com/ajax/gw-light.php?method=deezer.getUserData&api_version=1.0", nil)
	if err != nil {
		return "", "", err
	}

	if arl != "" {
		req.AddCookie(&http.Cookie{Name: "arl", Value: arl})
	}

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return "", "", err
	}
	defer resp.Body.Close()

	var data struct {
		Results struct {
			CheckForm string `json:"checkForm"`
			User      struct {
				ID int `json:"USER_ID"`
			} `json:"USER"`
		} `json:"results"`
	}

	if err := json.NewDecoder(resp.Body).Decode(&data); err != nil {
		return "", "", err
	}

	return data.Results.CheckForm, fmt.Sprintf("%d", data.Results.User.ID), nil
}

// GetSongData fetches private song metadata including original MD5 hash using the ARL session.
func GetSongData(ctx context.Context, trackID string, arl string, apiToken string) (string, error) {
	body := map[string]any{
		"sng_id": trackID,
	}
	bodyBytes, err := json.Marshal(body)
	if err != nil {
		return "", err
	}

	url := fmt.Sprintf("https://www.deezer.com/ajax/gw-light.php?method=song.getData&api_version=1.0&api_token=%s", apiToken)
	req, err := http.NewRequestWithContext(ctx, "POST", url, bytes.NewReader(bodyBytes))
	if err != nil {
		return "", err
	}
	req.Header.Set("Content-Type", "application/json")

	if arl != "" {
		req.AddCookie(&http.Cookie{Name: "arl", Value: arl})
	}

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()

	var data struct {
		Results struct {
			MD5Origin string `json:"MD5_ORIGIN"`
		} `json:"results"`
	}

	if err := json.NewDecoder(resp.Body).Decode(&data); err != nil {
		return "", err
	}

	if data.Results.MD5Origin == "" {
		return "", fmt.Errorf("md5_origin not found for song %s", trackID)
	}

	return data.Results.MD5Origin, nil
}

// DownloadDecryptedTrack downloads and decrypts a Deezer track on-the-fly, returning its reader.
func DownloadDecryptedTrack(ctx context.Context, trackID string, arl string, preferFLAC bool) (io.Reader, string, string, error) {
	meta, err := FetchTrackMetadata(ctx, trackID)
	if err != nil {
		return nil, "", "", fmt.Errorf("failed to fetch track metadata: %w", err)
	}

	// 1. Handshake to get MD5 Origin of the track
	var md5Origin string
	if arl != "" {
		apiToken, _, err := GetUserSession(ctx, arl)
		if err == nil && apiToken != "" {
			md5Origin, _ = GetSongData(ctx, trackID, arl, apiToken)
		}
	}

	// Fallback to public preview if we can't fetch private stream metadata
	if md5Origin == "" {
		if meta.Preview != "" {
			// Pull preview stream (usually unencrypted 128kbps MP3)
			req, err := http.NewRequestWithContext(ctx, "GET", meta.Preview, nil)
			if err != nil {
				return nil, "", "", err
			}
			resp, err := http.DefaultClient.Do(req)
			if err != nil {
				return nil, "", "", err
			}
			return resp.Body, meta.Title, "mp3", nil
		}
		return nil, "", "", fmt.Errorf("unable to acquire streaming source for track %s", trackID)
	}

	// 2. Build direct mobile CDN download URL based on format preference
	formatID := "3" // 320kbps MP3 by default
	extension := "mp3"
	if preferFLAC {
		formatID = "9" // FLAC Lossless
		extension = "flac"
	}

	// Construct CDN streaming URL path
	char0 := string(md5Origin[0])
	char01 := md5Origin[0:2]
	cdnURL := fmt.Sprintf("https://e-cdns-audio.dzcdn.net/mobile/1/%s/%s/%s_%s.mp3", char0, char01, md5Origin, formatID)

	req, err := http.NewRequestWithContext(ctx, "GET", cdnURL, nil)
	if err != nil {
		return nil, "", "", err
	}
	// Add user-agent to look like a mobile/authorized client
	req.Header.Set("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148")

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return nil, "", "", err
	}

	if resp.StatusCode != 200 {
		resp.Body.Close()
		// Try fallback to 128kbps MP3 (formatID 1)
		formatID = "1"
		cdnURL = fmt.Sprintf("https://e-cdns-audio.dzcdn.net/mobile/1/%s/%s/%s_%s.mp3", char0, char01, md5Origin, formatID)
		req, err = http.NewRequestWithContext(ctx, "GET", cdnURL, nil)
		if err != nil {
			return nil, "", "", err
		}
		resp, err = http.DefaultClient.Do(req)
		if err != nil {
			return nil, "", "", err
		}
		if resp.StatusCode != 200 {
			resp.Body.Close()
			return nil, "", "", fmt.Errorf("media CDN returned status code: %d", resp.StatusCode)
		}
		extension = "mp3"
	}

	// 3. Decrypt on-the-fly using our Blowfish ECB decryptor!
	decryptedStream, err := DecryptStream(resp.Body, trackID, "g4c3bch0")
	if err != nil {
		resp.Body.Close()
		return nil, "", "", fmt.Errorf("failed to initialize decrypter stream: %w", err)
	}

	closerReader := &autoClosingReader{
		Reader: decryptedStream,
		closer: resp.Body,
	}

	return closerReader, meta.Title, extension, nil
}

// Close closes the underlying HTTP response stream.
func (acr *autoClosingReader) Close() error {
	return acr.closer.Close()
}

type autoClosingReader struct {
	io.Reader
	closer io.Closer
}
