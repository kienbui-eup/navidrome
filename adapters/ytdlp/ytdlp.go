package ytdlp

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"strconv"
	"strings"
)

// YTSong represents a song record parsed from yt-dlp search.
type YTSong struct {
	ID       string `json:"id"`
	Title    string `json:"title"`
	Artist   string `json:"uploader"`
	Duration int    `json:"duration"`
}

type cmdReaderCloser struct {
	io.ReadCloser
	cmd *exec.Cmd
}

func (c *cmdReaderCloser) Close() error {
	err := c.ReadCloser.Close()
	_ = c.cmd.Wait() // Reaps the process to prevent zombies
	return err
}

// DownloadAudio starts yt-dlp to stream raw audio of a video to stdout, returning a ReadCloser.
func DownloadAudio(ctx context.Context, videoID string) (io.ReadCloser, string, error) {
	targetURL := "https://www.youtube.com/watch?v=" + videoID
	if strings.HasPrefix(videoID, "http://") || strings.HasPrefix(videoID, "https://") {
		targetURL = videoID
	}

	// Build arguments dynamically
	args := []string{"-f", "bestaudio", "-o", "-", "--js-runtimes", "node"}

	// YouTube Credentials
	if ytUser := os.Getenv("ND_YOUTUBE_USERNAME"); ytUser != "" {
		args = append(args, "--username", ytUser)
	}
	if ytPass := os.Getenv("ND_YOUTUBE_PASSWORD"); ytPass != "" {
		args = append(args, "--password", ytPass)
	}
	if ytCookies := os.Getenv("ND_YOUTUBE_COOKIES_FILE"); ytCookies != "" {
		args = append(args, "--cookies", ytCookies)
	}

	// Zing MP3 Credentials
	if zingUser := os.Getenv("ND_ZING_USERNAME"); zingUser != "" {
		args = append(args, "--username", zingUser)
	}
	if zingPass := os.Getenv("ND_ZING_PASSWORD"); zingPass != "" {
		args = append(args, "--password", zingPass)
	}

	args = append(args, targetURL)

	// Query bestaudio format
	cmd := exec.CommandContext(ctx, "yt-dlp", args...)

	stdout, err := cmd.StdoutPipe()
	if err != nil {
		return nil, "", fmt.Errorf("failed to get stdout pipe: %w", err)
	}

	if err := cmd.Start(); err != nil {
		return nil, "", fmt.Errorf("failed to start yt-dlp: %w", err)
	}

	// We assume bestaudio is typically opus or m4a
	return &cmdReaderCloser{ReadCloser: stdout, cmd: cmd}, "opus", nil
}

// SearchSongs searches YouTube using yt-dlp flat-playlist dump.
func SearchSongs(ctx context.Context, query string, limit int) ([]YTSong, error) {
	if limit <= 0 {
		limit = 5
	}
	searchStr := fmt.Sprintf("ytsearch%d:%s", limit, query)

	// Build arguments dynamically
	args := []string{
		"--flat-playlist",
		"--dump-single-json",
		"--js-runtimes", "node",
	}

	// YouTube Credentials for Search
	if ytUser := os.Getenv("ND_YOUTUBE_USERNAME"); ytUser != "" {
		args = append(args, "--username", ytUser)
	}
	if ytPass := os.Getenv("ND_YOUTUBE_PASSWORD"); ytPass != "" {
		args = append(args, "--password", ytPass)
	}
	if ytCookies := os.Getenv("ND_YOUTUBE_COOKIES_FILE"); ytCookies != "" {
		args = append(args, "--cookies", ytCookies)
	}

	args = append(args, searchStr)

	cmd := exec.CommandContext(ctx, "yt-dlp", args...)
	var stderr bytes.Buffer
	cmd.Stderr = &stderr

	output, err := cmd.Output()
	if err != nil {
		return nil, fmt.Errorf("yt-dlp search execution failed: %w (stderr: %s)", err, strings.TrimSpace(stderr.String()))
	}

	var result struct {
		Entries []struct {
			ID       string      `json:"id"`
			Title    string      `json:"title"`
			Uploader string      `json:"uploader"`
			Duration interface{} `json:"duration"` // Can be float64 or int or null
		} `json:"entries"`
	}

	if err := json.Unmarshal(output, &result); err != nil {
		return nil, fmt.Errorf("failed to parse search result json: %w", err)
	}

	var songs []YTSong
	for _, entry := range result.Entries {
		if entry.ID == "" || entry.Title == "" {
			continue
		}

		duration := 0
		if entry.Duration != nil {
			switch val := entry.Duration.(type) {
			case float64:
				duration = int(val)
			case string:
				if parsed, parseErr := strconv.Atoi(val); parseErr == nil {
					duration = parsed
				}
			}
		}

		songs = append(songs, YTSong{
			ID:       entry.ID,
			Title:    entry.Title,
			Artist:   entry.Uploader,
			Duration: duration,
		})
	}

	return songs, nil
}

// tempFileReadCloser wraps an os.File and a temporary directory path,
// ensuring the directory and all its files are cleaned up upon Close().
type tempFileReadCloser struct {
	file    *os.File
	tempDir string
}

func (t *tempFileReadCloser) Read(p []byte) (n int, err error) {
	return t.file.Read(p)
}

