package core

import (
	"reflect"
	"strings"
	"testing"

	"github.com/vi2play/vi2play/conf"
	"github.com/vi2play/vi2play/model"
)

func intPtr(i int) *int { return &i }

func TestNormalizeForMatch(t *testing.T) {
	cases := []struct{ in, want string }{
		{"Café Del Mar", "cafe del mar"},
		{"Bohemian Rhapsody (Remastered 2011)", "bohemian rhapsody remastered 2011"},
		{"  Multiple   Spaces  ", "multiple spaces"},
		{"", ""},
		{"Rock & Roll!", "rock roll"},
		{"Motörhead", "motorhead"},
	}
	for _, c := range cases {
		got := strings.Join(normalizeForMatch(c.in), " ")
		if got != c.want {
			t.Errorf("normalizeForMatch(%q) = %q, want %q", c.in, got, c.want)
		}
	}
}

func TestTokenSetSimilarity(t *testing.T) {
	cases := []struct {
		name string
		a, b string
		want int
	}{
		{"candidate title is a subset of artist+title", "Queen Bohemian Rhapsody", "Bohemian Rhapsody", 80},
		{"identical strings", "Queen Bohemian Rhapsody", "Queen Bohemian Rhapsody", 100},
		{"disjoint strings", "Queen Bohemian Rhapsody", "Metallica Enter Sandman", 0},
		{"empty original", "", "Bohemian Rhapsody", 0},
		{"empty candidate", "Queen Bohemian Rhapsody", "", 0},
		{"case/accents/punctuation are ignored", "QUEEN, Bohémian Rhapsody!!", "bohemian rhapsody", 80},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			if got := tokenSetSimilarity(c.a, c.b); got != c.want {
				t.Errorf("tokenSetSimilarity(%q, %q) = %d, want %d", c.a, c.b, got, c.want)
			}
		})
	}
}

func TestMatchScore(t *testing.T) {
	cases := []struct {
		name                          string
		originalArtist, originalTitle string
		candidateTitle                string
		hasDuration                   bool
		durationDeltaSec              float64
		want                          int
	}{
		{
			name:           "plain match, no duration data available",
			originalArtist: "Queen", originalTitle: "Bohemian Rhapsody",
			candidateTitle: "Bohemian Rhapsody",
			want:           80, // tokenSetSimilarity("Queen Bohemian Rhapsody","Bohemian Rhapsody")
		},
		{
			name:           "duration within +-5s window adds the bonus",
			originalArtist: "Queen", originalTitle: "Bohemian Rhapsody",
			candidateTitle: "Bohemian Rhapsody",
			hasDuration:    true, durationDeltaSec: 3,
			want: 90, // 80 + matchDurationBonus(10)
		},
		{
			name:           "duration outside the window: no bonus",
			originalArtist: "Queen", originalTitle: "Bohemian Rhapsody",
			candidateTitle: "Bohemian Rhapsody",
			hasDuration:    true, durationDeltaSec: 30,
			want: 80,
		},
		{
			name:           "negative delta within window still bonuses",
			originalArtist: "Queen", originalTitle: "Bohemian Rhapsody",
			candidateTitle: "Bohemian Rhapsody",
			hasDuration:    true, durationDeltaSec: -4,
			want: 90,
		},
		{
			name:           "live keyword only in candidate is penalized",
			originalArtist: "Queen", originalTitle: "Bohemian Rhapsody",
			candidateTitle: "Bohemian Rhapsody (Live)",
			want:           36, // sim=66 (2/(3+3)*200=66), penalty=30
		},
		{
			name:           "cover keyword present in both original and candidate is not penalized",
			originalArtist: "Queen", originalTitle: "Bohemian Rhapsody (Cover)",
			candidateTitle: "Bohemian Rhapsody (Cover)",
			want:           85, // sim = 2*3/(4+3)*100 = 85 (truncated), no penalty
		},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			got := matchScore(c.originalArtist, c.originalTitle, c.candidateTitle, c.hasDuration, c.durationDeltaSec)
			if got != c.want {
				t.Errorf("matchScore() = %d, want %d", got, c.want)
			}
		})
	}
}

func TestClampScore(t *testing.T) {
	cases := map[int]int{-50: 0, 0: 0, 50: 50, 100: 100, 150: 100}
	for in, want := range cases {
		if got := clampScore(in); got != want {
			t.Errorf("clampScore(%d) = %d, want %d", in, got, want)
		}
	}
}

