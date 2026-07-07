package scanner_test

import (
	"context"

	"github.com/vi2play/vi2play/conf/configtest"
	"github.com/vi2play/vi2play/consts"
	"github.com/vi2play/vi2play/core"
	"github.com/vi2play/vi2play/core/artwork"
	"github.com/vi2play/vi2play/core/metrics"
	"github.com/vi2play/vi2play/core/playlists"
	"github.com/vi2play/vi2play/db"
	"github.com/vi2play/vi2play/model"
	"github.com/vi2play/vi2play/persistence"
	"github.com/vi2play/vi2play/scanner"
	"github.com/vi2play/vi2play/server/events"
	"github.com/vi2play/vi2play/tests"
	. "github.com/onsi/ginkgo/v2"
	. "github.com/onsi/gomega"
)

var _ = Describe("Controller", func() {
	var ctx context.Context
	var ds *tests.MockDataStore
	var ctrl model.Scanner

	Describe("Status", func() {
		BeforeEach(func() {
			ctx = context.Background()
			db.Init(ctx)
			DeferCleanup(func() { Expect(tests.ClearDB()).To(Succeed()) })
			DeferCleanup(configtest.SetupConfig())
			ds = &tests.MockDataStore{RealDS: persistence.New(db.Db())}
			ds.MockedProperty = &tests.MockedPropertyRepo{}
			ctrl = scanner.New(ctx, ds, artwork.NoopCacheWarmer(), events.NoopBroker(), playlists.NewPlaylists(ds, core.NewImageUploadService()), metrics.NewNoopInstance())
		})

		It("includes last scan error", func() {
			Expect(ds.Property(ctx).Put(consts.LastScanErrorKey, "boom")).To(Succeed())
			status, err := ctrl.Status(ctx)
			Expect(err).ToNot(HaveOccurred())
			Expect(status.LastError).To(Equal("boom"))
		})

		It("includes scan type and error in status", func() {
			// Set up test data in property repo
			Expect(ds.Property(ctx).Put(consts.LastScanErrorKey, "test error")).To(Succeed())
			Expect(ds.Property(ctx).Put(consts.LastScanTypeKey, "full")).To(Succeed())

			// Get status and verify basic info
			status, err := ctrl.Status(ctx)
			Expect(err).ToNot(HaveOccurred())
			Expect(status.LastError).To(Equal("test error"))
			Expect(status.ScanType).To(Equal("full"))
		})
	})
})
