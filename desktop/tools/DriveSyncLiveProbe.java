import com.vidadomu.nexus.agent.core.sync.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import kotlin.jvm.functions.Function0;
import kotlinx.serialization.json.*;
import okhttp3.*;

/** Explicit integration probe: reads a token from stdin, uses only an isolated test folder. */
class DriveSyncLiveProbe {
    static JsonObject object(String text) {return (JsonObject)Json.Default.parseToJsonElement(text);}
    static Map<String,JsonObject> rows(String name) {return Map.of("projects:verification",object("{\"id\":\"verification\",\"name\":\""+name+"\",\"createdAt\":1}"));}
    static SyncResult cycle(SyncJournal journal,Map<String,JsonObject> rows) {SyncResult result=journal.exchange(rows);journal.acknowledge(result.getRows());return result;}
    public static void main(String[] arguments) throws Exception {
        String token=new BufferedReader(new InputStreamReader(System.in)).readLine();
        if(token==null||token.isBlank())throw new IllegalArgumentException("Token missing from private pipe");
        Path directory=Paths.get(arguments[0]);Files.createDirectories(directory);
        OkHttpClient http=new OkHttpClient.Builder().followRedirects(false).build();
        Function0<String> tokens=()->token;
        String root=DriveRemote.Companion.findOrCreateRoot(tokens);
        String metadata="{\"name\":\"Nexus verification "+UUID.randomUUID()+"\",\"mimeType\":\"application/vnd.google-apps.folder\",\"parents\":[\""+root+"\"]}";
        String folder;
        try(Response response=http.newCall(new Request.Builder().url("https://www.googleapis.com/drive/v3/files?fields=id").header("Authorization","Bearer "+token).post(RequestBody.create(metadata,MediaType.get("application/json"))).build()).execute()) {
            if(!response.isSuccessful())throw new IOException("Verification folder failed: "+response.code());
            folder=((JsonPrimitive)object(response.body().string()).get("id")).getContent();
        }
        try {
            DriveRemote remote=new DriveRemote(tokens,folder,http,directory.resolve("uploads").toFile());
            SyncJournal a=new SyncJournal(directory.resolve("a.json").toFile(),remote,"AXELPC probe");
            SyncJournal b=new SyncJournal(directory.resolve("b.json").toFile(),remote,"Mobile probe");
            cycle(a,rows("base"));SyncResult received=cycle(b,Map.of());
            if(!received.getRows().equals(rows("base")))throw new AssertionError("Project metadata differs");
            cycle(a,rows("left"));SyncResult conflict=cycle(b,rows("right"));
            if(conflict.getConflicts().size()!=1)throw new AssertionError("Concurrent revision was lost");
            SyncBlobs first=new SyncBlobs(directory.resolve("originals").toFile(),remote);
            String reference=first.capture(new ByteArrayInputStream(new byte[]{(byte)137,80,78,71,13,10,26,10,1,2,3}),"png");
            SyncBlobs second=new SyncBlobs(directory.resolve("downloaded").toFile(),remote);
            File copy=second.obtain(reference);
            if(!SyncBlobs.Companion.hash(copy).equals(reference.substring("nexus-sync:".length()).split("\\.")[0]))throw new AssertionError("Original bytes differ");
            Files.writeString(directory.resolve("result.json"),"{\"result\":\"PASS\",\"rootId\":\""+root+"\",\"metadataRoundTrip\":true,\"concurrentVersionsPreserved\":true,\"originalBytesVerified\":true}");
            System.out.println("PASS: real Drive metadata round trip, concurrent versions and verified original bytes");
        } finally {
            try(Response response=http.newCall(new Request.Builder().url("https://www.googleapis.com/drive/v3/files/"+folder).header("Authorization","Bearer "+token).patch(RequestBody.create("{\"trashed\":true}",MediaType.get("application/json"))).build()).execute()) {
                if(!response.isSuccessful())throw new IOException("Test folder was not moved to trash: "+response.code());
            }
        }
    }
}
