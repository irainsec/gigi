import Foundation
import Combine

public class WebSocketManager: ObservableObject {
    public static let shared = WebSocketManager()
    
    @Published public var isConnected: Bool = false
    @Published public var partnerStatus: String = "Faraway"
    @Published public var heartbeatBpm: Int = 0
    @Published public var isHeartbeatActive: Bool = false
    
    private var webSocketTask: URLSessionWebSocketTask?
    private var pingTimer: Timer?
    
    private init() {}
    
    public func connect(serverUrl: String = "wss://gigi.iamanraj.com/ws") {
        guard let url = URL(string: serverUrl) else { return }
        let session = URLSession(configuration: .default)
        webSocketTask = session.webSocketTask(with: url)
        webSocketTask?.resume()
        
        isConnected = true
        receiveMessages()
        startPingTimer()
    }
    
    public func disconnect() {
        webSocketTask?.cancel(with: .goingAway, reason: nil)
        pingTimer?.invalidate()
        isConnected = false
    }
    
    private func receiveMessages() {
        webSocketTask?.receive { [weak self] result in
            switch result {
            case .success(let message):
                switch message {
                case .string(let text):
                    self?.handleIncomingText(text)
                case .data(let data):
                    if let text = String(data: data, encoding: .utf8) {
                        self?.handleIncomingText(text)
                    }
                @unknown default:
                    break
                }
                self?.receiveMessages()
            case .failure(let error):
                print("WebSocket error: \(error)")
                DispatchQueue.main.async {
                    self?.isConnected = false
                }
            }
        }
    }
    
    private func handleIncomingText(_ text: String) {
        guard let data = text.data(using: .utf8) else { return }
        do {
            if let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
               let type = json["type"] as? String {
                DispatchQueue.main.async {
                    if type == "partner_status", let status = json["status"] as? String {
                        self.partnerStatus = status
                    } else if type == "heartbeat", let bpm = json["bpm"] as? Int {
                        self.heartbeatBpm = bpm
                        self.isHeartbeatActive = bpm > 0
                    }
                }
            }
        } catch {
            print("Failed to decode WebSocket message: \(error)")
        }
    }
    
    private func startPingTimer() {
        pingTimer?.invalidate()
        pingTimer = Timer.scheduledTimer(withTimeInterval: 30, repeats: true) { [weak self] _ in
            self?.webSocketTask?.sendPing { error in
                if let error = error {
                    print("Ping error: \(error)")
                }
            }
        }
    }
}
