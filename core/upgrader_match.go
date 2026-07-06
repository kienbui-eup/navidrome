package core

import (
	"path"
	"regexp"
	"strconv"
	"strings"
	"unicode"

	"github.com/navidrome/navidrome/model"
	"github.com/navidrome/navidrome/utils/gg"
	"github.com/navidrome/navidrome/utils/str"
)

// ---------------------------------------------------------------------------
// Fuzzy title/artist matching. See the "Matching" section of
// docs/superpowers/specs/2026-07-06-quality-upgrader-design.md.
// ---------------------------------------------------------------------------

const (
	// matchDurationWindowSec is the tolerance (seconds) used when comparing a
	// candidate's reported duration against the original track's duration.
	matchDurationWindowSec = 5.0
	// matchDurationBonus is added to the match score when durations line up
	// (only Internet Archive reports a candidate duration today).
	matchDurationBonus = 10
	// matchKeywordPenalty is subtracted, once per offending keyword, when a
	// candidate's title suggests it is a different performance of the track
	// (live/cover/remix/karaoke) and the original title doesn't say so too.
	matchKeywordPenalty = 30
)

// matchPenaltyKeywords are normalized (lowercase, accent-stripped) words that,
// when present only in the candidate's title, indicate a likely mismatch.
var matchPenaltyKeywords = []string{"live", "cover", "remix", "karaoke"}

// normalizeForMatch lowercases, strips diacritics/punctuation (reusing the
// project's existing utils/str helpers so this doesn't add a new dependency)
// and splits the result into tokens for set-based comparison.
func normalizeForMatch(s string) []string {
	// str.SanitizeFieldForSorting trims, strips accents (sanitize.Accents) and
	// lowercases; str.Clear ascii-fies smart quotes/dashes.
	n := str.SanitizeFieldForSorting(s)
	var b strings.Builder
	b.Grow(len(n))
	for _, r := range n {
		if unicode.IsLetter(r) || unicode.IsDigit(r) {
			b.WriteRune(r)
		} else {
			b.WriteRune(' ')
		}
	}
	return strings.Fields(b.String())
}

func tokenSet(tokens []string) map[string]bool {
	set := make(map[string]bool, len(tokens))
	for _, t := range tokens {
		set[t] = true
	}
	return set
}

// tokenSetSimilarity returns a 0-100 score for how similar two free-text
// strings are, based on the Dice coefficient of their normalized token sets:
// 2*|A∩B| / (|A|+|B|). This is the "token-set similarity" used for candidate
// matching in the design doc.
func tokenSetSimilarity(a, b string) int {
	ta := tokenSet(normalizeForMatch(a))
	tb := tokenSet(normalizeForMatch(b))
	if len(ta) == 0 || len(tb) == 0 {
		return 0
	}
	common := 0
	for t := range ta {
		if tb[t] {
			common++
		}
	}
	score := 200 * common / (len(ta) + len(tb))
	return clampScore(score)
}

// keywordPenalty returns the total penalty for keywords (live/cover/remix/
// karaoke) that appear in candidate but not in original.
func keywordPenalty(original, candidate string) int {
	on := tokenSet(normalizeForMatch(original))
	cn := tokenSet(normalizeForMatch(candidate))
	penalty := 0
	for _, kw := range matchPenaltyKeywords {
		if cn[kw] && !on[kw] {
			penalty += matchKeywordPenalty
		}
	}
	return penalty
}

// matchScore computes the 0-100 fuzzy match score between the original
// track's "artist title" and a candidate's display title: token-set
// similarity, +bonus when duration is known and within
// matchDurationWindowSec, -penalty when the candidate's title suggests a
// live/cover/remix/karaoke version that the original isn't.
//
// hasDuration/durationDeltaSec must only be set to true/non-zero when the
// source actually reports a duration for the candidate (today, only Internet
// Archive does; Drive/RSS candidates should pass hasDuration=false).
func matchScore(originalArtist, originalTitle, candidateTitle string, hasDuration bool, durationDeltaSec float64) int {
	original := strings.TrimSpace(originalArtist + " " + originalTitle)
	score := tokenSetSimilarity(original, candidateTitle)
	if hasDuration && absFloat(durationDeltaSec) <= matchDurationWindowSec {
		score += matchDurationBonus
	}
	score -= keywordPenalty(original, candidateTitle)
	return clampScore(score)
}

