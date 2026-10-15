package com.axel.apkexplorer;
import android.content.Context;
import android.os.ParcelFileDescriptor;
import java.io.*;

/** Privileged Shizuku user service, never exported as an Android component. */
public final class ExplorerService extends IExplorer.Stub {
    private final Context context;
    private Engine engine;
    public ExplorerService(Context context) { this.context=context; }
    public synchronized String start(String pkg,int max) {
        if(engine!=null && engine.running) return "Ya hay una exploración en curso.";
        if(!pkg.matches("[A-Za-z][\\w]*(\\.[A-Za-z][\\w]*)+") || max<1 || max>20) return "Parámetros inválidos.";
        engine=new Engine(context,pkg,max);
        new Thread(engine,"explorer-worker").start();
        return "Explorando en pantalla virtual. Puedes usar otra app.";
    }
    public synchronized String status() { return engine==null ? "Sin sesión" : engine.status; }
    public synchronized void stop() { if(engine!=null) engine.stop=true; }
    public synchronized ParcelFileDescriptor exportZip() {
        if(engine==null || engine.running) return null;
        try {
            final byte[] bytes=engine.zip();
            ParcelFileDescriptor[] pipe=ParcelFileDescriptor.createPipe();
            new Thread(()->{try(OutputStream out=new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {out.write(bytes);}catch(IOException ignored){}},"export").start();
            return pipe[0];
        }catch(Exception e){return null;}
    }
    public void destroy() { stop(); new Thread(()->{try{Thread.sleep(3000);}catch(Exception ignored){} System.exit(0);}).start(); }
}