func TestParseIADuration(t *testing.T) {
	cases := []struct {
		in      string
		wantSec float64
		wantOK  bool
	}{
		{"245.67", 245.67, true},
		{"4:05", 245, true},
		{"1:04:05", 3845, true},
		{"", 0, false},
		{"not-a-number", 0, false},
		{"1:not-a-number", 0, false},
	}
	for _, c := range cases {
		sec, ok := parseIADuration(c.in)
		if ok != c.wantOK {
			t.Errorf("parseIADuration(%q) ok = %v, want %v", c.in, ok, c.wantOK)
			continue
		}
		if ok && sec != c.wantSec {
			t.Errorf("parseIADuration(%q) = %v, want %v", c.in, sec, c.wantSec)
		}
	}
}

func TestIsLosslessCodec(t *testing.T) {
	cases := map[string]bool{
		"mp3": false, "aac": false, "vorbis": false, "opus": false,
		"wma": false, "mpc": false, "": false,
		"alac": true, "flac": true, "pcm": true, "ape": true,
		"wv": true, "dsd": true, "tta": true, "tak": true, "shn": true,
		"MP3": false, "FLAC": true, // case-insensitive
	}
	for codec, want := range cases {
		if got := isLosslessCodec(codec); got != want {
			t.Errorf("isLosslessCodec(%q) = %v, want %v", codec, got, want)
		}
	}
}

func TestEstimateQuality(t *testing.T) {
	cases := []struct {
		name, formatHint string
		want             qualityTier
	}{
		{"track.mp3", "VBR MP3", qualityTier{Lossless: false}},
		{"track.mp3", "320kbps MP3", qualityTier{Lossless: false, BitRateKbps: 320}},
		{"track.flac", "24bit Flac 96000Hz", qualityTier{Lossless: true, SampleRate: 96000, BitDepth: 24}},
		{"track.m4a", "", qualityTier{Lossless: false}}, // ambiguous container: conservatively lossy
		{"track.wav", "", qualityTier{Lossless: true}},
		{"track.aiff", "", qualityTier{Lossless: true}},
		{"weird%20name.mp3?dl=1", "192Kbps", qualityTier{Lossless: false, BitRateKbps: 192}},
	}
	for _, c := range cases {
		t.Run(c.name+"/"+c.formatHint, func(t *testing.T) {
			got := estimateQuality(c.name, c.formatHint)
			if got != c.want {
				t.Errorf("estimateQuality(%q, %q) = %+v, want %+v", c.name, c.formatHint, got, c.want)
			}
		})
	}
}

// sign normalizes an int to -1/0/1 so quality-tier comparison tests only
// assert the direction of the result, matching compareQualityTier's contract.
func sign(n int) int {
	switch {
	case n < 0:
		return -1
	case n > 0:
		return 1
	default:
		return 0
	}
}

// TestCompareQualityTier is the table-driven test for the stage-1 quality
// comparison rule (docs/superpowers/specs/2026-07-06-quality-upgrader-design.md,
// "Chấm điểm chất lượng — giai đoạn 1"): the candidate must win strictly at
// the first tier (format class, then bitrate, then sample rate/bit depth)
// that differs; ties or "unknown" values never disqualify or qualify.
func TestCompareQualityTier(t *testing.T) {
	cases := []struct {
		name string
		a, b qualityTier
		want int // matches compareQualityTier's own contract: 1 => a wins, -1 => b wins, 0 => tie/not comparable
	}{
		{"lossless (b) beats lossy (a) regardless of bitrate", qualityTier{Lossless: false, BitRateKbps: 320}, qualityTier{Lossless: true}, -1},
		{"lossless (a) beats lossy (b) regardless of bitrate", qualityTier{Lossless: true}, qualityTier{Lossless: false, BitRateKbps: 320}, 1},
		{"both lossy, unknown candidate bitrate: not comparable", qualityTier{Lossless: false, BitRateKbps: 128}, qualityTier{Lossless: false}, 0},
		{"both lossy, higher bitrate wins", qualityTier{Lossless: false, BitRateKbps: 128}, qualityTier{Lossless: false, BitRateKbps: 320}, -1},
		{"both lossy, lower bitrate loses", qualityTier{Lossless: false, BitRateKbps: 320}, qualityTier{Lossless: false, BitRateKbps: 128}, 1},
		{"equal bitrate ties at that tier, falls through to sample rate", qualityTier{Lossless: false, BitRateKbps: 128, SampleRate: 44100}, qualityTier{Lossless: false, BitRateKbps: 128, SampleRate: 48000}, -1},
		{"sample rate tie falls through to bit depth", qualityTier{Lossless: true, SampleRate: 44100, BitDepth: 16}, qualityTier{Lossless: true, SampleRate: 44100, BitDepth: 24}, -1},
		{"fully equal is a tie", qualityTier{Lossless: false, BitRateKbps: 128, SampleRate: 44100, BitDepth: 16}, qualityTier{Lossless: false, BitRateKbps: 128, SampleRate: 44100, BitDepth: 16}, 0},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			got := sign(compareQualityTier(c.a, c.b))
			if got != c.want {
				t.Errorf("compareQualityTier(%+v, %+v) sign = %d, want %d", c.a, c.b, got, c.want)
			}
		})
	}
}

