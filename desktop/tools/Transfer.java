package com.vidadomu.nexus.transfer;
import android.app.Instrumentation;
import android.os.Bundle;
import java.io.File;
import java.io.FileOutputStream;
import org.json.JSONObject;
public class Transfer extends Instrumentation {
    public void onCreate(Bundle args) { super.onCreate(args); start(); }
    public void onStart() {
        Bundle result=new Bundle();
        try {
            if(android.os.Process.myUid()/100000!=0) throw new IllegalStateException("Personal profile required");
            Class<?> type=getTargetContext().getClassLoader().loadClass("com.vidadomu.nexus.core.security.SecureStorage");
            Object storage=type.getConstructor(android.content.Context.class).newInstance(getTargetContext());
            JSONObject values=new JSONObject();
            for(String provider:new String[]{"atlas_cloud","runway","crun"}) {
                String key=(String)storage.getClass().getMethod("getProviderApiKey",String.class).invoke(storage,provider);
                if(key!=null && !key.trim().isEmpty()) values.put(provider,key);
            }
            File file=new File(getTargetContext().getFilesDir(),"desktop-key-transfer.json");
            try(FileOutputStream stream=new FileOutputStream(file)) { stream.write(values.toString().getBytes("UTF-8"));stream.getFD().sync(); }
            result.putInt("providers",values.length());finish(-1,result);
        } catch(Throwable e) { while(e.getCause()!=null)e=e.getCause();result.putString("error",e.getClass().getSimpleName());finish(0,result); }
    }
}
