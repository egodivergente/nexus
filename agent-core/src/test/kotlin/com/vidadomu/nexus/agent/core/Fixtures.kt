package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.assets.AssetIndex
import com.vidadomu.nexus.agent.core.cost.PublishedRateEstimator
import com.vidadomu.nexus.agent.core.registry.MediaSpec
import com.vidadomu.nexus.agent.core.registry.ModelRef
import com.vidadomu.nexus.agent.core.registry.ModelRegistry
import com.vidadomu.nexus.agent.core.registry.ModelSchema
import com.vidadomu.nexus.agent.core.registry.ModelSummary
import com.vidadomu.nexus.agent.core.registry.ParamSpec
import com.vidadomu.nexus.agent.core.router.ModelRouter
import com.vidadomu.nexus.agent.core.skills.DirectorySkillSource
import com.vidadomu.nexus.agent.core.skills.SkillStore
import com.vidadomu.nexus.agent.core.spend.GenerationBackend
import com.vidadomu.nexus.agent.core.spend.GenerationPlan
import com.vidadomu.nexus.agent.core.spend.GuardedExecutor
import com.vidadomu.nexus.agent.core.spend.JobStatus
import com.vidadomu.nexus.agent.core.spend.SpendGuard
import com.vidadomu.nexus.agent.core.spend.SubmitResult
import com.vidadomu.nexus.agent.core.tools.ToolDeps
import java.io.File

const val ATLAS = "atlas_cloud"
val KLING_PRO = ModelRef(ATLAS, "kwaivgi/kling-video-o3-pro/image-to-video")
val KLING_STD = ModelRef(ATLAS, "kwaivgi/kling-video-o3-std/image-to-video")
val SEEDANCE = ModelRef(ATLAS, "bytedance/seedance-2.0/image-to-video")
val VEO = ModelRef(ATLAS, "google/veo3.1/image-to-video")
val SEEDREAM = ModelRef(ATLAS, "bytedance/seedream-v5.0-pro/text-to-image")
val BROKEN = ModelRef(ATLAS, "acme/needs-elements/image-to-video")

private fun i2v(ref: ModelRef, name: String, release: String, flagship: Boolean = false, lastFrame: Boolean = true, audio: Boolean = false) = ModelSummary(
    ref = ref, name = name, modalities = setOf("VIDEO"), capabilities = setOf("IMAGE_TO_VIDEO"), inputMode = if (lastFrame) "DUAL_FRAME" else "SINGLE_IMAGE",
    releaseMonth = release, isFlagship = flagship, supportsImageInput = true, supportsFirstFrame = true, supportsLastFrame = lastFrame,
    supportsAudio = audio, maxImages = if (lastFrame) 2 else 1, mediaRequired = true, catalogDurations = listOf("5", "10"),
)

val MODELS: List<ModelSummary> = listOf(
    i2v(KLING_PRO, "Kling O3 Pro", "2026-06", flagship = true, audio = true),
    i2v(KLING_STD, "Kling O3 Std", "2026-06", audio = true),
    i2v(SEEDANCE, "Seedance 2.0", "2026-03", audio = true),
    i2v(VEO, "Veo 3.1", "2026-01", lastFrame = true, audio = true),
    i2v(BROKEN, "Acme Elements", "2026-08"),
    ModelSummary(SEEDREAM, "Seedream 5.0 Pro", modalities = setOf("IMAGE"), capabilities = setOf("TEXT_TO_IMAGE"), inputMode = "TEXT_ONLY", releaseMonth = "2026-05"),
)

private val dualFrame = MediaSpec(inputMode = "DUAL_FRAME", firstFrameField = "image", lastFrameField = "end_image", maxImages = 2, mediaRequired = true)

