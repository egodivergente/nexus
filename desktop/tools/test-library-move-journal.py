#!/usr/bin/env python3
"""Real bundled JVM move -> immutable sync journal -> isolated peer, without a network."""
import copy
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'desktop'))
from agent_host import AgentHost
from library_operations import LibraryOperations

HARNESS=r'''
import java.io.*;
import java.nio.file.*;
import java.util.*;
import kotlinx.serialization.json.*;
import com.vidadomu.nexus.agent.core.sync.*;

public class LibraryMoveJournalProof {
  static class Cloud implements SyncRemote {
    final Map<String,byte[]> files=new LinkedHashMap<>();
    final List<SyncCommit> written=new ArrayList<>();
    public List<SyncObject> commits() {
      List<SyncObject> items=new ArrayList<>();
      files.forEach((id,bytes)->items.add(new SyncObject(id,"commit-"+id+".json",bytes.length)));
      return items;
    }
    public byte[] readCommit(SyncObject object) {return files.get(object.getId());}
    public void putCommit(SyncCommit commit,byte[] bytes) {files.put(commit.getId(),bytes.clone());written.add(commit);}
    public void putBlob(String name,File file) {throw new AssertionError("No blob or network writes in journal proof");}
    public void getBlob(String name,File file) {throw new AssertionError("No blob or network reads in journal proof");}
  }
  static Map<String,JsonObject> rows(String path) throws Exception {
    JsonObject value=(JsonObject)Json.Default.parseToJsonElement(Files.readString(Path.of(path)));
    Map<String,JsonObject> result=new LinkedHashMap<>();
    value.forEach((key,row)->result.put(key,(JsonObject)row));return result;
  }
  static SyncResult cycle(SyncJournal journal,Map<String,JsonObject> rows) {
    SyncResult result=journal.exchange(rows);journal.acknowledge(result.getRows());return result;
  }
  static void require(boolean value,String message) {if(!value)throw new AssertionError(message);}
  public static void main(String[] args) throws Exception {
    Cloud cloud=new Cloud();Map<String,JsonObject> before=rows(args[0]),after=rows(args[1]);
    File root=new File(args[2]);
    SyncJournal pc=new SyncJournal(new File(root,"pc.json"),cloud,"PC aislado");
    SyncJournal phone=new SyncJournal(new File(root,"peer.json"),cloud,"Peer aislado");
    cycle(pc,before);SyncResult initialPeer=cycle(phone,Collections.emptyMap());
    int baselineCommits=cloud.written.size();
    SyncResult changed=cycle(pc,after);
    require(changed.getUploaded()==1,"Exactly one immutable move commit expected");
    List<SyncRevision> delta=cloud.written.subList(baselineCommits,cloud.written.size()).stream().flatMap(c->c.getRevisions().stream()).toList();
    require(delta.size()==1 && delta.get(0).getKey().equals("assets:move-source"),"Only the changed asset association may be revised");
    require(delta.get(0).getValue().get("projectId").toString().equals("\"b\""),"Destination was not recorded");
    require(delta.get(0).getValue().get("libraryDeleted").toString().equals("true"),"Redundant association retirement was not recorded");
    SyncResult received=cycle(phone,initialPeer.getRows());
    require(received.getRows().equals(after),"Peer did not receive the exact moved canonical index rows");
    require(received.getConflicts().isEmpty(),"A sequential move introduced an unexpected conflict");
    before.forEach((key,row)->{if(key.startsWith("generations:"))require(row.equals(received.getRows().get(key)),"Generation history was modified");});
    System.out.println("PASS actual bundled SyncJournal records projectId/libraryDeleted delta and round-trips unchanged generation history");
  }
}
'''


