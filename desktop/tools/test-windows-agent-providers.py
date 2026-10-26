"""Host -> DPAPI -> current Kotlin runtime -> local HTTP provider -> real tool.
No provider accounts or paid generations are contacted.
"""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
import sys
import tempfile
import threading
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from agent_host import AgentHost
from local_host import windows_keys, write_secret
from native_operations import NativeOperations
from corte_host import CorteHost


class ProviderAcceptance(unittest.TestCase):
    def test_profile_dpapi_tool_inference(self):
        requests=[]
        class Handler(BaseHTTPRequestHandler):
            protocol_version='HTTP/1.1'
            def log_message(self,*_):pass
            def do_POST(self):
                body=json.loads(self.rfile.read(int(self.headers['Content-Length'])));requests.append(body)
                if len(requests)==1:
                    message={'role':'assistant','content':None,'tool_calls':[{'id':'choose-group','type':'function','function':{'name':'use_desktop_tools','arguments':'{"group":"connections"}'}}]};finish='tool_calls'
                elif len(requests)==2:
                    message={'role':'assistant','content':None,'tool_calls':[{'id':'tool-1','type':'function','function':{'name':'get_nexus_settings','arguments':'{}'}}]};finish='tool_calls'
                else:message={'role':'assistant','content':'Conexión y herramienta verificadas localmente.'};finish='stop'
                data=json.dumps({'choices':[{'message':message,'finish_reason':finish}],'usage':{'prompt_tokens':100,'completion_tokens':15}}).encode()
                self.send_response(200);self.send_header('Content-Type','application/json');self.send_header('Content-Length',str(len(data)));self.send_header('Connection','close');self.end_headers();self.wfile.write(data);self.wfile.flush()
        server=ThreadingHTTPServer(('127.0.0.1',0),Handler);threading.Thread(target=server.serve_forever,daemon=True).start()
        with tempfile.TemporaryDirectory(prefix='nexus-brain-qa-') as tmp:
            data=Path(tmp);root=Path(__file__).resolve().parents[1]
            host=AgentHost(root,data,windows_keys,write_secret)
            host.native=NativeOperations(data,host,lambda:{},lambda _:None,None,lambda:{})
            host.corte=CorteHost(root,data,host.native)
            try:
                profile={'id':'mock-profile','name':'Local test','baseUrl':f'http://127.0.0.1:{server.server_port}/v1','modelId':'mock-model','capabilities':{'tools':True},'limits':{'maxCallsPerTurn':4}}
                result=host.command({'op':'settings','owner':'qa','args':{'backend':'profile','mode':'AGENT','brainProfile':profile,'brainKey':'synthetic-private-test-key','limits':{'maxToolRounds':3,'maxTokens':512}}})
                ciphertext=(data/'assistant/credentials.dpapi').read_bytes()
                self.assertNotIn(b'synthetic-private-test-key',ciphertext)
                self.assertNotIn('synthetic-private-test-key',(data/'assistant/settings.json').read_text('utf-8'))
                self.assertIn('brain:mock-profile',result['settings']['configured'])
                result=host.command({'op':'send','owner':'qa','args':{'text':'Consulta los ajustes reales con get_nexus_settings.'}})['result']
                self.assertIn('verificadas localmente',result['text'])
                self.assertEqual(len(requests),3)
                self.assertTrue(all(len(r['tools']) <= 64 for r in requests))
                sent = {t['function']['name'] for r in requests for t in r['tools']}
                available = set(host.command({'op':'snapshot','owner':'qa','args':{}})['result']['tools'])
                self.assertEqual(available, sent, 'Every runtime tool remains reachable across the two groups')
                self.assertIn('prepare_generation', {t['function']['name'] for t in requests[0]['tools']})
                self.assertIn('get_nexus_settings', {t['function']['name'] for t in requests[1]['tools']})
                self.assertTrue(any(m.get('role')=='tool' for m in requests[-1]['messages']))
                self.assertEqual(requests[0]['model'],'mock-model')
                self.assertEqual(requests[0].get('max_tokens'),512)
                print('PASS: settings, DPAPI, profile, inference, real native tool, second inference')
            finally:
                print('Local HTTP requests received:',len(requests))
                host.close();host.corte.close();host.native.executor.shutdown()
        server.shutdown();server.server_close()

if __name__=='__main__':unittest.main(verbosity=2)
