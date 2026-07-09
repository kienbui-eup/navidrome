import SwiftUI

struct LoginView: View {
    @ObservedObject var repository: SubsonicRepository
    
    @State private var baseUrl: String = "https://ms.troly.me"
    @State private var username: String = ""
    @State private var password: String = ""
    @State private var isLoading: Bool = false
    @State private var errorMessage: String? = nil
    @State private var animateGlow: Bool = false
    
    var onConnected: (ServerConfig) -> Void
    
    var body: some View {
        ZStack {
            // Ambient Backdrop Gradients
            Color.appBackground.ignoresSafeArea()
            
            // Glowing blurred background shapes
            ZStack {
                Circle()
                    .fill(Color.appPrimary.opacity(0.15))
                    .frame(width: 300, height: 300)
                    .blur(radius: 60)
                    .offset(x: -100, y: -150)
                    .scaleEffect(animateGlow ? 1.2 : 0.8)
                
                Circle()
                    .fill(Color.blue.opacity(0.1))
                    .frame(width: 250, height: 250)
                    .blur(radius: 50)
                    .offset(x: 120, y: 150)
                    .scaleEffect(animateGlow ? 0.9 : 1.1)
            }
            .onAppear {
                withAnimation(.easeInOut(duration: 4.0).repeatForever(autoreverses: true)) {
                    animateGlow = true
                }
            }
            
            ScrollView {
                VStack(spacing: 30) {
                    Spacer().frame(height: 50)
                    
                    // App Logo Symbol
                    VStack(spacing: 12) {
                        ZStack {
                            Circle()
                                .fill(Color.appPrimary.opacity(0.1))
                                .frame(width: 90, height: 90)
                            
                            Circle()
                                .stroke(Color.appPrimary.opacity(0.3), lineWidth: 1.5)
                                .frame(width: 90, height: 90)
                                .scaleEffect(animateGlow ? 1.05 : 0.95)
                            
                            Image("brand_logo")
                                .resizable()
                                .scaledToFit()
                                .frame(width: 64, height: 64)
                                .clipShape(Circle())
                                .shadow(color: .appPrimary.opacity(0.4), radius: 10, x: 0, y: 0)
                        }
                        
                        Text("Vi2Play")
                            .font(.system(size: 32, weight: .bold, design: .rounded))
                            .foregroundColor(.white)
                            .tracking(2.0)
                        
                        Text("VIP PLAY • VIỆT PLAYER")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundColor(.appPrimary)
                            .tracking(1.5)
                    }
                    
                    // Glassmorphic Input Card
                    VStack(spacing: 20) {
                        VStack(alignment: .leading, spacing: 8) {
                            Text("ĐỊA CHỈ MÁY CHỦ")
                                .font(.system(size: 10, weight: .bold))
                                .foregroundColor(.appPrimary)
                                .tracking(1)
                            
                            HStack {
                                Image(systemName: "server.rack")
                                    .foregroundColor(.appPrimary.opacity(0.8))
                                    .frame(width: 20)
                                
                                TextField("https://example.com", text: $baseUrl)
                                    .foregroundColor(.white)
                                    .autocapitalization(.none)
                                    .disableAutocorrection(true)
                                    .keyboardType(.URL)
                            }
                            .padding(.vertical, 12)
                            .padding(.horizontal, 14)
                            .background(Color.white.opacity(0.04))
                            .cornerRadius(10)
                            .overlay(
                                RoundedRectangle(cornerRadius: 10)
                                    .stroke(Color.white.opacity(0.08), lineWidth: 1)
                            )
                        }
                        
                        VStack(alignment: .leading, spacing: 8) {
                            Text("TÊN ĐĂNG NHẬP")
                                .font(.system(size: 10, weight: .bold))
                                .foregroundColor(.appPrimary)
                                .tracking(1)
                            
                            HStack {
                                Image(systemName: "person.fill")
                                    .foregroundColor(.appPrimary.opacity(0.8))
                                    .frame(width: 20)
                                
                                TextField("Tài khoản", text: $username)
                                    .foregroundColor(.white)
                                    .autocapitalization(.none)
                                    .disableAutocorrection(true)
                            }
                            .padding(.vertical, 12)
                            .padding(.horizontal, 14)
                            .background(Color.white.opacity(0.04))
                            .cornerRadius(10)
                            .overlay(
                                RoundedRectangle(cornerRadius: 10)
                                    .stroke(Color.white.opacity(0.08), lineWidth: 1)
                            )
                        }
                        
                        VStack(alignment: .leading, spacing: 8) {
                            Text("MẬT KHẨU")
                                .font(.system(size: 10, weight: .bold))
                                .foregroundColor(.appPrimary)
                                .tracking(1)
                            
                            HStack {
                                Image(systemName: "lock.fill")
                                    .foregroundColor(.appPrimary.opacity(0.8))
                                    .frame(width: 20)
                                
                                SecureField("Mật khẩu", text: $password)
                                    .foregroundColor(.white)
                            }
                            .padding(.vertical, 12)
                            .padding(.horizontal, 14)
                            .background(Color.white.opacity(0.04))
                            .cornerRadius(10)
                            .overlay(
                                RoundedRectangle(cornerRadius: 10)
                                    .stroke(Color.white.opacity(0.08), lineWidth: 1)
                            )
                        }
                    }
                    .padding(24)
                    .background(
                        RoundedRectangle(cornerRadius: 20)
                            .fill(Color.appSurface.opacity(0.6))
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 20)
                            .stroke(Color.white.opacity(0.08), lineWidth: 1.5)
                    )
                    .padding(.horizontal, 20)
                    
                    if let error = errorMessage {
                        HStack(spacing: 8) {
                            Image(systemName: "exclamationmark.triangle.fill")
                                .foregroundColor(.red)
                            Text(error)
                                .font(.system(size: 13))
                                .foregroundColor(.white.opacity(0.9))
                        }
                        .padding(.vertical, 10)
                        .padding(.horizontal, 16)
                        .background(Color.red.opacity(0.15))
                        .cornerRadius(10)
                        .overlay(
                            RoundedRectangle(cornerRadius: 10)
                                .stroke(Color.red.opacity(0.3), lineWidth: 1)
                        )
                        .padding(.horizontal, 20)
                        .transition(.opacity.combined(with: .scale))
                    }
                    
                    // Submit Connect Button
                    Button(action: handleConnect) {
                        HStack {
                            if isLoading {
                                ProgressView()
                                    .progressViewStyle(CircularProgressViewStyle(tint: .white))
                                    .padding(.trailing, 8)
                            } else {
                                Image(systemName: "bolt.horizontal.fill")
                                    .font(.system(size: 16, weight: .bold))
                            }
                            
                            Text(isLoading ? "Đang kết nối..." : "Kết nối máy chủ")
                                .font(.system(size: 16, weight: .bold))
                        }
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 52)
                        .background(
                            LinearGradient(
                                colors: [Color.appPrimary, Color.appPrimary.opacity(0.8)],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )
                        .cornerRadius(12)
                        .shadow(color: Color.appPrimary.opacity(0.4), radius: 12, x: 0, y: 5)
                    }
                    .disabled(isLoading || username.isEmpty || password.isEmpty || baseUrl.isEmpty)
                    .padding(.horizontal, 20)
                    
                    Spacer()
                }
            }
        }
    }
    
    private func handleConnect() {
        guard !baseUrl.isEmpty, !username.isEmpty, !password.isEmpty else { return }
        
        withAnimation {
            isLoading = true
            errorMessage = nil
        }
        
        // Sanitize url
        var cleanUrl = baseUrl.trimmingCharacters(in: .whitespacesAndNewlines)
        if !cleanUrl.lowercased().hasPrefix("http://") && !cleanUrl.lowercased().hasPrefix("https://") {
            cleanUrl = "https://" + cleanUrl
        }
        
        let targetConfig = ServerConfig(
            baseUrl: cleanUrl,
            username: username.trimmingCharacters(in: .whitespacesAndNewlines),
            password: password,
            clientName: "TroLyNhac",
            apiVersion: "1.16.1"
        )
        
        // Creating temporary testing repo
        let tempRepo = SubsonicRepository()
        tempRepo.config = targetConfig
        
        Task {
            let ok = await tempRepo.ping()
            
            await MainActor.run {
                isLoading = false
                if ok {
                    repository.saveConfig(targetConfig)
                    onConnected(targetConfig)
                } else {
                    withAnimation {
                        errorMessage = "Không thể kết nối đến máy chủ. Vui lòng kiểm tra lại thông tin cấu hình và mạng."
                    }
                }
            }
        }
    }
}
