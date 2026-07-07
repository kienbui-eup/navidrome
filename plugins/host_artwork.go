package plugins

import (
	"context"

	"github.com/vi2play/vi2play/core/publicurl"
	"github.com/vi2play/vi2play/model"
	"github.com/vi2play/vi2play/plugins/host"
)

type artworkServiceImpl struct{}

func newArtworkService() host.ArtworkService {
	return &artworkServiceImpl{}
}

func (a *artworkServiceImpl) GetArtistUrl(_ context.Context, id string, size int32) (string, error) {
	artID := model.ArtworkID{Kind: model.KindArtistArtwork, ID: id}
	return publicurl.ImageURL(nil, artID, int(size)), nil
}

func (a *artworkServiceImpl) GetAlbumUrl(_ context.Context, id string, size int32) (string, error) {
	artID := model.ArtworkID{Kind: model.KindAlbumArtwork, ID: id}
	return publicurl.ImageURL(nil, artID, int(size)), nil
}

func (a *artworkServiceImpl) GetTrackUrl(_ context.Context, id string, size int32) (string, error) {
	artID := model.ArtworkID{Kind: model.KindMediaFileArtwork, ID: id}
	return publicurl.ImageURL(nil, artID, int(size)), nil
}

func (a *artworkServiceImpl) GetPlaylistUrl(_ context.Context, id string, size int32) (string, error) {
	artID := model.ArtworkID{Kind: model.KindPlaylistArtwork, ID: id}
	return publicurl.ImageURL(nil, artID, int(size)), nil
}

var _ host.ArtworkService = (*artworkServiceImpl)(nil)
