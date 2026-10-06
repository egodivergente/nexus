import base64
import json
import unittest
from unittest.mock import patch
from nexus_bridge import NexusBridge, BridgeError, decode_result
from nexus_mcp import mcp


class BridgeTests(unittest.TestCase):
    def bridge(self):
        return NexusBridge(adb="adb", hardware_serial="S25SERIAL")

    def test_bundle_decodes_without_inventing_success(self):
        data = {"ok": False, "error": "Parámetro inválido"}
        encoded = base64.urlsafe_b64encode(json.dumps(data).encode()).decode()
        self.assertEqual(decode_result("Result: Bundle[{nexus_result=" + encoded + "}]"), data)

    def test_missing_bridge_has_clear_error(self):
        with self.assertRaises(BridgeError):
            decode_result("No content provider found")

    def test_never_falls_back_to_pixel(self):
        bridge = self.bridge()
        with patch.object(bridge, "_run", side_effect=["List of devices attached\npixel device", "PIXELSERIAL"]):
            with self.assertRaisesRegex(BridgeError, "No se utilizará otro"):
                bridge.device()

    def test_duplicate_transports_choose_same_phone_tls(self):
        bridge = self.bridge()
        with patch.object(bridge, "_run", side_effect=["List of devices attached\nip:5555 device\nip:36969 device", "S25SERIAL", "S25SERIAL", "0", "SM-S938B"]):
            self.assertEqual(bridge.device(), ("ip:36969", "SM-S938B"))

    def test_work_profile_refused(self):
        bridge = self.bridge()
        with patch.object(bridge, "_run", side_effect=["s25 device", "S25SERIAL", "11"]):
            with self.assertRaisesRegex(BridgeError, "perfil personal"):
                bridge.device()

    def test_paid_operation_rejected_before_adb(self):
        bridge = self.bridge()
        with patch.object(bridge, "_run") as run:
            with self.assertRaises(BridgeError):
                bridge.call("generate", {"prompt": "test"})
            run.assert_not_called()

    def test_open_accepts_only_canonical_uuid(self):
        bridge = self.bridge()
        with patch.object(bridge, "_run") as run:
            with self.assertRaises(BridgeError):
                bridge.open_draft("x; input tap 1 1")
            run.assert_not_called()

    def test_prompt_and_native_keys_roundtrip_without_shell_text(self):
        bridge = self.bridge()
        payload = {"prompt": 'Luz cálida; $(echo secreto) " &', "parameters": {"keep_original_sound": "false", "character_orientation": "video"}}
        response = base64.urlsafe_b64encode(b'{"ok":true}').decode()
        with patch.object(bridge, "device", return_value=("phone", "SM-S938B")), patch.object(bridge, "_run", return_value=f"Bundle[nexus_result={response}]") as run:
            self.assertTrue(bridge.call("prepare_draft", payload)["ok"])
            args = run.call_args.args[0]
            self.assertEqual(json.loads(base64.urlsafe_b64decode(args[-1])), payload)
            self.assertNotIn(payload["prompt"], args)

    def test_nonexistent_draft_cannot_report_opened(self):
        bridge = self.bridge()
        with patch.object(bridge, "call", return_value={"ok": True, "drafts": []}), patch.object(bridge, "_run") as run:
            with self.assertRaisesRegex(BridgeError, "no está disponible"):
                bridge.open_draft("1e4c5026-895b-4543-a044-7726d293fd37")
            run.assert_not_called()

    def test_status_targets_agent_variant(self):
        bridge = self.bridge()
        response = base64.urlsafe_b64encode(b'{"ok":true}').decode()
        with patch.object(bridge, "device", return_value=("phone", "SM-S938B")), patch.object(bridge, "_run", return_value=f"Bundle[nexus_result={response}]") as run:
            bridge.call("status")
            self.assertIn("content://com.vidadomu.nexus.agent.agent", run.call_args.args[0])

    def test_open_targets_real_activity_in_agent_variant(self):
        bridge = self.bridge()
        draft_id = "1e4c5026-895b-4543-a044-7726d293fd37"
        with patch.object(bridge, "call", return_value={"ok": True, "drafts": [{"draftId": draft_id}]}), patch.object(bridge, "device", return_value=("phone", "SM-S938B")), patch.object(bridge, "_run", return_value="Starting") as run:
            self.assertTrue(bridge.open_draft(draft_id)["ok"])
            self.assertIn("com.vidadomu.nexus.agent/com.vidadomu.nexus.MainActivity", run.call_args.args[0])


class McpTests(unittest.IsolatedAsyncioTestCase):
    async def test_mcp_exposes_only_specific_operations(self):
        tools = await mcp.list_tools()
        self.assertEqual({tool.name for tool in tools}, {"nexus_status", "nexus_list_models", "nexus_get_model", "nexus_prepare_draft", "nexus_list_drafts", "nexus_open_draft", "nexus_discard_draft"})
        self.assertTrue(next(tool for tool in tools if tool.name == "nexus_status").annotations.readOnlyHint)
        self.assertFalse(next(tool for tool in tools if tool.name == "nexus_prepare_draft").annotations.readOnlyHint)
        self.assertTrue(next(tool for tool in tools if tool.name == "nexus_discard_draft").annotations.destructiveHint)


if __name__ == "__main__":
    unittest.main()
