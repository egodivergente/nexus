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
            JSONObject assistant=new JSONObject();
            for(String[] entry:new String[][]{{"accessKey","nexus_bedrock_access_key"},{"secretKey","nexus_bedrock_secret_key"},{"token","nexus_assistant_proxy"}}) {
                String value=(String)storage.getClass().getMethod("getProviderApiKey",String.class).invoke(storage,entry[1]);
                if(value!=null && !value.isEmpty())assistant.put(entry[0],value);
            }
            JSONObject config=new JSONObject();
            android.content.SharedPreferences prefs=getTargetContext().getSharedPreferences("assistant_config_v1",0);
            for(java.util.Map.Entry<String,?> entry:prefs.getAll().entrySet())config.put(entry.getKey(),entry.getValue());
            JSONObject currency=new JSONObject();
            currency.put("preference",storage.getClass().getMethod("getCurrencyPreference").invoke(storage));
            currency.put("rate",storage.getClass().getMethod("getUsdToEurRate").invoke(storage));
            currency.put("lastUpdated",storage.getClass().getMethod("getExchangeRateLastUpdated").invoke(storage));
            JSONObject mcp=new JSONObject();
            for(java.util.Map.Entry<String,?> entry:getTargetContext().getSharedPreferences("agent_mcp_servers_v1",0).getAll().entrySet()) {
                String endpoint=String.valueOf(entry.getValue());
                Class<?> internet=getTargetContext().getClassLoader().loadClass("com.vidadomu.nexus.assistant.AgentInternet");
                Object companion=internet.getField("Companion").get(null);
                String identifier=(String)companion.getClass().getMethod("credentialKey",String.class).invoke(companion,endpoint);
                String token=(String)storage.getClass().getMethod("getProviderApiKey",String.class).invoke(storage,identifier);
                mcp.put(entry.getKey(),new JSONObject().put("url",endpoint).put("token",token==null?"":token));
            }
            JSONObject payload=new JSONObject().put("providers",values).put("assistant",assistant).put("settings",config).put("currency",currency).put("mcp",mcp);
            try(FileOutputStream stream=new FileOutputStream(file)) { stream.write(payload.toString().getBytes("UTF-8"));stream.getFD().sync(); }
            result.putInt("providers",values.length());finish(-1,result);
        } catch(Throwable e) { while(e.getCause()!=null)e=e.getCause();result.putString("error",e.getClass().getSimpleName());finish(0,result); }
    }
}
