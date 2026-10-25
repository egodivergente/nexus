package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.skills.DirectorySkillSource
import com.vidadomu.nexus.agent.core.skills.SkillParseCache
import com.vidadomu.nexus.agent.core.skills.SkillStore
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The persistent parse cache must return exactly what a full parse returns, and follow edits. */
class SkillParseCacheTest {
    @Test fun cachedStoreEqualsFullParse() {
        val file = File.createTempFile("skills-cache", ".json").apply { delete() }
        val reference = realSkills().all().sortedBy { it.id }
        val first = SkillStore(listOf(DirectorySkillSource(skillsDir(), "test")), cache = SkillParseCache(file))
        assertTrue(file.length() > 0, "the first load writes the cache")
        val second = SkillStore(listOf(DirectorySkillSource(skillsDir(), "test")), cache = SkillParseCache(file))
        assertEquals(reference, first.all().sortedBy { it.id })
        assertEquals(reference, second.all().sortedBy { it.id })
        file.delete()
    }

    @Test fun anEditedSkillIsParsedAgain() {
        val dir = createTempDir("skills")
        val skill = File(dir, "demo/SKILL.md").apply { parentFile.mkdirs() }
        fun write(title: String) = skill.writeText("---\nid: demo\nkind: expertise\ntitle: $title\nversion: 1\n---\n# $title\nCuerpo.\n")
        val cache = File(dir, "cache.json")
        write("Primera")
        assertEquals("Primera", SkillStore(listOf(DirectorySkillSource(dir)), cache = SkillParseCache(cache)).get("demo")?.title)
        write("Segunda")
        assertEquals("Segunda", SkillStore(listOf(DirectorySkillSource(dir)), cache = SkillParseCache(cache)).get("demo")?.title)
        dir.deleteRecursively()
    }

    @Test fun aCorruptCacheFallsBackToParsing() {
        val file = File.createTempFile("skills-cache", ".json").apply { writeText("{not json") }
        assertEquals(realSkills().all().size, SkillStore(listOf(DirectorySkillSource(skillsDir(), "test")), cache = SkillParseCache(file)).all().size)
        file.delete()
    }
}
