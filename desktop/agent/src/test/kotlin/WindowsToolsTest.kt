import com.vidadomu.nexus.agent.core.llm.*
import com.vidadomu.nexus.agent.core.tools.*
import com.vidadomu.nexus.agent.core.config.AgentMode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

class WindowsToolsTest {
    @Test fun transportKeepsAllGroupsReachableAndPreservesMedia() {
        val names = WindowsToolProvider.connections + WindowsToolProvider.models +
            (1..40).map { "local_$it" } + setOf("use_desktop_tools", "detect_video_scenes", "import_windows_files")
        val tools = names.map { ToolSpec(it, it, buildJsonObject {}) }
        val user = LlmMessage(Role.USER, listOf(ContentBlock.Text("Importa y recorta el vídeo")))
        val request = LlmRequest("test", emptyList(), listOf(user), tools, 1024)
        val defaults = WindowsToolProvider.select(request)
        assertTrue(defaults.tools.size <= 64)
        assertTrue(defaults.tools.any { it.name == "detect_video_scenes" })
        assertTrue(defaults.tools.any { it.name == "prepare_generation" })
        val choose = LlmMessage(Role.ASSISTANT, listOf(ContentBlock.ToolUse("choose", "use_desktop_tools", buildJsonObject { put("group", "connections") })))
        val connections = WindowsToolProvider.select(request.copy(messages = listOf(user, choose)))
        assertTrue(connections.tools.size <= 64)
        assertTrue(connections.tools.any { it.name == "web_search" })
        assertTrue(connections.tools.any { it.name == "import_windows_files" })
        assertEquals(names, (defaults.tools + connections.tools).map { it.name }.toSet())
        assertEquals(defaults.tools, WindowsToolProvider.select(request.copy(messages = listOf(user, choose, user))).tools)
    }

    @Test fun nativeErrorsAreReportedAsErrorsAndCancellationPropagates() { runBlocking {
        val failing = WindowsTools.all(NativeOperations { _, _, _ -> buildJsonObject { put("isError", true); put("error", "No existe") } })
        val session = AgentSession("test", AgentMode.AGENT)
        assertTrue(failing.first { it.name == "import_windows_files" }.run(buildJsonObject {}, session).isError)
        val cancelled = WindowsTools.all(NativeOperations { _, _, _ -> throw kotlinx.coroutines.CancellationException("detenido") })
        assertFailsWith<kotlinx.coroutines.CancellationException> {
            cancelled.first { it.name == "import_windows_files" }.run(buildJsonObject {}, session)
        }
    } }
}