func TestCandidateWins(t *testing.T) {
	cases := []struct {
		name                string
		original, candidate qualityTier
		want                bool
	}{
		{"flac candidate beats mp3 original", qualityTier{Lossless: false, BitRateKbps: 128}, qualityTier{Lossless: true}, true},
		{"same lossy class, unknown candidate bitrate: does not win", qualityTier{Lossless: false, BitRateKbps: 128}, qualityTier{Lossless: false}, false},
		{"lower-bitrate lossy candidate does not win", qualityTier{Lossless: false, BitRateKbps: 320}, qualityTier{Lossless: false, BitRateKbps: 128}, false},
		{"higher-bitrate lossy candidate wins", qualityTier{Lossless: false, BitRateKbps: 128}, qualityTier{Lossless: false, BitRateKbps: 320}, true},
		{"identical tiers do not win (tie is not a win)", qualityTier{Lossless: true, SampleRate: 44100}, qualityTier{Lossless: true, SampleRate: 44100}, false},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			if got := candidateWins(c.original, c.candidate); got != c.want {
				t.Errorf("candidateWins(%+v, %+v) = %v, want %v", c.original, c.candidate, got, c.want)
			}
		})
	}
}

func TestIsUpgradableTrack(t *testing.T) {
	orig := conf.Server.Upgrade
	defer func() { conf.Server.Upgrade = orig }()

	cases := []struct {
		name       string
		mf         model.MediaFile
		minBitRate int
		want       bool
	}{
		{"mp3 lossy, no bitrate ceiling", model.MediaFile{Suffix: "mp3", BitRate: 320}, 0, true},
		{"flac is never upgradable", model.MediaFile{Suffix: "flac", BitRate: 1000}, 0, false},
		{"wav is never upgradable", model.MediaFile{Suffix: "wav", BitRate: 1411}, 0, false},
		{"m4a with bit depth is ALAC (lossless): excluded", model.MediaFile{Suffix: "m4a", BitRate: 1000, BitDepth: intPtr(24)}, 0, false},
		{"m4a without bit depth is AAC (lossy): included", model.MediaFile{Suffix: "m4a", BitRate: 256}, 0, true},
		{"mp3 at/above the bitrate ceiling is excluded", model.MediaFile{Suffix: "mp3", BitRate: 320}, 256, false},
		{"mp3 below the bitrate ceiling is included", model.MediaFile{Suffix: "mp3", BitRate: 128}, 256, true},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			conf.Server.Upgrade.MinBitRate = c.minBitRate
			if got := isUpgradableTrack(c.mf); got != c.want {
				t.Errorf("isUpgradableTrack() = %v, want %v", got, c.want)
			}
		})
	}
}

func TestParseUpgradeSources(t *testing.T) {
	cases := []struct {
		in   string
		want []string
	}{
		{"archive", []string{"archive"}},
		{"archive,drive,rss", []string{"archive", "drive", "rss"}},
		{" Archive , DRIVE ", []string{"archive", "drive"}},
		{"archive,archive", []string{"archive"}},
		{"archive,bogus,drive", []string{"archive", "drive"}},
		{"", nil},
	}
	for _, c := range cases {
		if got := parseUpgradeSources(c.in); !reflect.DeepEqual(got, c.want) {
			t.Errorf("parseUpgradeSources(%q) = %v, want %v", c.in, got, c.want)
		}
	}
}
