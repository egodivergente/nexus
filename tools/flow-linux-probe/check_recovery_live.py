"""Private protocol smoke test. No Google login, credentials output or generation."""
import asyncio
from pathlib import Path
import httpx
import websockets


async def main():
    origin = 'http://100.112.57.2:8899'
    token = (Path.home() / '.local/share/nexus-flow-probe/recovery-token').read_text().strip()
    async with httpx.AsyncClient(base_url=origin, timeout=70) as client:
        assert (await client.get('/status')).status_code == 401
        response = await client.post('/unlock', json={'token': token})
        response.raise_for_status()
        cookie = client.cookies.get('nexus_flow')
        async with websockets.connect(origin.replace('http:', 'ws:') + '/websockify',
                                      origin=origin,
                                      additional_headers={'Cookie': f'nexus_flow={cookie}'}) as ws:
            assert ws.subprotocol is None
            assert (await asyncio.wait_for(ws.recv(), 5)).startswith(b'RFB ')
            response = await client.post('/lock')
            response.raise_for_status()
            await asyncio.wait_for(ws.wait_closed(), 4)
        assert (await client.get('/status')).status_code == 401
        print('PASS: anonymous denied, authenticated RFB handshake, live socket revoked, session invalidated')


if __name__ == '__main__':
    asyncio.run(main())