func (t *tempFileReadCloser) Close() error {
	fileErr := t.file.Close()
	removeErr := os.RemoveAll(t.tempDir)
	if fileErr != nil {
		return fileErr
	}
	return removeErr
}

// DownloadAndTagAudio downloads audio via yt-dlp, transcodes to high-quality MP3,
// embeds the official video thumbnail as cover artwork, injects standard ID3v2 tags,
// and returns a ReadCloser that streams the file and self-cleans on close.
func DownloadAndTagAudio(ctx context.Context, targetURL, title, artist, album string) (io.ReadCloser, string, error) {
	tempDir, err := os.MkdirTemp("", "nd_import_")
	if err != nil {
		return nil, "", fmt.Errorf("failed to create temp dir: %w", err)
	}

	downloadPath := filepath.Join(tempDir, "download.mp3")
	taggedPath := filepath.Join(tempDir, "tagged.mp3")

	// Attempt download with --embed-thumbnail
	args := []string{
		"-x",
		"--audio-format", "mp3",
		"--audio-quality", "0",
		"--embed-thumbnail",
		"--js-runtimes", "node",
		"-o", downloadPath,
	}

	if ytUser := os.Getenv("ND_YOUTUBE_USERNAME"); ytUser != "" {
		args = append(args, "--username", ytUser)
	}
	if ytPass := os.Getenv("ND_YOUTUBE_PASSWORD"); ytPass != "" {
		args = append(args, "--password", ytPass)
	}
	if ytCookies := os.Getenv("ND_YOUTUBE_COOKIES_FILE"); ytCookies != "" {
		args = append(args, "--cookies", ytCookies)
	}
	if zingUser := os.Getenv("ND_ZING_USERNAME"); zingUser != "" {
		args = append(args, "--username", zingUser)
	}
	if zingPass := os.Getenv("ND_ZING_PASSWORD"); zingPass != "" {
		args = append(args, "--password", zingPass)
	}

	args = append(args, targetURL)

	cmd := exec.CommandContext(ctx, "yt-dlp", args...)
	var stderr bytes.Buffer
	cmd.Stderr = &stderr

	if err := cmd.Run(); err != nil {
		// Fallback without thumbnail embedding if failed
		argsNoThumb := []string{
			"-x",
			"--audio-format", "mp3",
			"--audio-quality", "0",
			"--js-runtimes", "node",
			"-o", downloadPath,
		}

		if ytUser := os.Getenv("ND_YOUTUBE_USERNAME"); ytUser != "" {
			argsNoThumb = append(argsNoThumb, "--username", ytUser)
		}
		if ytPass := os.Getenv("ND_YOUTUBE_PASSWORD"); ytPass != "" {
			argsNoThumb = append(argsNoThumb, "--password", ytPass)
		}
		if ytCookies := os.Getenv("ND_YOUTUBE_COOKIES_FILE"); ytCookies != "" {
			argsNoThumb = append(argsNoThumb, "--cookies", ytCookies)
		}
		if zingUser := os.Getenv("ND_ZING_USERNAME"); zingUser != "" {
			argsNoThumb = append(argsNoThumb, "--username", zingUser)
		}
		if zingPass := os.Getenv("ND_ZING_PASSWORD"); zingPass != "" {
			argsNoThumb = append(argsNoThumb, "--password", zingPass)
		}

		argsNoThumb = append(argsNoThumb, targetURL)

		cmdFallback := exec.CommandContext(ctx, "yt-dlp", argsNoThumb...)
		var stderrFallback bytes.Buffer
		cmdFallback.Stderr = &stderrFallback
		if errFallback := cmdFallback.Run(); errFallback != nil {
			_ = os.RemoveAll(tempDir)
			return nil, "", fmt.Errorf("failed to download audio via yt-dlp: %v (fallback stderr: %s)", errFallback, stderrFallback.String())
		}
	}

	// Ingress and overwrite metadata tags using ffmpeg
	ffmpegArgs := []string{
		"-y",
		"-i", downloadPath,
		"-map", "0",
		"-c", "copy",
		"-metadata", "title=" + title,
		"-metadata", "artist=" + artist,
		"-metadata", "album=" + album,
		taggedPath,
	}

	ffmpegCmd := exec.CommandContext(ctx, "ffmpeg", ffmpegArgs...)
	if err := ffmpegCmd.Run(); err != nil {
		// Fallback to simple tagging without map option if it fails
		ffmpegArgsSimple := []string{
			"-y",
			"-i", downloadPath,
			"-c", "copy",
			"-metadata", "title=" + title,
			"-metadata", "artist=" + artist,
			"-metadata", "album=" + album,
			taggedPath,
		}
		ffmpegCmdSimple := exec.CommandContext(ctx, "ffmpeg", ffmpegArgsSimple...)
		if errSimple := ffmpegCmdSimple.Run(); errSimple != nil {
			taggedPath = downloadPath
		}
	}

	file, err := os.Open(taggedPath)
	if err != nil {
		_ = os.RemoveAll(tempDir)
		return nil, "", fmt.Errorf("failed to open tagged mp3 file: %w", err)
	}

	return &tempFileReadCloser{file: file, tempDir: tempDir}, "mp3", nil
}
