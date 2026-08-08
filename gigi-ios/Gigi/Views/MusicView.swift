import SwiftUI

public struct MusicView: View {
    @ObservedObject var musicViewModel: MusicViewModel
    @State private var rotationAngle: Double = 0
    
    public var body: some View {
        ZStack {
            LinearGradient(
                gradient: Gradient(colors: [Color(red: 0.15, green: 0.1, blue: 0.2), Color(red: 0.05, green: 0.05, blue: 0.1)]),
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()
            
            if musicViewModel.isAlbumBrowserOpen {
                // Library View
                VStack {
                    HStack {
                        Text("Your Library")
                            .font(.system(size: 28, weight: .bold))
                            .foregroundColor(.white)
                        Spacer()
                    }
                    .padding(.horizontal)
                    .padding(.top, 60)
                    
                    Spacer()
                    
                    VStack(spacing: 12) {
                        Image(systemName: "music.note.list")
                            .font(.system(size: 48))
                            .foregroundColor(.purple)
                        Text("No custom albums yet")
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundColor(.white)
                    }
                    .padding()
                    .background(RoundedRectangle(cornerRadius: 16).fill(Color.white.opacity(0.1)))
                    
                    Spacer()
                }
                .padding(.bottom, 90)
            } else {
                // Player View
                VStack(spacing: 24) {
                    Spacer()
                    
                    // Vinyl Disc
                    ZStack {
                        Circle()
                            .fill(Color.black)
                            .frame(width: 260, height: 260)
                            .shadow(color: .purple.opacity(0.3), radius: 20)
                            .rotationEffect(.degrees(rotationAngle))
                        
                        Circle()
                            .stroke(Color.white.opacity(0.15), lineWidth: 2)
                            .frame(width: 240, height: 240)
                        
                        Circle()
                            .fill(Color.purple)
                            .frame(width: 80, height: 80)
                        
                        Text("🎵")
                            .font(.system(size: 32))
                    }
                    
                    if let song = musicViewModel.currentSong {
                        VStack(spacing: 6) {
                            Text(song.title)
                                .font(.system(size: 22, weight: .bold))
                                .foregroundColor(.white)
                            Text(song.artist)
                                .font(.system(size: 15))
                                .foregroundColor(.white.opacity(0.7))
                        }
                    }
                    
                    // Playback controls
                    HStack(spacing: 40) {
                        Button(action: {}) {
                            Image(systemName: "backward.fill")
                                .font(.system(size: 24))
                                .foregroundColor(.white)
                        }
                        
                        Button(action: {
                            musicViewModel.togglePlayback()
                        }) {
                            Image(systemName: musicViewModel.isPlaying ? "pause.circle.fill" : "play.circle.fill")
                                .font(.system(size: 64))
                                .foregroundColor(.purple)
                        }
                        
                        Button(action: {}) {
                            Image(systemName: "forward.fill")
                                .font(.system(size: 24))
                                .foregroundColor(.white)
                        }
                    }
                    
                    Spacer()
                }
                .padding(.bottom, 90)
            }
            
            // Settings Sheet Overlay
            if musicViewModel.isMusicSettingsOpen {
                VStack {
                    Spacer()
                    VStack(spacing: 16) {
                        Text("Deck Tools & Settings")
                            .font(.system(size: 18, weight: .bold))
                        
                        Divider()
                        
                        HStack {
                            Text("Theme Editor")
                            Spacer()
                            Image(systemName: "chevron.right")
                        }
                        .padding(.vertical, 8)
                        
                        Button(action: {
                            musicViewModel.setMusicSettingsOpen(false)
                        }) {
                            Text("Close")
                                .font(.system(size: 16, weight: .bold))
                                .foregroundColor(.white)
                                .frame(maxWidth: .infinity)
                                .padding()
                                .background(Capsule().fill(Color.purple))
                        }
                    }
                    .padding()
                    .background(RoundedRectangle(cornerRadius: 24).fill(Color(UIColor.secondarySystemGroupedBackground)))
                    .padding()
                }
                .transition(.move(edge: .bottom))
            }
        }
    }
}
