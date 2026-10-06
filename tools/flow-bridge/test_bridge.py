import asyncio
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch, AsyncMock
from PIL import Image
import bridge_tools as bridge

class JobTests(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.override = patch.object(bridge, "ROOT", self.root)
        self.override.start()
        folder = self.root / "results"
        folder.mkdir()
        self.image = folder / "fixture.png"
        Image.new("RGB", (8, 8), "blue").save(self.image)

    async def asyncTearDown(self):
        self.override.stop()
        self.temporary.cleanup()

    async def test_repeated_id_calls_provider_once_and_verifies_cache(self):
        stub = AsyncMock(return_value={"status": "completed", "files": [str(self.image)]})
        with patch("gflow_cli.mcp.tools.gflow_generate_image", stub):
            first = await bridge.flow_generate_image("same-job", "blue sphere")
            second = await bridge.flow_generate_image("same-job", "blue sphere")
        self.assertEqual(stub.await_count, 1)
        self.assertEqual(first["sha256"], second["sha256"])
        self.assertEqual(second["status"], "completed")

    async def test_different_parameters_cannot_reuse_id(self):
        stub = AsyncMock(return_value={"status": "completed", "files": [str(self.image)]})
        with patch("gflow_cli.mcp.tools.gflow_generate_image", stub):
            await bridge.flow_generate_image("same-job", "blue sphere")
            second = await bridge.flow_generate_image("same-job", "red sphere")
        self.assertEqual(stub.await_count, 1)
        self.assertEqual(second["status"], "error")

    async def test_ambiguous_failure_is_not_resubmitted(self):
        stub = AsyncMock(side_effect=TimeoutError())
        with patch("gflow_cli.mcp.tools.gflow_generate_image", stub):
            first = await bridge.flow_generate_image("timeout-job", "blue sphere")
            second = await bridge.flow_generate_image("timeout-job", "blue sphere")
        self.assertEqual(stub.await_count, 1)
        self.assertEqual(first["status"], "needs_reconciliation")
        self.assertEqual(second["status"], "needs_reconciliation")

    async def test_invalid_model_never_calls_provider(self):
        stub = AsyncMock()
        with patch("gflow_cli.mcp.tools.gflow_generate_image", stub):
            result = await bridge.flow_generate_image("invalid", "blue sphere", model="unknown")
        self.assertEqual(stub.await_count, 0)
        self.assertEqual(result["status"], "error")

    async def test_cached_result_change_detected(self):
        proof = bridge.verified_file({"path": str(self.image)})
        Image.new("RGB", (8, 8), "red").save(self.image)
        with self.assertRaises(ValueError):
            bridge.verified_file(proof)

    async def test_pro_is_sent_as_pro_and_an_existing_id_cannot_switch_models(self):
        stub = AsyncMock(return_value={"status":"completed","files":[str(self.image)]})
        with patch("gflow_cli.mcp.tools.gflow_generate_image",stub):
            first = await bridge.flow_generate_image("pro-job","blue sphere",model="nano-pro")
            repeated = await bridge.flow_generate_image("pro-job","blue sphere",model="nano-pro")
            wrong = await bridge.flow_generate_image("pro-job","blue sphere",model="nano2")
        self.assertEqual(stub.await_count,1)
        self.assertEqual(stub.call_args.kwargs['model'],'nano-pro')
        self.assertEqual(first['requested_model'],'nano-pro')
        self.assertEqual(repeated['status'],'completed')
        self.assertEqual(wrong['status'],'error')

    async def test_each_video_model_keeps_its_identity_on_submission(self):
        for model in ('veo_fast','veo_quality','veo_lite','omni_flash'):
            stub=AsyncMock(return_value={'status':'needs_reconciliation'})
            with patch('gflow_cli.mcp.tools.gflow_generate_video',stub):
                await bridge.flow_generate_video('video-'+model,'a blue sphere',model=model)
                await bridge.flow_generate_video('video-'+model,'a blue sphere',model=model)
            self.assertEqual(stub.await_count,1)
            self.assertEqual(stub.call_args.kwargs['model'],model)

if __name__ == "__main__":
    unittest.main()
