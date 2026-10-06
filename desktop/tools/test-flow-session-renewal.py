import asyncio
import json
from pathlib import Path
import sys
import tempfile
import time
import unittest
from unittest.mock import AsyncMock, MagicMock, patch

root = Path(__file__).resolve().parents[2]
sys.path[:0] = [str(root/'desktop'), str(root/'tools/flow-bridge')]
import flow_connection as connection
import flow_provider as provider
import flow_session as session
import flow_quote

class BackgroundTests(unittest.TestCase):
    def setUp(self):
        connection.STATE={'status':'pending','checked_at':0}
        connection.RUNNING=False
        connection.NEXT_CHECK=0
        connection.LAST_STARTED=-1000
        provider.WORKERS.clear()

    def test_windows_share_one_refresh_and_manual_refresh_is_bounded(self):
        with patch.object(connection.time,'monotonic',return_value=100),patch.object(connection.threading,'Thread') as worker:
            self.assertEqual(connection.snapshot()['status'],'refreshing')
            connection.snapshot(refresh=True)
            connection.snapshot(interactive=True)
            self.assertEqual(worker.call_count,1)
            with patch.object(connection,'probe',return_value={'status':'connected','checked_at':1}):
                connection._refresh()
            self.assertEqual(connection.snapshot(refresh=True)['status'],'connected')
            self.assertEqual(worker.call_count,1)
        with patch.object(connection.time,'monotonic',return_value=116),patch.object(connection.threading,'Thread') as worker:
            self.assertEqual(connection.snapshot(refresh=True)['status'],'refreshing')
            self.assertEqual(worker.call_count,1)

    def test_generation_owns_profile_and_is_never_restarted(self):
        provider.WORKERS.add('existing-job')
        try:
            with patch.object(connection.threading,'Thread') as worker:
                self.assertEqual(connection.snapshot(refresh=True)['status'],'busy')
                self.assertEqual(connection.snapshot(interactive=True)['status'],'busy')
                worker.assert_not_called()
        finally:provider.WORKERS.clear()

    def test_interactive_login_is_explicit_and_shared_between_windows(self):
        with patch.object(connection.threading,'Thread') as worker:
            self.assertEqual(connection.snapshot(interactive=True)['status'],'signing_in')
            self.assertEqual(connection.snapshot()['status'],'signing_in')
            self.assertEqual(worker.call_count,1)
            self.assertEqual(worker.call_args.kwargs['args'],(True,))

    def test_failure_does_not_claim_last_connection_is_still_live(self):
        connection.STATE={'status':'connected','checked_at':1}
        with patch.object(connection,'probe',side_effect=RuntimeError('private detail')):
            connection._refresh()
        self.assertEqual(connection.snapshot()['status'],'error')
        self.assertNotIn('private',json.dumps(connection.STATE))

    def test_session_failure_never_becomes_video_price_fallback(self):
        with tempfile.TemporaryDirectory() as folder,patch.object(provider,'RUNTIME',Path(folder)),patch.object(provider,'available',return_value=True),patch.object(provider,'call',return_value={'status':'error','error_class':'SessionUnavailable'}):
            with self.assertRaisesRegex(ValueError,'renovar la sesión'):
                provider.quote({'model':'veo_fast','aspect':'16:9'})

class RenewalTests(unittest.IsolatedAsyncioTestCase):
    def test_cached_connection_expires_and_unverified_states_are_not_cached(self):
        with tempfile.TemporaryDirectory() as folder,patch.object(session,'CACHE',Path(folder)/'health.json'):
            session.remember('auth_required')
            self.assertIsNone(session.cached())
            session.remember('connected')
            self.assertEqual(session.cached()['status'],'connected')
            with patch.object(session.time,'time',return_value=time.time()+301):
                self.assertIsNone(session.cached())

    async def test_only_an_existing_google_session_can_be_renewed(self):
        with patch.object(session,'renew',AsyncMock(return_value={'status':'auth_required'})):
            with self.assertRaises(session.SessionUnavailable) as error:
                await session.ensure()
            self.assertEqual(error.exception.status,'auth_required')
        self.assertTrue(session.allowed_signin('https://accounts.google.com/v3/signin/accountchooser'))
        self.assertFalse(session.allowed_signin('https://accounts.google.com.example.com/signin'))
        self.assertFalse(session.allowed_signin('http://accounts.google.com/signin'))

    async def test_google_cookie_and_visible_composer_do_not_prove_server_auth(self):
        page=MagicMock(url='https://flow.google.com/project/fixture')
        page.locator.return_value.first.wait_for=AsyncMock()
        ctx=MagicMock()
        ctx.cookies=AsyncMock(return_value=[{'name':'SAPISID','domain':'.google.com'},{'name':'OSID','domain':'flow.google.com'}])
        ctx.request.get=AsyncMock(return_value=MagicMock(status=200,url='https://accounts.google.com/signin'))
        self.assertFalse(await session.app_ready(page,ctx))
        ctx.request.get.return_value.url='https://myaccount.google.com/'
        self.assertTrue(await session.app_ready(page,ctx))

    async def test_only_readonly_quote_is_retried_once_after_auth_failure(self):
        AuthExpiredError=type('AuthExpiredError',(Exception,),{})
        with patch.object(session,'ensure',AsyncMock()) as ensure,patch.object(flow_quote,'_quote',AsyncMock(side_effect=[AuthExpiredError(),{'credits':0}])) as quote:
            self.assertEqual((await flow_quote.quote('nano2','1:1'))['credits'],0)
            self.assertEqual(quote.await_count,2)
            ensure.assert_any_await(force=True)
        with patch.object(session,'ensure',AsyncMock()) as ensure,patch.object(flow_quote,'_quote',AsyncMock(side_effect=TimeoutError())) as quote:
            with self.assertRaises(TimeoutError):await flow_quote.quote('nano2','1:1')
            self.assertEqual(quote.await_count,1)
            self.assertEqual(ensure.await_count,1)

if __name__=='__main__':unittest.main()
