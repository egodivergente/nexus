package com.vidadomu.nexus.sync

import kotlinx.serialization.json.*

/** Synthetic conflict rows shaped like SyncWorkspace status output. No real data. */
object DriveConflictFixtures {
    const val AT=1792491300000L
    const val ASSET_ID="0d8c2f0e-7b0a-4c9e-9a51-2b7c51d7a001"
    const val PROJECT_ID="5f1e9c3a-1111-4c9e-9a51-2b7c51d7a002"

    fun version(id:String,device:String?,at:Long?,value:JsonObject?)=buildJsonObject {
        put("id",id)
        device?.let {put("device",it)}
        at?.let {put("at",it)}
        put("value",value?:JsonNull)
    }
    fun conflict(key:String,vararg versions:JsonObject)=buildJsonObject {put("key",key);put("versions",JsonArray(versions.toList()))}
    fun status(vararg conflicts:JsonObject,missing:Int=0)=buildJsonObject {
        put("state",if(conflicts.isEmpty())"partial" else "conflicts")
        put("missingMedia",missing)
        put("conflicts",JsonArray(conflicts.toList()))
    }
    fun asset(name:String?,uri:String,width:Int?=null,height:Int?=null,projectId:String?=null)=buildJsonObject {
        put("id",ASSET_ID);put("uri",uri);put("type","IMAGE")
        name?.let {put("displayName",it)}
        width?.let {put("width",it)};height?.let {put("height",it)}
        projectId?.let {put("projectId",it)}
        put("createdAt",AT)
    }
    fun project(name:String?,description:String?=null)=buildJsonObject {
        put("id",PROJECT_ID);name?.let {put("name",it)};description?.let {put("description",it)};put("createdAt",AT)
    }
    fun assetConflict(index:Int,name:String="foto-$index.png")=conflict("assets:asset-$index",
        version("a-$index","SM-S938B",AT,asset(name,"nexus-sync:abc$index.png",1024,768)),
        version("b-$index","Windows",AT+60_000,asset(name,"nexus-sync:abc$index.png",2048,1536)))
}
