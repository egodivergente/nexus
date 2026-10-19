"""Cancellation closes a real pending local HTTP inference and preserves the JVM session."""
from http.server import BaseHTTPRequestHandler,ThreadingHTTPServer
import json
from pathlib import Path
import sys
import tempfile
import threading
import time
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from agent_host import AgentHost
from local_host import windows_keys,write_secret


class CancelAcceptance(unittest.TestCase):
    def test_cancel_is_owned_and_next_turn_uses_same_runtime(self):
        received=threading.Event();release=threading.Event();calls=[]
        class Handler(BaseHTTPRequestHandler):
            def log_message(self,*_):pass
            def do_POST(self):
                body=json.loads(self.rfile.read(int(self.headers['Content-Length'])));calls.append(body)
                if len(calls)==1:received.set();release.wait(20)
                raw=json.dumps({'choices':[{'message':{'role':'assistant','content':'Seguimos.'},'finish_reason':'stop'}],'usage':{'prompt_tokens':10,'completion_tokens':3}}).encode()
                try:self.send_response(200);self.send_header('Content-Length',str(len(raw)));self.end_headers();self.wfile.write(raw)
                except ConnectionError:pass
        server=ThreadingHTTPServer(('127.0.0.1',0),Handler);threading.Thread(target=server.serve_forever,daemon=True).start()
        try:
            with tempfile.TemporaryDirectory(prefix='nexus-cancel-') as tmp:
                host=AgentHost(Path(__file__).resolve().parents[1],Path(tmp),windows_keys,write_secret)
                try:
                    host.command({'op':'settings','owner':'qa','args':{'backend':'profile','brainKey':'synthetic-key','brainProfile':{'id':'local-brain','name':'Local','baseUrl':f'http://127.0.0.1:{server.server_port}','modelId':'mock','capabilities':{'tools':False,'streaming':False}}}})
                    outcome=[]
                    def send():
                        try:outcome.append(host.command({'op':'send','owner':'qa','turn_id':'turn-one','args':{'text':'Primero'}}))
                        except Exception as error:outcome.append(str(error))
                    worker=threading.Thread(target=send);worker.start();self.assertTrue(received.wait(10));pid=host.process.pid
                    with self.assertRaises(ValueError):host.cancel('other-window','turn-one')
                    with self.assertRaises(ValueError):host.cancel('qa','stale-turn')
                    start=time.monotonic();self.assertTrue(host.cancel('qa','turn-one')['requested']);worker.join(5)
                    self.assertFalse(worker.is_alive());self.assertLess(time.monotonic()-start,5)
                    self.assertIn('Turno detenido',str(outcome))
                    with self.assertRaises(ValueError):host.cancel('qa','turn-one')
                    result=host.command({'op':'send','owner':'qa','turn_id':'turn-two','args':{'text':'Ahora sí'}})['result']
                    self.assertEqual(host.process.pid,pid);self.assertEqual(result['text'],'Seguimos.')
                    self.assertEqual(len(calls),2)
                finally:release.set();host.close()
        finally:server.shutdown();server.server_close()

    def test_cancel_waiting_browser_tool_discards_late_reply(self):
        calls=[]
        class Provider(BaseHTTPRequestHandler):
            protocol_version="HTTP/1.1"
            def log_message(self,*_):pass
            def do_POST(self):
                calls.append(json.loads(self.rfile.read(int(self.headers['Content-Length']))))
                if len(calls)==1:
                    message={'role':'assistant','content':None,'tool_calls':[{'id':'catalog','type':'function','function':{'name':'list_models','arguments':'{}'}}]};finish='tool_calls'
                else:message={'role':'assistant','content':'Seguimos.'};finish='stop'
                raw=json.dumps({'choices':[{'message':message,'finish_reason':finish}]}).encode()
                self.send_response(200);self.send_header('Content-Type','application/json');self.send_header('Content-Length',str(len(raw)));self.send_header('Connection','close');self.end_headers();self.wfile.write(raw);self.wfile.flush()
        server=ThreadingHTTPServer(('127.0.0.1',0),Provider);threading.Thread(target=server.serve_forever,daemon=True).start()
        try:
            with tempfile.TemporaryDirectory(prefix='nexus-cancel-tool-') as tmp:
                host=AgentHost(Path(__file__).resolve().parents[1],Path(tmp),windows_keys,write_secret)
                try:
                    host.command({'op':'settings','owner':'qa','args':{'backend':'profile','mode':'AGENT','brainKey':'synthetic-key','brainProfile':{'id':'local-tools','name':'Local','baseUrl':f'http://127.0.0.1:{server.server_port}','modelId':'mock','capabilities':{'tools':True,'streaming':False}}}})
                    outcome=[]
                    def send():
                        try:outcome.append(host.command({'op':'send','owner':'qa','turn_id':'tool-turn','args':{'text':'Lista los modelos'}}))
                        except Exception as error:outcome.append(str(error))
                    worker=threading.Thread(target=send);worker.start();deadline=time.monotonic()+10;events=[]
                    while time.monotonic()<deadline:
                        events=host.events('qa')
                        if events:break
                        time.sleep(.02)
                    self.assertTrue(events);self.assertEqual(events[0]['op'],'models');pid=host.process.pid
                    host.cancel('qa','tool-turn');worker.join(5);self.assertFalse(worker.is_alive());self.assertIn('Turno detenido',str(outcome))
                    self.assertFalse(host.reply({'owner':'qa','id':events[0]['id'],'ok':True,'value':[]}))
                    result=host.command({'op':'send','owner':'qa','turn_id':'next','args':{'text':'Continuar'}})['result']
                    self.assertEqual(result['text'],'Seguimos.');self.assertEqual(host.process.pid,pid);self.assertEqual(len(calls),2)
                    # A callback queued at the same time as cancellation must not start another wait.
                    host.active_exchange={'cancelled':True}
                    self.assertFalse(host.browser_reply('models',{})['ok']);self.assertFalse(host.pending)
                    host.active_exchange=None
                finally:host.close()
        finally:server.shutdown();server.server_close()


if __name__=='__main__':unittest.main(verbosity=2)
