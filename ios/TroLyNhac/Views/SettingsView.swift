import SwiftUI

struct SettingsView: View {
    @ObservedObject var repository: SubsonicRepository
    var onLogout: () -> Void
    
    @State private var serverStatus: String = "Đang kiểm tra..."
    @State private var serverStatusColor: Color = .gray
    @State private var isScanning: Bool = false
    @State private var showingLogoutAlert = false
    @State private var showingScanStartedAlert = false
    
    var body: some View {
        NavigationView {
            ZStack {
                Color.appBackground.ignoresSafeArea()
                
                List {
                    // SERVER INFO CARD
                    Section(header: Text("Thông tin kết nối").foregroundColor(.gray).font(.system(size: 11, weight: .bold))) {
                        VStack(alignment: .leading, spacing: 6) {
                            Text("Máy chủ")
                                .font(.system(size: 12, weight: .bold))
                                .foregroundColor(.appPrimary)
                            Text(repository.config.baseUrl)
                                .font(.system(size: 14))
                                .foregroundColor(.white)
                        }
                        .padding(.vertical, 4)
                        
                        VStack(alignment: .leading, spacing: 6) {
                            Text("Tài khoản")
                                .font(.system(size: 12, weight: .bold))
                                .foregroundColor(.appPrimary)
                            Text(repository.config.username)
                                .font(.system(size: 14))
                                .foregroundColor(.white)
                        }
                        .padding(.vertical, 4)
                        
                        HStack {
                            Text("Trạng thái kết nối")
                                .font(.system(size: 14, weight: .medium))
                                .foregroundColor(.white)
                            Spacer()
                            Circle()
                                .fill(serverStatusColor)
                                .frame(width: 8, height: 8)
                            Text(serverStatus)
                                .font(.system(size: 13, weight: .medium))
                                .foregroundColor(serverStatusColor)
                        }
                        .padding(.vertical, 4)
                    }
                    .listRowBackground(Color.appSurface.opacity(0.6))
                    
                    // ADMIN PANEL
                    if repository.isAdmin {
                        Section(header: Text("Bảng điều khiển Admin").foregroundColor(.gray).font(.system(size: 11, weight: .bold))) {
                            HStack {
                                Image(systemName: "crown.fill")
                                    .foregroundColor(.yellow)
                                Text("Quyền quản trị viên")
                                    .font(.system(size: 14, weight: .bold))
                                    .foregroundColor(.white)
                                Spacer()
                                Text("Có")
                                    .font(.system(size: 13))
                                    .foregroundColor(.gray)
                            }
                            .padding(.vertical, 4)
                            
                            Button(action: triggerScan) {
                                HStack {
                                    if isScanning {
                                        ProgressView()
                                            .progressViewStyle(CircularProgressViewStyle(tint: .appPrimary))
                                            .padding(.trailing, 8)
                                    } else {
                                        Image(systemName: "arrow.clockwise.icloud.fill")
                                            .foregroundColor(.appPrimary)
                                    }
                                    
                                    Text(isScanning ? "Đang yêu cầu quét..." : "Quét lại toàn bộ thư viện")
                                        .font(.system(size: 14, weight: .semibold))
                                        .foregroundColor(.white)
                                }
                            }
                            .disabled(isScanning)
                            .padding(.vertical, 4)
                        }
                        .listRowBackground(Color.appSurface.opacity(0.6))
                    }
                    
                    // APP DETAILS & VERSION
                    Section(header: Text("Ứng dụng").foregroundColor(.gray).font(.system(size: 11, weight: .bold))) {
                        HStack {
                            Text("Phiên bản")
                                .font(.system(size: 14))
                                .foregroundColor(.white)
                            Spacer()
                            Text("1.0.0 (TroLyNhac)")
                                .font(.system(size: 13))
                                .foregroundColor(.gray)
                        }
                        .padding(.vertical, 4)
                        
                        Button(action: { showingLogoutAlert = true }) {
                            HStack {
                                Image(systemName: "power")
                                    .foregroundColor(.red)
                                Text("Đăng xuất / Ngắt kết nối")
                                    .font(.system(size: 14, weight: .bold))
                                    .foregroundColor(.red)
                            }
                        }
                        .padding(.vertical, 4)
                    }
                    .listRowBackground(Color.appSurface.opacity(0.6))
                }
                .listStyle(InsetGroupedListStyle())
                .hideListBackground() // SwiftUI iOS 15+ backwards compatible hide default list background
            }
            .navigationTitle("Cài đặt")
            .navigationBarTitleDisplayMode(.inline)
            .onAppear {
                checkConnection()
            }
            .alert("Đăng xuất", isPresented: $showingLogoutAlert) {
                Button("Huỷ", role: .cancel) {}
                Button("Xác nhận", role: .destructive) {
                    repository.clearConfig()
                    onLogout()
                }
            } message: {
                Text("Bạn có chắc chắn muốn ngắt kết nối khỏi máy chủ Navidrome hiện tại không? Mọi thông tin tài khoản sẽ bị xoá.")
            }
            .alert("Thông báo", isPresented: $showingScanStartedAlert) {
                Button("Đồng ý", role: .cancel) {}
            } message: {
                Text("Tiến trình quét lại nhạc đã được kích hoạt thành công trên máy chủ! Thư viện nhạc mới sẽ tự động cập nhật sau vài phút.")
            }
        }
        .navigationViewStyle(StackNavigationViewStyle())
    }
    
    private func checkConnection() {
        Task {
            let ok = await repository.ping()
            await MainActor.run {
                if ok {
                    serverStatus = "Trực tuyến (Online)"
                    serverStatusColor = .appPrimary
                } else {
                    serverStatus = "Ngoại tuyến (Offline)"
                    serverStatusColor = .red
                }
            }
        }
    }
    
    private func triggerScan() {
        guard !isScanning else { return }
        
        isScanning = true
        Task {
            do {
                let success = try await repository.triggerLibraryScan()
                await MainActor.run {
                    isScanning = false
                    if success {
                        showingScanStartedAlert = true
                    }
                }
            } catch {
                print("Failed to start scan: \(error)")
                await MainActor.run {
                    isScanning = false
                }
            }
        }
    }
}