func absFloat(f float64) float64 {
	if f < 0 {
		return -f
	}
	return f
}

func clampScore(s int) int {
	if s < 0 {
		return 0
	}
	if s > 100 {
		return 100
	}
	return s
}

// displayTitle picks the best human-readable label for a found candidate,
// preferring the source's own title field over the raw filename.
func displayTitle(title, filename string) string {
	if strings.TrimSpace(title) != "" {
		return title
	}
	base := path.Base(strings.Split(filename, "?")[0])
	return strings.TrimSuffix(base, path.Ext(base))
}

// parseIADuration parses Internet Archive's file "length" field, which may be
// either plain seconds ("245.67") or a "mm:ss" / "h:mm:ss" timestamp, into
// seconds. Returns ok=false when the value can't be parsed (or is empty) so
// callers know no duration is available for the ±5s matching bonus.
func parseIADuration(s string) (seconds float64, ok bool) {
	s = strings.TrimSpace(s)
	if s == "" {
		return 0, false
	}
	if !strings.Contains(s, ":") {
		f, err := strconv.ParseFloat(s, 64)
		if err != nil {
			return 0, false
		}
		return f, true
	}
	parts := strings.Split(s, ":")
	var total float64
	for _, p := range parts {
		f, err := strconv.ParseFloat(p, 64)
		if err != nil {
			return 0, false
		}
		total = total*60 + f
	}
	return total, true
}

// ---------------------------------------------------------------------------
// Stage-1 quality comparison (before download). See "Chấm điểm chất lượng —
// giai đoạn 1" in the design doc.
// ---------------------------------------------------------------------------

// qualityTier captures the quality signals available before a candidate is
// downloaded, compared against the original track's actual quality.
type qualityTier struct {
	Lossless    bool
	BitRateKbps int // 0 = unknown
	SampleRate  int // Hz, 0 = unknown
	BitDepth    int // 0 = unknown
}

// losslessCodecs mirrors the codec vocabulary returned by
// model.MediaFile.AudioCodec() (see model/mediafile.go's
// inferCodecFromSuffix): every codec not in this set is treated as lossy for
// upgrade-scanning purposes.
var losslessCodecs = map[string]bool{
	"flac": true,
	"alac": true,
	"pcm":  true, // wav, aiff
	"ape":  true,
	"wv":   true,
	"tta":  true,
	"tak":  true,
	"shn":  true,
	"dsd":  true, // dsf, dff
}

func isLosslessCodec(codec string) bool {
	return losslessCodecs[strings.ToLower(codec)]
}

// losslessExtensions classifies a candidate known only by filename (Drive,
// RSS, and Internet Archive's file listing), where there's no decoded codec,
// only a file extension. Deliberately excludes "m4a" (ambiguous AAC/ALAC
// container) so such candidates are conservatively treated as lossy, same as
// the "Track có thể nâng cấp" rule for tracks already in the library.
var losslessExtensions = map[string]bool{
	"flac": true,
	"alac": true,
	"wav":  true,
	"aiff": true,
	"aif":  true,
	"ape":  true,
	"shn":  true,
	"dsf":  true,
	"dff":  true,
	"wv":   true,
	"wvp":  true,
	"tak":  true,
	"tta":  true,
}

// extOf returns the lowercase file extension (no leading dot, no query
// string) of name.
func extOf(name string) string {
	name = strings.Split(name, "?")[0]
	return strings.ToLower(strings.TrimPrefix(path.Ext(name), "."))
}

// originalQuality builds the quality tier of the media file currently in the
// library.
func originalQuality(mf model.MediaFile) qualityTier {
	return qualityTier{
		Lossless:    isLosslessCodec(mf.AudioCodec()),
		BitRateKbps: mf.BitRate,
		SampleRate:  mf.SampleRate,
		BitDepth:    gg.V(mf.BitDepth),
	}
}