def main():
    runtime=ROOT/'desktop/agent/runtime'
    java,javac=shutil.which('java'),shutil.which('javac')
    if not java or not javac or not (runtime/'core.jar').is_file():
        raise SystemExit('Java 17, javac and the existing bundled core.jar are required; runtime is never rebuilt.')
    with tempfile.TemporaryDirectory(prefix='nexus-move-journal-') as directory:
        root=Path(directory);profile=root/'profile';assistant=profile/'assistant';media=profile/'media'
        assistant.mkdir(parents=True);media.mkdir()
        payload=b'Isolated exact original: no paid generation or provider request.'
        first='1'*32+'.png';second='2'*32+'.png'
        (media/first).write_bytes(payload);(media/second).write_bytes(payload)
        assets=[{'id':'move-source','uri':'/media/'+first,'type':'IMAGE','displayName':'Original A','source':'GENERATED','role':'OUTPUT','createdAt':1,'projectId':'a','generationId':'generation'},
                {'id':'move-destination','uri':'/media/'+second,'type':'IMAGE','displayName':'Original B','source':'SHARED','role':'REFERENCE','createdAt':2,'projectId':'b','tags':['preserved']}]
        index={'schemaVersion':1,'projects':[{'id':'a','name':'A','createdAt':1},{'id':'b','name':'B','createdAt':2}],
               'assets':assets,'generations':[{'id':'generation','nexusJobId':'job','providerId':'local_media','modelId':'local/reference','modality':'IMAGE','prompt':'Isolated fixture',
               'createdAt':1,'projectId':'a','outputAssetIds':['move-source'],'status':'COMPLETED'}]}
        (assistant/'index.json').write_text(json.dumps(index),'utf-8')
        state={'jobs':[{'id':'job','modality':'IMAGE','status':'COMPLETED','outputs':['/media/'+first],'parameters':{'__project_id':'a'},'startedAt':1}],
               'draft':{'prompt':'Unchanged isolated draft'}}
        state_before=copy.deepcopy(state)
        host=AgentHost(ROOT/'desktop',profile,lambda _: {},lambda *_:None)
        try:
            host.start();before=host.exchange('snapshot',{})['index']
            result=LibraryOperations(host,lambda:state).command({'op':'move','entryIds':['asset:move-source'],'projectId':'b','sourceProjectId':'a'})
            after=host.exchange('snapshot',{})['index']
            assert result['status']=='completed' and result['movedEntries']==1 and result['mergedAssets']==1
            assert before['generations']==after['generations'] and state==state_before
            assert before['assets'][1]==after['assets'][1]
            assert all((media/name).read_bytes()==payload for name in (first,second))
        finally:host.close()
        # SyncWorkspace normally replaces local URIs by this exact portable
        # content identity before handing the same complete asset rows to Journal.
        portable='nexus-sync:'+hashlib.sha256(payload).hexdigest()+'.png'
        for name,value in (('before',before),('after',after)):
            rows={}
            for collection in ('projects','scenes','shots','characters','assets','generations'):
                for row in value.get(collection,[]):
                    row=dict(row)
                    if collection=='assets':row['uri']=portable
                    rows[collection+':'+row['id']]=row
            (root/(name+'.json')).write_text(json.dumps(rows),'utf-8')
        source=root/'LibraryMoveJournalProof.java';source.write_text(HARNESS,'utf-8')
        flags={'check':True,'capture_output':True,'text':True,'encoding':'utf-8','timeout':45,
               'creationflags':getattr(subprocess,'CREATE_NO_WINDOW',0)}
        compiled=subprocess.run([javac,'-J-Xmx128m','-cp',str(runtime/'*'),'-d',str(root),str(source)],**flags)
        result=subprocess.run([java,'-Xms16m','-Xmx96m','-XX:+UseSerialGC','-cp',str(root)+os.pathsep+str(runtime/'*'),
                               'LibraryMoveJournalProof',str(root/'before.json'),str(root/'after.json'),str(root/'journal')],**flags)
        print('PASS actual bundled importAssets updates existing IDs and preserves original bytes, destination metadata and job history')
        print(result.stdout.strip())
        print(json.dumps({'runtimeCoreSha256':hashlib.sha256((runtime/'core.jar').read_bytes()).hexdigest(),
                          'mode':'Existing bundled runtime, disposable native profile and in-memory cloud; no network or phone writes'}))


if __name__=='__main__':main()