val SCHEMAS: Map<ModelRef, ModelSchema> = mapOf(
    KLING_PRO to ModelSchema(KLING_PRO, listOf(
        ParamSpec("duration", "Duración", "DURATION", "integer", min = 3.0, max = 15.0, default = "5"),
        ParamSpec("sound", "Sonido", "BOOLEAN", "boolean", default = "false"),
        ParamSpec("multi_shot", "Multiplano", "BOOLEAN", "boolean", default = "false", advanced = true),
    ), dualFrame, source = "https://static.atlascloud.ai/model/schema/kwaivgi-kling-video-o3-pro-image-to-video.json"),
    KLING_STD to ModelSchema(KLING_STD, listOf(
        ParamSpec("duration", "Duración", "DURATION", "integer", min = 3.0, max = 15.0, default = "5"),
        ParamSpec("sound", "Sonido", "BOOLEAN", "boolean", default = "false"),
    ), dualFrame),
    SEEDANCE to ModelSchema(SEEDANCE, listOf(
        ParamSpec("duration", "Duración", "DURATION", "integer", options = listOf("5", "10"), default = "5"),
        ParamSpec("resolution", "Resolución", "RESOLUTION", "string", options = listOf("480p", "720p", "1080p"), default = "720p"),
        ParamSpec("generate_audio", "Audio", "BOOLEAN", "boolean", default = "true"),
        ParamSpec("seed", "Seed", "SEED", "integer", min = -1.0, max = 2147483647.0),
    ), MediaSpec(inputMode = "DUAL_FRAME", firstFrameField = "image", lastFrameField = "last_image", maxImages = 2, mediaRequired = true)),
    VEO to ModelSchema(VEO, listOf(
        ParamSpec("duration", "Duración", "DURATION", "integer", options = listOf("4", "6", "8"), default = "8"),
        ParamSpec("resolution", "Resolución", "RESOLUTION", "string", options = listOf("720p", "1080p"), default = "720p"),
        ParamSpec("generate_audio", "Audio", "BOOLEAN", "boolean", default = "true"),
        ParamSpec("negative_prompt", "Prompt negativo", "LONG_TEXT", "string"),
    ), MediaSpec(inputMode = "DUAL_FRAME", firstFrameField = "image", lastFrameField = "last_frame", maxImages = 2, mediaRequired = true)),
    BROKEN to ModelSchema(BROKEN, listOf(ParamSpec("duration", "Duración", "DURATION", "integer", default = "5")), dualFrame, unsupportedRequired = listOf("elements")),
    SEEDREAM to ModelSchema(SEEDREAM, listOf(
        ParamSpec("size", "Tamaño", "ENUM", "string", options = listOf("2048*2048", "2560*1440"), default = "2048*2048"),
    ), MediaSpec(inputMode = "TEXT_ONLY")),
)

class FakeRegistry(private val models: List<ModelSummary> = MODELS, private val schemas: Map<ModelRef, ModelSchema> = SCHEMAS) : ModelRegistry {
    var schemaCalls = 0
    override suspend fun list() = models
    override suspend fun schema(ref: ModelRef): ModelSchema { schemaCalls++; return schemas[ref] ?: error("sin schema para $ref") }
}

open class FakeBackend : GenerationBackend {
    val submitted = mutableListOf<GenerationPlan>()
    val statuses = mutableMapOf<String, JobStatus>()
    var failNext: Exception? = null
    override suspend fun submit(plan: GenerationPlan): SubmitResult {
        failNext?.let { failNext = null; throw it }
        submitted += plan
        val id = "job_${submitted.size}"
        statuses[id] = JobStatus(id, "PROCESSING", 0.1f)
        return SubmitResult(id, "prov_${submitted.size}")
    }
    override suspend fun status(jobId: String) = statuses[jobId] ?: JobStatus(jobId, "FAILED", error = "desconocido")
    override suspend fun cancel(jobId: String): Boolean { statuses[jobId] = JobStatus(jobId, "CANCELLED"); return true }
}

fun skillsDir(): File = File(System.getProperty("nexus.skills.dir") ?: "../agent-skills/skills")
fun realSkills(): SkillStore = SkillStore(listOf(DirectorySkillSource(skillsDir(), "test")))
fun prices(): PublishedRateEstimator = PublishedRateEstimator.fromJson(
    Thread.currentThread().contextClassLoader.getResource("atlas_published_prices.json")!!.readText(),
)

class Harness(
    val registry: FakeRegistry = FakeRegistry(),
    val backend: FakeBackend = FakeBackend(),
    val assets: AssetIndex = AssetIndex(null),
    var now: Long = 1_790_000_000_000L,
) {
    val skills = realSkills()
    val cost = prices()
    val guard = SpendGuard(clock = { now })
    val executor = GuardedExecutor(backend, guard)
    val router = ModelRouter(registry, skills, cost)
    val deps = ToolDeps(registry, skills, cost, router, guard, executor, assets, clock = { now })
}
