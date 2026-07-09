import Foundation
import CryptoKit

public struct ServerConfig: Codable, Equatable {
    public var baseUrl: String = "https://ms.troly.me"
    public var username: String = ""
    public var password: String = ""
    public var clientName: String = "TroLyNhac"
    public var apiVersion: String = "1.16.1"
    
    public init(baseUrl: String = "https://ms.troly.me", username: String = "", password: String = "", clientName: String = "TroLyNhac", apiVersion: String = "1.16.1") {
        self.baseUrl = baseUrl
        self.username = username
        self.password = password
        self.clientName = clientName
        self.apiVersion = apiVersion
    }
    
    public init() {}
}

public class SubsonicAuth {
    public static func salt() -> String {
        let bytes = (0..<8).map { _ in UInt8.random(in: 0...255) }
        return bytes.map { String(format: "%02x", $0) }.joined()
    }
    
    public static func token(password: String, salt: String) -> String {
        let input = password + salt
        let digest = Insecure.MD5.hash(data: Data(input.utf8))
        return digest.map { String(format: "%02x", $0) }.joined()
    }
}

public class SubsonicRepository: ObservableObject {
    @Published var config: ServerConfig
    @Published var isAdmin: Bool = false
    @Published var isConfigured: Bool = false
    
    private let jsonDecoder: JSONDecoder = {
        let decoder = JSONDecoder()
        decoder.keyDecodingStrategy = .useDefaultKeys
        return decoder
    }()
    
    private var cachedSsoToken: String? = nil
    
    public init() {
        self.config = ServerConfig()
        if let data = UserDefaults.standard.data(forKey: "trolynhac_config"),
           let savedConfig = try? JSONDecoder().decode(ServerConfig.self, from: data) {
            self.config = savedConfig
            self.isConfigured = true
            
            // Check admin status in the background
            Task {
                _ = await checkAdminStatus()
            }
        }
    }
    
    public func saveConfig(_ newConfig: ServerConfig) {
        if let data = try? JSONEncoder().encode(newConfig) {
            UserDefaults.standard.set(data, forKey: "trolynhac_config")
            self.config = newConfig
            self.isConfigured = true
            self.cachedSsoToken = nil
            self.isAdmin = false
            
            Task {
                _ = await checkAdminStatus()
            }
        }
    }
    
    public func clearConfig() {
        UserDefaults.standard.removeObject(forKey: "trolynhac_config")
        self.config = ServerConfig()
        self.isConfigured = false
        self.cachedSsoToken = nil
        self.isAdmin = false
    }
    
    // ── AUTHENTICATION QUERY & STREAM URLS ──────────────────────────────────
    
    private func authQueryParams() -> [URLQueryItem] {
        let s = SubsonicAuth.salt()
        let t = SubsonicAuth.token(password: config.password, salt: s)
        return [
            URLQueryItem(name: "u", value: config.username),
            URLQueryItem(name: "t", value: t),
            URLQueryItem(name: "s", value: s),
            URLQueryItem(name: "v", value: config.apiVersion),
            URLQueryItem(name: "c", value: config.clientName),
            URLQueryItem(name: "f", value: "json")
        ]
    }
    
    public func streamUrl(songId: String, format: String? = nil) -> URL? {
        var components = URLComponents(string: config.baseUrl.trimmingCharacters(in: CharacterSet(charactersIn: "/")) + "/rest/stream.view")
        var queryItems = authQueryParams()
        queryItems.append(URLQueryItem(name: "id", value: songId))
        if let format = format, !format.isEmpty {
            queryItems.append(URLQueryItem(name: "format", value: format))
        }
        components?.queryItems = queryItems
        return components?.url
    }
    
    public func coverArtUrl(coverArtId: String?, size: Int = 300) -> URL? {
        guard let coverArtId = coverArtId else { return nil }
        var components = URLComponents(string: config.baseUrl.trimmingCharacters(in: CharacterSet(charactersIn: "/")) + "/rest/getCoverArt.view")
        var queryItems = authQueryParams()
        queryItems.append(URLQueryItem(name: "id", value: coverArtId))
        queryItems.append(URLQueryItem(name: "size", value: String(size)))
        components?.queryItems = queryItems
        return components?.url
    }
    
    public func isServerTranscodeSuffix(_ suffix: String?) -> Bool {
        guard let suffix = suffix?.lowercased() else { return false }
        return ["dsf", "dff", "dsd", "aif", "aiff"].contains(suffix)
    }
    
    // ── HTTP HELPER ────────────────────────────────────────────────────────
    
