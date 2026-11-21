package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.assets.*
import java.io.File
import java.nio.file.Files
import kotlin.test.*
import org.junit.jupiter.api.Test

class LibraryFileDeletionTest {
    private fun <T> inFolder(block: (File) -> T): T {
        val root = Files.createTempDirectory("nexus-erase-test").toFile()
        try { return block(root) } finally { root.deleteRecursively() }
    }
    private fun media(dir: File, name: String) = File(dir, name).apply { parentFile.mkdirs(); writeText(name) }
    private fun asset(id: String, file: File) = Asset(id, file.toURI().toString(), AssetType.IMAGE, displayName = id, createdAt = 1)
    private fun target(a: Asset) = DeletionTarget("asset:" + a.id, a.displayName, setOf(a.id), setOf(a.uri))

    @Test fun onlyFilesInsideNexusFoldersCountAsPrivate() = inFolder { root ->
        val files = File(root, "files")
        val erase = LibraryFileDeletion(files)
        val imported = media(files, "agent_media/agent_1.png")
        val blob = media(files, "assistant/sync/workspaces/w/blobs/" + "a".repeat(64) + ".png")
        val journal = media(files, "assistant/sync/workspaces/w/journal.json")
        val gallery = media(root, "Documents/ALEXIA/Nexus/Imágenes/foto.png")
        val other = media(files, "skills/notes.md")
        assertEquals(imported.canonicalFile, erase.privateFile(imported.toURI().toString()))
        assertEquals(imported.canonicalFile, erase.privateFile(imported.path))
        assertEquals(blob.canonicalFile, erase.privateFile(blob.toURI().toString()))
        assertNull(erase.privateFile(journal.toURI().toString()))
        assertNull(erase.privateFile(gallery.toURI().toString()))
        assertNull(erase.privateFile(other.toURI().toString()))
        assertNull(erase.privateFile(File(files, "agent_media/../skills/notes.md").path))
        assertNull(erase.privateFile(File(files, "agent_media").toURI().toString()))
        assertNull(erase.privateFile("content://media/external/images/1"))
        assertNull(erase.privateFile("https://cdn.example.org/a.png"))
    }

    @Test fun erasesPrivateBytesButOnlyForgetsExternalOriginals() = inFolder { root ->
        val files = File(root, "files")
        val imported = media(files, "agent_media/agent_1.png")
        val gallery = media(root, "DCIM/foto.png")
        val a = asset("a", imported); val b = asset("b", gallery)
        val erase = LibraryFileDeletion(files)
        val plan = erase.plan(IndexData(assets = listOf(a, b)), listOf(target(a), target(b)))
        assertEquals(2, plan.removable.size)
        assertEquals(listOf(imported.canonicalFile), plan.files)
        assertEquals(1, plan.external)
        assertEquals(1, erase.delete(plan.files))
        assertFalse(imported.exists())
        assertTrue(gallery.exists())
    }

    @Test fun whatSomethingElseUsesIsNeitherErasedNorRemoved() = inFolder { root ->
        val files = File(root, "files")
        val story = asset("story", media(files, "agent_media/agent_story.png"))
        val identity = asset("identity", media(files, "agent_media/agent_identity.png"))
        val shared = media(files, "agent_media/agent_shared.png")
        val copy = asset("copy", shared); val twin = asset("twin", shared)
        val montage = asset("montage", media(files, "cut/exports/montaje_1.mp4"))
        val result = asset("result", media(files, "custom_outputs/out_1.png"))
        val free = asset("free", media(files, "agent_media/agent_free.png"))
        media(files, "cut/projects/p1/project.json").writeText("""{"media":[{"uri":"file:///x/cut/exports/montaje_1.mp4"}]}""")
        val index = IndexData(assets = listOf(story, identity, copy, twin, montage, result, free),
            characters = listOf(Character("c", null, "Mateo", referenceAssetIds = listOf("identity"))),
            story = StoryState(links = listOf(StoryLink("l", "p", "story", "shot", "s"))))
        val targets = listOf(story, identity, copy, montage, result, free).map(::target)
        val plan = LibraryFileDeletion(files).plan(index, targets, cutProjectsDir = File(files, "cut/projects"), inUse = listOf(result.uri))
        assertEquals(listOf("asset:free"), plan.removable.map { it.key })
        assertEquals(setOf("asset:story", "asset:identity", "asset:copy", "asset:montage", "asset:result"), plan.blocked.keys)
        assertEquals("se usa en el guion", plan.blocked["asset:story"])
        assertEquals("es referencia de un personaje", plan.blocked["asset:identity"])
        assertEquals("se usa en un montaje de Nexus Cut", plan.blocked["asset:montage"])
        assertEquals(listOf(File(files, "agent_media/agent_free.png").canonicalFile), plan.files)
    }

    @Test fun twoTilesOfTheSameFileCanGoTogether() = inFolder { root ->
        val files = File(root, "files")
        val shared = media(files, "agent_media/agent_shared.png")
        val a = asset("a", shared); val b = asset("b", shared)
        val plan = LibraryFileDeletion(files).plan(IndexData(assets = listOf(a, b)), listOf(target(a), target(b)))
        assertTrue(plan.blocked.isEmpty())
        assertEquals(listOf(shared.canonicalFile), plan.files)
    }

    @Test fun aFileAChatShowsIsErasedWithAWarning() = inFolder { root ->
        val files = File(root, "files")
        val a = asset("a", media(files, "agent_media/agent_chat.png"))
        media(files, "assistant/conversations/c1.json").writeText("""{"attachments":["file:///data/user/0/x/files/agent_media/agent_chat.png"]}""")
        val plan = LibraryFileDeletion(files).plan(IndexData(assets = listOf(a)), listOf(target(a)), chatsDir = File(files, "assistant/conversations"))
        assertEquals(1, plan.inChats)
        assertEquals(1, plan.files.size)
    }

    @Test fun deleteChecksEveryPathAgainAndNeverRecurses() = inFolder { root ->
        val files = File(root, "files")
        val outside = media(root, "Documents/keep.png")
        val folder = File(files, "agent_media/sub").apply { mkdirs() }
        val inside = media(folder, "agent_x.png")
        val erase = LibraryFileDeletion(files)
        assertEquals(0, erase.delete(listOf(outside, File(files, "agent_media/sub"))))
        assertTrue(outside.exists()); assertTrue(inside.exists())
    }

    @Test fun syncCopiesOfTheSameBytesAreErasedToo() = inFolder { root ->
        val files = File(root, "files")
        val imported = media(files, "agent_media/agent_1.png")
        val hash = com.vidadomu.nexus.agent.core.sync.SyncBlobs.hash(imported)
        val drive = File(files, "assistant/sync/workspaces/w/blobs/$hash.png").apply { parentFile.mkdirs(); writeText(imported.readText()) }
        val device = File(files, "device-sync/blobs/$hash").apply { parentFile.mkdirs(); writeText(imported.readText()) }
        val unrelated = media(files, "device-sync/blobs/" + "b".repeat(64))
        val a = asset("a", imported)
        val plan = LibraryFileDeletion(files).plan(IndexData(assets = listOf(a)), listOf(target(a)))
        assertEquals(listOf(imported, drive, device).map { it.canonicalFile }.toSet(), plan.files.toSet())
        assertEquals(setOf(hash), plan.hashes)
        assertFalse(unrelated.canonicalFile in plan.files)
    }
}
