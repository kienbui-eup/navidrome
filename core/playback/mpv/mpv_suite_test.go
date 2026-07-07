package mpv

import (
	"testing"

	"github.com/vi2play/vi2play/log"
	"github.com/vi2play/vi2play/tests"
	. "github.com/onsi/ginkgo/v2"
	. "github.com/onsi/gomega"
)

func TestMPV(t *testing.T) {
	tests.Init(t, false)
	log.SetLevel(log.LevelFatal)
	RegisterFailHandler(Fail)
	RunSpecs(t, "MPV Suite")
}
