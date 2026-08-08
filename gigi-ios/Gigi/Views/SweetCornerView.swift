import SwiftUI

public struct SweetCornerView: View {
    @ObservedObject var webSocketManager = WebSocketManager.shared
    @State private var partnerName: String = "Aman Raj"
    @State private var showingInviteSheet: Bool = false
    
    public var body: some View {
        ZStack {
            LinearGradient(
                gradient: Gradient(colors: [Color(red: 0.1, green: 0.05, blue: 0.2), Color.black]),
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()
            
            VStack {
                HStack {
                    Button(action: {}) {
                        HStack {
                            Image(systemName: "house.fill")
                            Text("Recenter")
                                .font(.system(size: 13, weight: .medium))
                        }
                        .padding(.horizontal, 14)
                        .padding(.vertical, 8)
                        .background(Capsule().fill(Color.purple.opacity(0.4)))
                        .foregroundColor(.white)
                    }
                    Spacer()
                }
                .padding(.horizontal)
                .padding(.top, 50)
                
                Spacer()
                
                VStack(spacing: 12) {
                    ZStack {
                        Circle()
                            .fill(Color.orange)
                            .frame(width: 80, height: 80)
                        Text("🦁")
                            .font(.system(size: 44))
                    }
                    
                    Text(partnerName)
                        .font(.system(size: 20, weight: .bold))
                        .foregroundColor(.white)
                }
                
                Spacer()
                
                VStack(spacing: 10) {
                    HStack {
                        Image(systemName: "sparkles")
                            .font(.system(size: 24))
                            .foregroundColor(.yellow)
                        VStack(alignment: .leading) {
                            Text("Bring your person here!")
                                .font(.system(size: 15, weight: .bold))
                                .foregroundColor(.white)
                            Text("Tap + to send an invite or join")
                                .font(.system(size: 12))
                                .foregroundColor(.white.opacity(0.7))
                        }
                        Spacer()
                    }
                    .padding()
                    .background(
                        RoundedRectangle(cornerRadius: 18)
                            .fill(Color.purple.opacity(0.3))
                            .overlay(RoundedRectangle(cornerRadius: 18).stroke(Color.purple.opacity(0.5), lineWidth: 1))
                    )
                    
                    HStack {
                        Spacer()
                        Button(action: { showingInviteSheet = true }) {
                            Image(systemName: "plus")
                                .font(.system(size: 22, weight: .bold))
                                .foregroundColor(.white)
                                .frame(width: 54, height: 54)
                                .background(Circle().fill(Color.purple))
                                .shadow(radius: 8)
                        }
                    }
                }
                .padding(.horizontal, 24)
                .padding(.bottom, 90)
            }
        }
    }
}
