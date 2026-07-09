package deezer

import (
	"bytes"
	"crypto/rand"
	"io"
	"testing"

	"golang.org/x/crypto/blowfish"
)

func TestDeriveBlowfishKey(t *testing.T) {
	trackID := "987654321"
	salt := "g4c3bch0"
	key := DeriveBlowfishKey(trackID, salt)

	if len(key) != 16 {
		t.Fatalf("Expected key length 16, got %d", len(key))
	}

	// Verify repeatable derivation
	key2 := DeriveBlowfishKey(trackID, salt)
	if !bytes.Equal(key, key2) {
		t.Error("Derivation is not repeatable")
	}
}

func TestDecryptStream(t *testing.T) {
	trackID := "123456"
	salt := "g4c3bch0"

	// Create random test data of 5000 bytes (about 2.5 chunks)
	originalData := make([]byte, 5000)
	_, err := rand.Read(originalData)
	if err != nil {
		t.Fatalf("failed to generate random data: %v", err)
	}

	// Encrypt using the same formula
	key := DeriveBlowfishKey(trackID, salt)
	cipher, err := blowfish.NewCipher(key)
	if err != nil {
		t.Fatalf("failed to create blowfish cipher: %v", err)
	}

	encryptedData := make([]byte, len(originalData))
	copy(encryptedData, originalData)

	for chunkIndex := 0; chunkIndex*2048 < len(originalData); chunkIndex++ {
		if chunkIndex%3 == 0 {
			start := chunkIndex * 2048
			end := start + 2048
			if end > len(originalData) {
				end = len(originalData)
			}
			chunk := encryptedData[start:end]
			limit := (len(chunk) / 8) * 8
			for i := 0; i < limit; i += 8 {
				cipher.Encrypt(chunk[i:i+8], chunk[i:i+8])
			}
		}
	}

	// Decrypt on-the-fly
	decryptedReader, err := DecryptStream(bytes.NewReader(encryptedData), trackID, salt)
	if err != nil {
		t.Fatalf("DecryptStream failed to initialize: %v", err)
	}

	decryptedData, err := io.ReadAll(decryptedReader)
	if err != nil {
		t.Fatalf("Failed to read decrypted stream: %v", err)
	}

	if !bytes.Equal(originalData, decryptedData) {
		t.Error("Decrypted data does not match the original plaintext data")
	}
}