    private func performRequest<T: Codable>(endpoint: String, queryParams: [URLQueryItem] = []) async throws -> T {
        var components = URLComponents(string: config.baseUrl.trimmingCharacters(in: CharacterSet(charactersIn: "/")) + "/" + endpoint)
        var allQuery = authQueryParams()
        allQuery.append(contentsOf: queryParams)
        components?.queryItems = allQuery
        
        guard let url = components?.url else {
            throw URLError(.badURL)
        }
        
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        
        let (data, response) = try await URLSession.shared.data(for: request)
        
        guard let httpResponse = response as? HTTPURLResponse, (200...299).contains(httpResponse.statusCode) else {
            throw URLError(.badServerResponse)
        }
        
        return try jsonDecoder.decode(T.self, from: data)
    }
    
    private func extractBody(_ response: SubsonicResponse) throws -> SubsonicBody {
        guard let body = response.response else {
            throw SubsonicError(code: -1, message: "Phản hồi rỗng")
        }
        if body.status != "ok" {
            throw SubsonicError(code: body.error?.code ?? -1, message: body.error?.message ?? "Máy chủ trả lỗi")
        }
        return body
    }
    
    // ── PUBLIC SUBSONIC API METHODS ──────────────────────────────────────────
    
    public func ping() async -> Bool {
        do {
            let res: SubsonicResponse = try await performRequest(endpoint: "rest/ping.view")
            return res.response?.status == "ok"
        } catch {
            return false
        }
    }
    
    public func fetchAlbums(type: String, size: Int = 40) async throws -> [Album] {
        let params = [
            URLQueryItem(name: "type", value: type),
            URLQueryItem(name: "size", value: String(size))
        ]
        let res: SubsonicResponse = try await performRequest(endpoint: "rest/getAlbumList2.view", queryParams: params)
        let body = try extractBody(res)
        return body.albumList2?.album ?? []
    }
    
    public func fetchAlbum(id: String) async throws -> Album {
        let params = [URLQueryItem(name: "id", value: id)]
        let res: SubsonicResponse = try await performRequest(endpoint: "rest/getAlbum.view", queryParams: params)
        let body = try extractBody(res)
        guard let album = body.album else {
            throw SubsonicError(code: -1, message: "Không tìm thấy thông tin album")
        }
        return album
    }
    
    public func fetchArtist(id: String) async throws -> Artist {
        let params = [URLQueryItem(name: "id", value: id)]
        let res: SubsonicResponse = try await performRequest(endpoint: "rest/getArtist.view", queryParams: params)
        let body = try extractBody(res)
        guard let artist = body.artist else {
            throw SubsonicError(code: -1, message: "Không tìm thấy thông tin nghệ sĩ")
        }
        return artist
    }
    
    public func fetchArtists() async throws -> [Artist] {
        let res: SubsonicResponse = try await performRequest(endpoint: "rest/getArtists.view")
        let body = try extractBody(res)
        return body.artists?.index?.flatMap { $0.artist ?? [] } ?? []
    }
    
    public func fetchPlaylists() async throws -> [Playlist] {
        let res: SubsonicResponse = try await performRequest(endpoint: "rest/getPlaylists.view")
        let body = try extractBody(res)
        return body.playlists?.playlist ?? []
    }
    
    public func fetchPlaylist(id: String) async throws -> Playlist {
        let params = [URLQueryItem(name: "id", value: id)]
        let res: SubsonicResponse = try await performRequest(endpoint: "rest/getPlaylist.view", queryParams: params)
        let body = try extractBody(res)
        guard let playlist = body.playlist else {
            throw SubsonicError(code: -1, message: "Không tìm thấy danh sách phát")
        }
        return playlist
    }
    
    public func fetchStarredSongs() async throws -> [Song] {
        let res: SubsonicResponse = try await performRequest(endpoint: "rest/getStarred2.view")
        let body = try extractBody(res)
        return body.starred2?.song ?? []
    }
    
    public func search(query: String) async throws -> SearchResult3 {
        let params = [
            URLQueryItem(name: "query", value: query),
            URLQueryItem(name: "songCount", value: "30"),
            URLQueryItem(name: "albumCount", value: "20"),
            URLQueryItem(name: "artistCount", value: "20")
        ]
        let res: SubsonicResponse = try await performRequest(endpoint: "rest/search3.view", queryParams: params)
        let body = try extractBody(res)
        return body.searchResult3 ?? SearchResult3(artist: [], album: [], song: [])
    }
    
    public func createPlaylist(name: String, songIds: [String]) async throws {
        var params = [URLQueryItem(name: "name", value: name)]
        for id in songIds {
            params.append(URLQueryItem(name: "songId", value: id))
        }
        let res: SubsonicResponse = try await performRequest(endpoint: "rest/createPlaylist.view", queryParams: params)
        _ = try extractBody(res)
    }
    
    public func addToPlaylist(playlistId: String, songIds: [String]) async throws {
        var params = [URLQueryItem(name: "playlistId", value: playlistId)]
        for id in songIds {
            params.append(URLQueryItem(name: "songIdToAdd", value: id))
        }
        let res: SubsonicResponse = try await performRequest(endpoint: "rest/updatePlaylist.view", queryParams: params)
        _ = try extractBody(res)
    }
    
