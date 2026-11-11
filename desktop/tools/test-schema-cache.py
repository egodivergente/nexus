import sys
import unittest
from io import BytesIO
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import local_host as host

class SchemaCacheTests(unittest.TestCase):
    url='https://static.atlascloud.ai/model/schema/test.json'
    def setUp(self):
        host.SCHEMA_CACHE.clear()
    def get(self,**kwargs):
        return host.dispatch({},'schema',{'url':self.url,**kwargs})
    def test_reuses_schema_and_does_not_leak_mutations(self):
        with patch.object(host,'public_json',return_value={'a':[]}) as fetch:
            self.get()['a'].append('change')
            self.assertEqual(self.get(),{'a':[]})
            self.assertEqual(fetch.call_count,1)
            fetch.assert_called_with(self.url,timeout=15)
    def test_expiry_and_refresh(self):
        with patch.object(host,'public_json',return_value={}) as fetch:
            self.get();self.get(refresh=True)
            host.SCHEMA_CACHE[self.url]=(0,'{}')
            self.get()
            self.assertEqual(fetch.call_count,3)
    def test_errors_are_not_cached(self):
        with patch.object(host,'public_json',side_effect=TimeoutError):
            with self.assertRaises(TimeoutError):self.get()
        self.assertFalse(host.SCHEMA_CACHE)
    def test_allowlist_before_cache(self):
        with self.assertRaises(ValueError):host.dispatch({},'schema',{'url':'https://example.test/a'})
    def test_public_schema_identifies_nexus_without_forwarding_provider_secret(self):
        def answer(request, timeout):
            # Cloudflare rejects the library-default Python urllib signature.
            self.assertEqual(request.get_header('User-agent'),'Nexus/1.0.0 (Windows; Desktop)')
            self.assertEqual(request.get_header('Accept'),'application/json')
            self.assertIsNone(request.get_header('Authorization'))
            self.assertEqual(timeout,15)
            return BytesIO(b'{"components":{}}')
        with patch.object(host.OPENER,'open',side_effect=answer) as fetch:
            self.assertEqual(host.dispatch({'atlas_cloud':'isolated-test-value'},'schema',{'url':self.url}),{'components':{}})
            self.assertEqual(fetch.call_count,1)

if __name__=='__main__':unittest.main()
