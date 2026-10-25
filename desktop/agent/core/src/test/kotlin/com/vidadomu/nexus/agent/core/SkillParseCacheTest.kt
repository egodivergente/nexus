package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.skills.DirectorySkillSource
import com.vidadomu.nexus.agent.core.skills.SkillParseCache
import com.vidadomu.nexus.agent.core.skills.SkillStore
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SkillParseCacheTest {
    private fun tempDir(): File = Files.createTempDirectory("skills-cache").toFile()
    private fun write(dir: File, id: String, title: String) = File(dir, "$id/SKILL.md").apply {
        parentFile.mkdirs()
        writeText("---\nid: $id\nkind: expertise\ntitle: $title\nversion: 1\n---\n# $title\nCuerpo.\n")
    }
    private fun store(dir: File, cache: File) = SkillStore(listOf(DirectorySkillSource(dir, "t")), cache = SkillParseCache(cache))

    @Test fun cachedStoreEqualsFullParse() {
        val dir = tempDir()
        try {
            val file = File(dir, "cache.json")
            val reference = realSkills().all().sortedBy { it.id }
            val first = SkillStore(listOf(DirectorySkillSource(skillsDir(), "test")), cache = SkillParseCache(file))
            assertTrue(file.length() > 0, "the first load writes the cache")
            val second = SkillStore(listOf(DirectorySkillSource(skillsDir(), "test")), cache = SkillParseCache(file))
            assertEquals(reference, first.all().sortedBy { it.id })
            assertEquals(reference, second.all().sortedBy { it.id })
            assertEquals(realSkills().errors, second.errors)
        } finally { dir.deleteRecursively() }
    }

    @Test fun anEditedSkillIsParsedAgain() {
        val dir = tempDir()
        try {
            val cache = File(dir.parentFile, dir.name + "-cache.json")
            write(dir, "demo", "Primera")
            assertEquals("Primera", store(dir, cache).get("demo")?.title)
            write(dir, "demo", "Segunda")
            assertEquals("Segunda", store(dir, cache).get("demo")?.title)
            cache.delete()
        } finally { dir.deleteRecursively() }
    }

    @Test fun aDeletedSkillDisappearsAndItsEntryIsDropped() {
        val root = tempDir()
        try {
            val skills = File(root, "skills"); val cache = File(root, "cache.json")
            write(skills, "keep", "Keep"); val gone = write(skills, "gone", "Gone")
            assertEquals(setOf("keep", "gone"), store(skills, cache).all().map { it.id }.toSet())
            val before = cache.readText()
            gone.parentFile.deleteRecursively()
            val after = store(skills, cache)
            assertNull(after.get("gone"))
            assertEquals(listOf("keep"), after.all().map { it.id })
            assertTrue(before.contains("Gone") && !cache.readText().contains("Gone"), "removed entry must leave the cache")
        } finally { root.deleteRecursively() }
    }

    @Test fun aCorruptCacheFallsBackToParsingAndIsRepaired() {
        val root = tempDir()
        try {
            val cache = File(root, "cache.json").apply { writeText("{not json") }
            assertEquals(realSkills().all().size, SkillStore(listOf(DirectorySkillSource(skillsDir(), "test")), cache = SkillParseCache(cache)).all().size)
            assertTrue(cache.readText().startsWith("{\"version\""), "a successful load rewrites a corrupt cache")
        } finally { root.deleteRecursively() }
    }

    @Test fun anOutdatedFormatIsIgnored() {
        val root = tempDir()
        try {
            val skills = File(root, "skills"); val cache = File(root, "cache.json")
            write(skills, "demo", "Real")
            store(skills, cache)
            val text = cache.readText()
            assertTrue(text.startsWith("{\"version\":1,"))
            cache.writeText(text.replace("\"title\":\"Real\"", "\"title\":\"Stale\"").replaceFirst("{\"version\":1,", "{\"version\":999,"))
            assertEquals("Real", store(skills, cache).get("demo")?.title)
        } finally { root.deleteRecursively() }
    }

    @Test fun parseErrorsAreReportedAndNeverCached() {
        val root = tempDir()
        try {
            val skills = File(root, "skills"); val cache = File(root, "cache.json")
            File(skills, "bad/SKILL.md").apply { parentFile.mkdirs(); writeText("no header") }
            write(skills, "ok", "Ok")
            repeat(2) {
                val s = store(skills, cache)
                assertEquals(1, s.errors.size)
                assertEquals(listOf("ok"), s.all().map { it.id })
            }
        } finally { root.deleteRecursively() }
    }

    @Test fun aFailedWriteReportsFalseAndTouchesNothing() {
        val root = tempDir()
        try {
            val blocker = File(root, "blocker").apply { writeText("x") }
            val cache = SkillParseCache(File(blocker, "c.json"))
            cache.parse("---\nid: demo\nkind: expertise\ntitle: Demo\n---\nCuerpo.\n", "t/demo/SKILL.md")
            assertFalse(cache.save(), "a parent that is a file cannot hold the cache")
            assertEquals("x", blocker.readText())
            assertEquals(listOf("blocker"), root.list()!!.toList())
        } finally { root.deleteRecursively() }
    }

    @Test fun rewritingReplacesTheFileAndLeavesNoTemporaryFiles() {
        val root = tempDir()
        try {
            val skills = File(root, "skills"); val cacheDir = File(root, "cache"); val cache = File(cacheDir, "c.json")
            write(skills, "demo", "Primera"); store(skills, cache)
            write(skills, "demo", "Segunda"); store(skills, cache)
            assertEquals(listOf("c.json"), cacheDir.list()!!.toList())
            assertTrue(cache.readText().contains("Segunda") && !cache.readText().contains("Primera"))
        } finally { root.deleteRecursively() }
    }
}
