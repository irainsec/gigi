import socket
s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
s.settimeout(2)
try:
    s.connect(("10.135.191.132", 2803))
    print("SUCCESS: Port 2803 is reachable on 10.135.191.132")
except Exception as e:
    print(f"FAILED: Port 2803 is NOT reachable on 10.135.191.132. Error: {e}")
finally:
    s.close()
