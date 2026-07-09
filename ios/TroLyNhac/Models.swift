import Foundation

// ── SUBSONIC RESPONSE MODELS ───────────────────────────────────────────────

public struct SubsonicResponse: Codable {
    public let response: SubsonicBody?
    
    enum CodingKeys: String, CodingKey {
        case response = "subsonic-response"
    }
}

public struct SubsonicBody: Codable {
    public let status: String
    public let version: String
    public let albumList2: AlbumList2?
    public let album: Album?
    public let artist: Artist?
    public let artists: ArtistsRoot?
    public let playlists: PlaylistsRoot?
    public let playlist: Playlist?
    public let starred2: Starred2?
    public let searchResult3: SearchResult3?
    public let similarSongs2: SimilarSongs2?
    public let error: SubsonicError?
}

public struct SubsonicError: Codable, Error {
    public let code: Int
    public let message: String
}

public struct AlbumList2: Codable {
    public let album: [Album]?
}

public struct ArtistsRoot: Codable {
    public let index: [ArtistIndex]?
}

public struct ArtistIndex: Codable {
    public let name: String
    public let artist: [Artist]?
}

public struct PlaylistsRoot: Codable {
    public let playlist: [Playlist]?
}

public struct Starred2: Codable {
    public let album: [Album]?
    public let song: [Song]?
}

public struct SearchResult3: Codable {
    public let artist: [Artist]?
    public let album: [Album]?
    public let song: [Song]?
}

// ── ENTITY MODELS ──────────────────────────────────────────────────────────

public struct Album: Codable, Identifiable, Hashable {
    public let id: String
    public let name: String
    public let artist: String?
    public let artistId: String?
    public let year: Int?
    public let coverArt: String?
    public let songCount: Int?
    public let duration: Int?
    public let song: [Song]? // populated by getAlbum
    
    public func hash(into hasher: inout Hasher) {
        hasher.combine(id)
    }
    
    public static func == (lhs: Album, rhs: Album) -> Bool {
        lhs.id == rhs.id
    }
}

public struct Artist: Codable, Identifiable, Hashable {
    public let id: String
    public let name: String
    public let coverArt: String?
    public let albumCount: Int?
    public let album: [Album]? // populated by getArtist
    
    public func hash(into hasher: inout Hasher) {
        hasher.combine(id)
    }
    
    public static func == (lhs: Artist, rhs: Artist) -> Bool {
        lhs.id == rhs.id
    }
}

public struct Playlist: Codable, Identifiable, Hashable {
    public let id: String
    public let name: String
    public let coverArt: String?
    public let songCount: Int?
    public let entry: [Song]?
    
    public func hash(into hasher: inout Hasher) {
        hasher.combine(id)
    }
    
    public static func == (lhs: Playlist, rhs: Playlist) -> Bool {
        lhs.id == rhs.id
    }
}

public struct Song: Codable, Identifiable, Hashable {
    public let id: String
    public let title: String
    public let album: String?
    public let albumId: String?
    public let artist: String?
    public let artistId: String?
    public let track: Int?
    public let year: Int?
    public let duration: Int? // in seconds
    public let coverArt: String?
    public let suffix: String?
    public let bitRate: Int?
    public let bitDepth: Int?
    public let samplingRate: Int?
    
    public func hash(into hasher: inout Hasher) {
        hasher.combine(id)
    }
    
    public static func == (lhs: Song, rhs: Song) -> Bool {
        lhs.id == rhs.id
    }
}

// ── NATIVE API SSO & ADMIN MODELS ──────────────────────────────────────────

public struct SsoRequest: Codable {
    public let username: String
    public let token: String
    public let salt: String
}

public struct SsoResponse: Codable {
    public let id: String
    public let name: String
    public let username: String
    public let isAdmin: Bool
    public let token: String
}

public struct ScanResponse: Codable {
    public let status: String
}

public struct DeleteResponse: Codable {
    public let ids: [String]
}

public struct SimilarSongs2: Codable {
    public let song: [Song]?
}
