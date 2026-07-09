import SwiftUI

public struct AppTheme {
    // Elegant luxury gold theme (Vi2Play brand style)
    public static let background = Color(red: 11/255, green: 11/255, blue: 13/255) // #0B0B0D - Warm near-black
    public static let primary = Color(red: 224/255, green: 175/255, blue: 80/255) // #E0AF50 - Premium warm gold
    public static let surface = Color(red: 11/255, green: 11/255, blue: 13/255) // #0B0B0D - Surface dark
    public static let surfaceVariant = Color(red: 29/255, green: 26/255, blue: 21/255) // #1D1A15 - Warm charcoal cards
    public static let divider = Color(red: 53/255, green: 48/255, blue: 31/255) // #35301F - Warm gold outline
    public static let textPrimary = Color.white
    public static let textSecondary = Color(red: 167/255, green: 156/255, blue: 134/255) // #A79C86 - Muted warm gold-gray
    public static let textMuted = Color.white.opacity(0.4)
    
    // Golden gradient for highlights, active sliders and premium headers
    public static let goldGradient = LinearGradient(
        colors: [
            Color(red: 246/255, green: 231/255, blue: 181/255), // Cream / Champagne
            Color(red: 224/255, green: 175/255, blue: 80/255),  // Gold highlight
            Color(red: 170/255, green: 119/255, blue: 28/255)   // Deep bronze
        ],
        startPoint: .topLeading,
        endPoint: .bottomTrailing
    )
}

// Color Extension for ease of use
extension Color {
    public static let appBackground = AppTheme.background
    public static let appPrimary = AppTheme.primary
    public static let appSurface = AppTheme.surface
    public static let appSurfaceVariant = AppTheme.surfaceVariant
    public static let appDivider = AppTheme.divider
    public static let appGoldGradient = AppTheme.goldGradient
}

// SwiftUI Rounded Corner helper
struct CornerRadiusStyle: ViewModifier {
    var radius: CGFloat
    var corners: UIRectCorner

    struct CornerRadiusShape: Shape {
        var radius: CGFloat = .infinity
        var corners: UIRectCorner = .allCorners

        func path(in rect: CGRect) -> Path {
            let path = UIBezierPath(roundedRect: rect, byRoundingCorners: corners, cornerRadii: CGSize(width: radius, height: radius))
            return Path(path.cgPath)
        }
    }

    func body(content: Content) -> some View {
        content
            .clipShape(CornerRadiusShape(radius: radius, corners: corners))
    }
}

extension View {
    public func cornerRadius(_ radius: CGFloat, corners: UIRectCorner) -> some View {
        modifier(CornerRadiusStyle(radius: radius, corners: corners))
    }
    
    @ViewBuilder
    public func hideListBackground() -> some View {
        if #available(iOS 16.0, *) {
            self.scrollContentBackground(.hidden)
        } else {
            self
        }
    }
}

