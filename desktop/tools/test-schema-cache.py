import sys
import unittest
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

if __name__=='__main__':unittest.main()
