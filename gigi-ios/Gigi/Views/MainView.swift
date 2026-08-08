import SwiftUI

public struct MainView: View {
    @StateObject private var mainViewModel = MainViewModel()
    @StateObject private var musicViewModel = MusicViewModel()
    
    private let navItems = [
        NavigationItem(label: "Reminders", iconName: "bell.fill"),
        NavigationItem(label: "Sweet Corner", iconName: "heart.fill"),
        NavigationItem(label: "Music", iconName: "music.note.house.fill")
    ]
    
    private let musicNavItems = [
        NavigationItem(label: "Library", iconName: "square.stack.fill"),
        NavigationItem(label: "Player", iconName: "play.fill"),
        NavigationItem(label: "Settings", iconName: "gearshape.fill")
    ]
    
    public var body: some View {
        ZStack(alignment: .bottom) {
            Group {
                switch mainViewModel.selectedNavIndex {
                case 0:
                    RemindersView()
                case 1:
                    SweetCornerView()
                case 2:
                    MusicView(musicViewModel: musicViewModel)
                default:
                    SweetCornerView()
                }
            }
            .ignoresSafeArea()
            
            if mainViewModel.isNavVisible {
                let inMusicTab = mainViewModel.selectedNavIndex == 2
                
                GlassBottomNavigation(
                    items: inMusicTab ? musicNavItems : navItems,
                    selectedIndex: {
                        if !inMusicTab { return mainViewModel.selectedNavIndex }
                        if musicViewModel.isMusicSettingsOpen { return 2 }
                        if musicViewModel.isAlbumBrowserOpen { return 0 }
                        return 1
                    }(),
                    onItemSelected: { index in
                        if inMusicTab {
                            switch index {
                            case 0:
                                musicViewModel.setMusicSettingsOpen(false)
                                musicViewModel.setAlbumBrowserOpen(true)
                            case 1:
                                musicViewModel.setMusicSettingsOpen(false)
                                musicViewModel.setAlbumBrowserOpen(false)
                            case 2:
                                musicViewModel.setMusicSettingsOpen(true)
                            default: break
                            }
                        } else {
                            mainViewModel.selectedNavIndex = index
                        }
                    },
                    onSwipe: { delta in
                        if inMusicTab {
                            let currentSub = musicViewModel.isMusicSettingsOpen ? 2 : (musicViewModel.isAlbumBrowserOpen ? 0 : 1)
                            if delta < 0 { // Right swipe -> Back
                                if currentSub == 2 {
                                    musicViewModel.setMusicSettingsOpen(false)
                                    musicViewModel.setAlbumBrowserOpen(false)
                                } else {
                                    musicViewModel.setMusicSettingsOpen(false)
                                    musicViewModel.setAlbumBrowserOpen(false)
                                    mainViewModel.selectedNavIndex = 1
                                }
                            } else if delta > 0 { // Left swipe -> Next
                                if currentSub == 0 {
                                    musicViewModel.setMusicSettingsOpen(false)
                                    musicViewModel.setAlbumBrowserOpen(false)
                                } else if currentSub == 1 {
                                    musicViewModel.setMusicSettingsOpen(true)
                                } else {
                                    musicViewModel.setMusicSettingsOpen(false)
                                    musicViewModel.setAlbumBrowserOpen(false)
                                    mainViewModel.selectedNavIndex = 1
                                }
                            }
                        } else {
                            mainViewModel.selectedNavIndex = max(0, min(navItems.count - 1, mainViewModel.selectedNavIndex + delta))
                        }
                    },
                    isMusicTab: inMusicTab,
                    activeNowPlayingTrackLabel: musicViewModel.currentSong?.title,
                    isNowPlayingPlaying: musicViewModel.isPlaying,
                    onNowPlayingClick: {
                        mainViewModel.selectedNavIndex = 2
                        musicViewModel.setAlbumBrowserOpen(false)
                        musicViewModel.setMusicSettingsOpen(false)
                    },
                    onNowPlayingTogglePlay: {
                        musicViewModel.togglePlayback()
                    }
                )
            }
        }
        .onAppear {
            WebSocketManager.shared.connect()
        }
    }
}
