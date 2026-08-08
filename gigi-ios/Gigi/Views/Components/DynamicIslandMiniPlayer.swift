import SwiftUI

public struct DynamicIslandMiniPlayer: View {
    public var trackLabel: String
    public var partnerName: String?
    public var isPlaying: Bool
    public var onTogglePlay: () -> Void
    public var onClick: () -> Void
    
    public var body: some View {
        Button(action: onClick) {
            HStack(spacing: 12) {
                ZStack {
                    Circle()
                        .fill(Color.black)
                        .frame(width: 32, height: 32)
                    Circle()
                        .fill(Color.white.opacity(0.3))
                        .frame(width: 10, height: 10)
                }
                
                VStack(alignment: .leading, spacing: 2) {
                    Text(trackLabel)
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundColor(.primary)
                        .lineLimit(1)
                    if let partner = partnerName {
                        Text(partner)
                            .font(.system(size: 11))
                            .foregroundColor(.secondary)
                            .lineLimit(1)
                    }
                }
                
                Spacer()
                
                Button(action: onTogglePlay) {
                    Image(systemName: isPlaying ? "pause.fill" : "play.fill")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(.primary)
                        .padding(8)
                        .background(Circle().fill(Color.primary.opacity(0.1)))
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .background(
                Capsule()
                    .fill(Color(UIColor.systemBackground).opacity(0.85))
                    .shadow(color: Color.black.opacity(0.15), radius: 10, x: 0, y: 4)
            )
        }
        .buttonStyle(PlainButtonStyle())
    }
}
