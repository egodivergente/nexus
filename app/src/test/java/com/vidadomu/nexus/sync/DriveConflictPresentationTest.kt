package com.vidadomu.nexus.sync

import com.vidadomu.nexus.sync.DriveConflictFixtures.ASSET_ID
import com.vidadomu.nexus.sync.DriveConflictFixtures.AT
import com.vidadomu.nexus.sync.DriveConflictFixtures.PROJECT_ID
import com.vidadomu.nexus.sync.DriveConflictFixtures.asset
import com.vidadomu.nexus.sync.DriveConflictFixtures.assetConflict
import com.vidadomu.nexus.sync.DriveConflictFixtures.conflict
import com.vidadomu.nexus.sync.DriveConflictFixtures.project
import com.vidadomu.nexus.sync.DriveConflictFixtures.status
import com.vidadomu.nexus.sync.DriveConflictFixtures.version
import java.util.TimeZone
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class DriveConflictPresentationTest {
    private val utc=TimeZone.getTimeZone("UTC")
    private fun summarize(status:JsonObject,projects:Map<String,String> = emptyMap())=DriveConflictPresentation.summarize(status,projects,utc)

    @Test fun titlesUseNamesAndNeverTechnicalIds() {
        val summary=summarize(status(
            assetConflict(1,"Retrato final.png"),
            conflict("projects:$PROJECT_ID",version("p1","Pixel 8 Pro",AT,project(null,"A")),version("p2","Windows",AT,project(null,"B"))),
        ))
        val titles=summary.groups.flatMap {group->group.items.map {it.title}}
        assertEquals(listOf("Proyecto sin nombre","Archivo «Retrato final.png»"),titles)
        for(title in titles) {
            assertFalse(title.contains(PROJECT_ID));assertFalse(title.contains("asset-1"));assertFalse(title.contains(":"))
        }
    }

    @Test fun showsOnlyMetadataThatExistsAndNeverInventsDates() {
        val item=summarize(status(conflict("assets:$ASSET_ID",
            version("v1","SM-S938B",0,asset("clip.png","nexus-sync:x.png",width=1024)),
            version("v2",null,null,asset("clip.png","nexus-sync:x.png",width=1024,height=768)),
        ))).groups.single().items.single()
        val (first,second)=item.versions
        assertNull(first.date);assertEquals("Fecha no registrada",first.dateLabel)
        assertNull(second.device);assertEquals("Dispositivo sin nombre",second.deviceLabel)
        assertNull(item.field(first,"Resolución"))
        assertEquals("1024 × 768",item.field(second,"Resolución"))
        val labels=item.versions.flatMap {version->version.fields.map {it.label}}.toSet()
        assertFalse(labels.any {it.contains("Miniatura") || it.contains("Tamaño")})
        assertNull(item.field(first,"Duración"));assertNull(item.field(first,"Formato"))
        assertEquals(setOf("Resolución"),item.differingFields)
    }

    @Test fun formatsRealTimestampsInTheGivenZone() {
        val text=DriveConflictPresentation.formatDate(AT,utc)!!
        assertTrue(text,text.contains("2026") && text.contains("10:15") && text.startsWith("20 "))
        assertNull(DriveConflictPresentation.formatDate(-5,utc))
    }

    @Test fun distinguishesMissingOriginalFromCopiedAndRemoval() {
        val item=summarize(status(conflict("assets:$ASSET_ID",
            version("drive","SM-S938B",AT,asset("a.png","nexus-sync:aa.png")),
            version("local","Windows",AT,asset("a.png","file:///storage/a.png")),
            version("gone","Pixel 8 Pro",AT,null),
        ),missing=2)).let {summary->
            assertEquals(2,summary.missingMedia)
            summary.groups.single().items.single()
        }
        assertEquals(DriveOriginalState.REFERENCED_IN_DRIVE,item.versions[0].original)
        assertEquals(DriveOriginalState.NOT_REFERENCED_IN_DRIVE,item.versions[1].original)
        assertTrue(item.versions[2].removes);assertNull(item.versions[2].original);assertTrue(item.versions[2].fields.isEmpty())
        assertTrue(DriveConflictPresentation.ORIGINAL in item.differingFields)
        assertFalse(item.onlyHiddenDifferences)
    }

    @Test fun groupsInStableOrderWithReadableSummary() {
        val summary=summarize(status(
            assetConflict(1),assetConflict(2),
            conflict("projects:$PROJECT_ID",version("p1","A",AT,project("Corto")),version("p2","B",AT,project("Corto final"))),
        ))
        assertEquals(listOf(DriveConflictKind.PROJECTS,DriveConflictKind.ASSETS),summary.groups.map {it.kind})
        assertEquals(3,summary.total)
        assertEquals("3 elementos con versiones distintas",summary.headline)
        assertEquals("1 proyecto · 2 archivos",summary.breakdown)
        assertEquals("Proyecto «Corto»",summary.item("projects:$PROJECT_ID")!!.title)
    }

    @Test fun pagesLargeListsInFixedSteps() {
        val items=(1..12).toList()
        assertEquals(5,DriveConflictPresentation.page(items,1).size)
        assertEquals(10,DriveConflictPresentation.page(items,2).size)
        assertEquals(12,DriveConflictPresentation.page(items,3).size)
        assertEquals(5,DriveConflictPresentation.page(items,0).size)
    }

    @Test fun repeatedNamesAreDisambiguatedWithoutIds() {
        val items=summarize(status(assetConflict(1,"toma.png"),assetConflict(2,"toma.png"))).groups.single().items
        assertEquals(listOf("Archivo «toma.png» (1)","Archivo «toma.png» (2)"),items.map {it.title})
    }

    @Test fun malformedEntriesAreCountedNotGuessed() {
        val summary=summarize(buildJsonObject {
            put("missingMedia",-3)
            put("conflicts",buildJsonArray {
                add(JsonPrimitive("basura"))
                add(buildJsonObject {put("key","assets:x")})
                add(conflict("assets:y",version("solo","A",AT,null)))
                add(conflict("assets:z",buildJsonObject {put("device","sin id")},version("ok","B",AT,null)))
                add(assetConflict(3))
            })
        })
        assertEquals(4,summary.malformed)
        assertEquals(1,summary.total)
        assertEquals(0,summary.missingMedia)
    }

    @Test fun resolvesProjectNamesLocallyAndAdmitsUnknownOnes() {
        val known=summarize(status(conflict("assets:$ASSET_ID",
            version("v1","A",AT,asset("a.png","nexus-sync:a.png",projectId=PROJECT_ID)),
            version("v2","B",AT,asset("b.png","nexus-sync:a.png",projectId="otro")),
        )),mapOf(PROJECT_ID to "Videoclip")).groups.single().items.single()
        assertEquals("Videoclip",known.field(known.versions[0],"Proyecto"))
        assertEquals("Proyecto no disponible en este dispositivo",known.field(known.versions[1],"Proyecto"))
        assertEquals(setOf("Nombre","Proyecto"),known.differingFields)
    }

    @Test fun flagsDifferencesOutsideVisibleFields() {
        val hidden=buildJsonObject {put("id",PROJECT_ID);put("name","Igual");put("createdAt",1)}
        val other=buildJsonObject {put("id",PROJECT_ID);put("name","Igual");put("createdAt",2)}
        val item=summarize(status(conflict("projects:$PROJECT_ID",version("a","A",AT,hidden),version("b","B",AT,other)))).groups.single().items.single()
        assertTrue(item.differingFields.isEmpty())
        assertTrue(item.onlyHiddenDifferences)
    }
}
