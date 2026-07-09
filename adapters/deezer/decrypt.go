package deezer

import (
	"crypto/md5"
	"fmt"
	"io"

	"golang.org/x/crypto/blowfish"
)

// DeriveBlowfishKey derives the 16-byte Blowfish decryption key for a track.
func DeriveBlowfishKey(trackID string, salt string) []byte {
	if salt == "" {
		salt = "g4c3bch0" // Default known Deezer salt
	}
	hasher := md5.New()
	hasher.Write([]byte(trackID))
	md5Hex := fmt.Sprintf("%x", hasher.Sum(nil))

	key := make([]byte, 16)
	for i := 0; i < 16; i++ {
		key[i] = md5Hex[i] ^ md5Hex[i+16] ^ salt[i%len(salt)]
	}
	return key
}

// DecryptStream reads from an encrypted reader and decrypts the stream on-the-fly.
// It decrypts only the first 2048-byte block of every 6144-byte chunk (every 3rd block).
func DecryptStream(src io.Reader, trackID string, salt string) (io.Reader, error) {
	key := DeriveBlowfishKey(trackID, salt)
	cipher, err := blowfish.NewCipher(key)
	if err != nil {
		return nil, fmt.Errorf("failed to create blowfish cipher: %w", err)
	}

	pr, pw := io.Pipe()

	go func() {
		defer pw.Close()
		buf := make([]byte, 2048)
		chunkIndex := 0

		for {
			n, err := io.ReadFull(src, buf)
			if n > 0 {
				chunk := buf[:n]
				// Only decrypt every third chunk (index 0, 3, 6, ...)
				if chunkIndex%3 == 0 {
					limit := (len(chunk) / 8) * 8
					for i := 0; i < limit; i += 8 {
						cipher.Decrypt(chunk[i:i+8], chunk[i:i+8])
					}
				}
				_, writeErr := pw.Write(chunk)
				if writeErr != nil {
					return
				}
				chunkIndex++
			}

			if err != nil {
				if err != io.EOF && err != io.ErrUnexpectedEOF {
					_ = pw.CloseWithError(err)
				}
				break
			}
		}
	}()

	return pr, nil
}