// Best-effort regexes for pulling quality hints out of Internet Archive's
// free-text ArchiveFile.Format field (e.g. "VBR MP3", "24bit Flac",
// "44100Hz"). ArchiveFile has no dedicated bitrate/sample-rate/bit-depth
// field, so ranking degrades to extOf-based lossless/lossy class only when
// these don't match, same as Drive/RSS.
var (
	reKbps      = regexp.MustCompile(`(?i)(\d+)\s*k(?:b(?:it)?)?ps`)
	reSampleKHz = regexp.MustCompile(`(?i)(\d+(?:\.\d+)?)\s*khz`)
	reSampleHz  = regexp.MustCompile(`(?i)(\d+)\s*hz`)
	reBitDepth  = regexp.MustCompile(`(?i)(\d+)\s*-?\s*bit`)
)

func parseKbps(s string) int {
	m := reKbps.FindStringSubmatch(s)
	if m == nil {
		return 0
	}
	n, _ := strconv.Atoi(m[1])
	return n
}

func parseSampleRateHz(s string) int {
	if m := reSampleKHz.FindStringSubmatch(s); m != nil {
		f, _ := strconv.ParseFloat(m[1], 64)
		return int(f * 1000)
	}
	if m := reSampleHz.FindStringSubmatch(s); m != nil {
		n, _ := strconv.Atoi(m[1])
		return n
	}
	return 0
}

func parseBitDepth(s string) int {
	m := reBitDepth.FindStringSubmatch(s)
	if m == nil {
		return 0
	}
	n, _ := strconv.Atoi(m[1])
	return n
}

// estimateQuality builds the best-effort quality tier for a candidate known
// only by filename plus an optional free-text format hint (only Internet
// Archive supplies one, via ArchiveFile.Format). Drive and RSS never provide
// a format hint, so their candidates are ranked by extension class alone,
// with est_bitrate/sample-rate/bit-depth left at 0 (unknown) — true bitrate
// is only verified after download (stage 2, out of Phase 2 scope).
func estimateQuality(name, formatHint string) qualityTier {
	return qualityTier{
		Lossless:    losslessExtensions[extOf(name)],
		BitRateKbps: parseKbps(formatHint),
		SampleRate:  parseSampleRateHz(formatHint),
		BitDepth:    parseBitDepth(formatHint),
	}
}

// compareQualityTier compares two quality tiers per the design doc's stage-1
// rule: compare tiers in order (lossless-class, then bitrate, then sample
// rate, then bit depth); the first tier with a *known* difference on both
// sides decides the result. A tier that is unknown (<=0) on either side is
// skipped rather than counted as a loss: Drive/RSS and most Internet Archive
// listings don't declare bitrate/sample-rate/bit-depth, and the design doc
// says that case is ranked by extension class alone (real bitrate is
// verified after download, in stage 2).
//
// Returns >0 if a is strictly better, <0 if b is strictly better, 0 if tied
// (including "not comparable").
func compareQualityTier(a, b qualityTier) int {
	if a.Lossless != b.Lossless {
		if a.Lossless {
			return 1
		}
		return -1
	}
	if r := compareKnown(a.BitRateKbps, b.BitRateKbps); r != 0 {
		return r
	}
	if r := compareKnown(a.SampleRate, b.SampleRate); r != 0 {
		return r
	}
	return compareKnown(a.BitDepth, b.BitDepth)
}

// compareKnown compares a and b, treating <=0 (unknown) on either side as
// "not comparable" (returns 0) rather than as a loss.
func compareKnown(a, b int) int {
	if a <= 0 || b <= 0 {
		return 0
	}
	switch {
	case a < b:
		return -1
	case a > b:
		return 1
	default:
		return 0
	}
}

// candidateWins reports whether candidate strictly beats original under the
// stage-1 quality rule — the gate used to decide whether a matched candidate
// is even worth queuing for admin review.
func candidateWins(original, candidate qualityTier) bool {
	return compareQualityTier(original, candidate) < 0
}
