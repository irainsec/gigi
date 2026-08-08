import SwiftUI

public struct NavigationItem: Identifiable {
    public var id: String { label }
    public var label: String
    public var iconName: String
}

public struct GlassBottomNavigation: View {
    public var items: [NavigationItem]
    public var selectedIndex: Int
    public var onItemSelected: (Int) -> Void
    public var onSwipe: (Int) -> Void
    public var isMusicTab: Bool
    public var activeNowPlayingTrackLabel: String?
    public var isNowPlayingPlaying: Bool
    public var onNowPlayingClick: () -> Void
    public var onNowPlayingTogglePlay: () -> Void
    
    @GestureState private var dragOffset: CGFloat = 0
    
    public var body: some View {
        VStack(spacing: 8) {
            if let track = activeNowPlayingTrackLabel, !track.isEmpty, isNowPlayingPlaying, !isMusicTab {
                DynamicIslandMiniPlayer(
                    trackLabel: track,
                    partnerName: nil,
                    isPlaying: isNowPlayingPlaying,
                    onTogglePlay: onNowPlayingTogglePlay,
                    onClick: onNowPlayingClick
                )
                .transition(.move(edge: .bottom).combined(with: .opacity))
            }
            
            HStack(spacing: 0) {
                ForEach(0..<items.count, id: \.self) { index in
                    let item = items[index]
                    let isSelected = selectedIndex == index
                    
                    Button(action: {
                        withAnimation(.spring(response: 0.3, dampingFraction: 0.7)) {
                            onItemSelected(index)
                        }
                    }) {
                        HStack(spacing: 6) {
                            Image(systemName: item.iconName)
                                .font(.system(size: 16, weight: .semibold))
                            
                            if isSelected {
                                Text(item.label)
                                    .font(.system(size: 13, weight: .bold))
                                    .transition(.scale.combined(with: .opacity))
                            }
                        }
                        .padding(.vertical, 10)
                        .padding(.horizontal, isSelected ? 16 : 12)
                        .foregroundColor(isSelected ? .purple : .primary.opacity(0.7))
                        .background(
                            Capsule()
                                .fill(isSelected ? Color.purple.opacity(0.15) : Color.clear)
                        )
                    }
                }
            }
            .padding(6)
            .background(
                Capsule()
                    .fill(Color(UIColor.systemBackground).opacity(0.8))
                    .shadow(color: Color.black.opacity(0.12), radius: 12, x: 0, y: 4)
            )
            .gesture(
                DragGesture()
                    .updating($dragOffset) { value, state, _ in
                        state = value.translation.width
                    }
                    .onEnded { value in
                        if value.translation.width > 50 {
                            onSwipe(-1) // Swipe Right -> Prev
                        } else if value.translation.width < -50 {
                            onSwipe(1) // Swipe Left -> Next
                        }
                    }
            )
        }
        .padding(.horizontal, 16)
        .padding(.bottom, 20)
    }
}
