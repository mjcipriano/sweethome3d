import json, http.server
N=[0]
FIRST={"reply":"I'll add a reading corner by the east window of the living room: an armchair turned toward the room and a lamp beside it.","commands":[{"action":"add_furniture","name":"Armchair","x":420,"y":70,"angle":0},{"action":"add_furniture","name":"Lamp","x":350,"y":40,"elevation":0}]}
class H(http.server.BaseHTTPRequestHandler):
    def log_message(self,*a): pass
    def do_POST(self):
        body=json.loads(self.rfile.read(int(self.headers.get('Content-Length','0'))) or b'{}')
        N[0]+=1
        text=json.dumps(FIRST) if N[0]==1 else json.dumps({"reply":"Done: the armchair and the lamp are in the north-east corner of the living room. Edit > Undo removes them.","commands":[]})
        
        if body.get('stream'):
            self.send_response(200); self.send_header('Content-Type','text/event-stream'); self.end_headers()
            for i in range(0,len(text),40):
                self.wfile.write(('data: '+json.dumps({"choices":[{"delta":{"content":text[i:i+40]}}]})+'\n\n').encode())
            self.wfile.write(b'data: [DONE]\n\n')
        else:
            d=json.dumps({"choices":[{"message":{"role":"assistant","content":text}}]}).encode()
            self.send_response(200); self.send_header('Content-Type','application/json'); self.send_header('Content-Length',str(len(d))); self.end_headers(); self.wfile.write(d)
http.server.HTTPServer(('127.0.0.1',47998),H).serve_forever()
