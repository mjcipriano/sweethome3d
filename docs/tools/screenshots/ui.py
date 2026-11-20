import socket, sys
s = socket.create_connection(("127.0.0.1", 47999)); s.sendall((" ".join(sys.argv[1:]) + "\n").encode()); s.settimeout(30)
d = b""
while True:
    b = s.recv(65536)
    if not b: break
    d += b
print(d.decode().rstrip())