    public func deletePlaylist(id: String) async throws {
        let params = [URLQueryItem(name: "id", value: id)]
        let res: SubsonicResponse = try await performRequest(endpoint: "rest/deletePlaylist.view", queryParams: params)
        _ = try extractBody(res)
    }
    
    // ── NATIVE API (SSO & ADMIN TOOLS) ───────────────────────────────────────
    
    public func getSsoToken() async throws -> String {
        if let token = cachedSsoToken { return token }
        
        let salt = SubsonicAuth.salt()
        let token = SubsonicAuth.token(password: config.password, salt: salt)
        
        let requestBody = SsoRequest(username: config.username, token: token, salt: salt)
        
        let url = URL(string: config.baseUrl.trimmingCharacters(in: CharacterSet(charactersIn: "/")) + "/auth/sso/subsonic")!
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.addValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONEncoder().encode(requestBody)
        
        let (data, response) = try await URLSession.shared.data(for: request)
        
        guard let httpResponse = response as? HTTPURLResponse, (200...299).contains(httpResponse.statusCode) else {
            throw URLError(.badServerResponse)
        }
        
        let ssoResponse = try jsonDecoder.decode(SsoResponse.self, from: data)
        
        await MainActor.run {
            self.isAdmin = ssoResponse.isAdmin
        }
        
        self.cachedSsoToken = ssoResponse.token
        return ssoResponse.token
    }
    
    public func checkAdminStatus() async -> Bool {
        do {
            _ = try await getSsoToken()
            return self.isAdmin
        } catch {
            return false
        }
    }
    
    public func triggerLibraryScan() async throws -> Bool {
        let sso = try await getSsoToken()
        let url = URL(string: config.baseUrl.trimmingCharacters(in: CharacterSet(charactersIn: "/")) + "/api/import/scan")!
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.addValue("Bearer \(sso)", forHTTPHeaderField: "X-VI-Authorization")
        
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse, (200...299).contains(httpResponse.statusCode) else {
            return false
        }
        
        let scanRes = try jsonDecoder.decode(ScanResponse.self, from: data)
        return scanRes.status == "scan_started"
    }
    
    public func deleteSong(id: String) async throws -> Bool {
        let sso = try await getSsoToken()
        let url = URL(string: config.baseUrl.trimmingCharacters(in: CharacterSet(charactersIn: "/")) + "/api/song/\(id)")!
        var request = URLRequest(url: url)
        request.httpMethod = "DELETE"
        request.addValue("Bearer \(sso)", forHTTPHeaderField: "X-VI-Authorization")
        
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse, (200...299).contains(httpResponse.statusCode) else {
            return false
        }
        
        let delRes = try jsonDecoder.decode(DeleteResponse.self, from: data)
        return delRes.ids.contains(id)
    }
    
    public func deleteAlbum(id: String) async throws -> Bool {
        let sso = try await getSsoToken()
        let url = URL(string: config.baseUrl.trimmingCharacters(in: CharacterSet(charactersIn: "/")) + "/api/album/\(id)")!
        var request = URLRequest(url: url)
        request.httpMethod = "DELETE"
        request.addValue("Bearer \(sso)", forHTTPHeaderField: "X-VI-Authorization")
        
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse, (200...299).contains(httpResponse.statusCode) else {
            return false
        }
        
        let delRes = try jsonDecoder.decode(DeleteResponse.self, from: data)
        return delRes.ids.contains(id)
    }
    
    public func fetchSimilarSongs(songId: String, count: Int = 20) async throws -> [Song] {
        let params = [
            URLQueryItem(name: "id", value: songId),
            URLQueryItem(name: "count", value: String(count))
        ]
        let res: SubsonicResponse = try await performRequest(endpoint: "rest/getSimilarSongs2.view", queryParams: params)
        let body = try extractBody(res)
        return body.similarSongs2?.song ?? []
    }
    
    public func fetchSongsByArtist(artistId: String, artistName: String) async throws -> [Song] {
        do {
            let artist = try await fetchArtist(id: artistId)
            guard let albums = artist.album, !albums.isEmpty else {
                throw SubsonicError(code: -1, message: "No albums found")
            }
            var allSongs: [Song] = []
            let albumsToFetch = Array(albums.prefix(3))
            for album in albumsToFetch {
                if let fullAlbum = try? await fetchAlbum(id: album.id), let songs = fullAlbum.song {
                    allSongs.append(contentsOf: songs)
                }
            }
            if !allSongs.isEmpty {
                return allSongs
            }
        } catch {
            print("Failed to fetch artist albums, falling back to search: \(error)")
        }
        
        let searchRes = try await search(query: artistName)
        return searchRes.song ?? []
    }
}
