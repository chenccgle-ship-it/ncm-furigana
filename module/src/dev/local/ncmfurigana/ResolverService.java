package dev.local.ncmfurigana;

import android.app.*;
import android.content.*;
import android.os.*;
import java.io.*;
import java.util.UUID;
import java.util.zip.*;

/** Owns the child process. No root, global proxy, certificate, or system setting is needed. */
public final class ResolverService extends Service {
    private volatile java.lang.Process child;
    private volatile boolean stopping;
    private volatile boolean restartRequested;
    private Thread worker;
    private static volatile boolean alive;
    static final String PREFS="resolver";
    static void event(Context context,String text) {
        Bundle b=new Bundle(); b.putString("event",text);
        context.getContentResolver().call(android.net.Uri.parse("content://dev.local.ncmfurigana.report"),"append",null,b);
    }
    static Bundle config(Context context) {
        android.content.SharedPreferences p=context.getSharedPreferences(PREFS,0);
        Bundle b=new Bundle(); b.putBoolean("enabled",alive&&p.getBoolean("enabled",false));
        b.putInt("port",alive?p.getInt("port",0):0); b.putString("token",alive?p.getString("token",""):""); return b;
    }
    @Override public void onCreate() {
        super.onCreate();
        alive=true;
        NotificationManager manager=getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("resolver","备用音源服务",NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE);
        startForeground(21,new Notification.Builder(this,"resolver").setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("网易云注音 · 备用音源已开启").setContentText("点击管理或停止备用音源服务")
            .setContentIntent(open).setOngoing(true).build());
    }
    @Override public synchronized int onStartCommand(Intent intent,int flags,int id) {
        if (worker!=null) {
            if(intent!=null&&"reload".equals(intent.getAction())) {
                restartRequested=true;
                getSharedPreferences(PREFS,0).edit().putInt("port",0).apply();
                if(child!=null) child.destroy();
            }
            return START_NOT_STICKY;
        }
        stopping=false;
        getSharedPreferences(PREFS,0).edit().putBoolean("enabled",true).putInt("port",0).apply();
        worker=new Thread(() -> {
            do {
                synchronized(this) {restartRequested=false;}
                runEngine();
            } while(restartRequested&&!stopping);
            stopSelf();
        },"NcmResolverEngine"); worker.start(); return START_NOT_STICKY;
    }
    private void runEngine() {
        try {
            File directory=new File(getFilesDir(),"resolver-v2");
            File marker=new File(directory,".complete");
            if(!marker.isFile()) {
                directory.mkdirs();
                try(ZipInputStream zip=new ZipInputStream(getAssets().open("resolver.zip"))) {
                    ZipEntry entry; byte[] buf=new byte[16384];
                    while((entry=zip.getNextEntry())!=null) {
                        File target=new File(directory,entry.getName());
                        if(!target.getCanonicalPath().startsWith(directory.getCanonicalPath()+File.separator)) throw new IOException("Invalid asset path");
                        if(entry.isDirectory()) { target.mkdirs(); continue; }
                        target.getParentFile().mkdirs();
                        try(FileOutputStream out=new FileOutputStream(target)) {int n;while((n=zip.read(buf))!=-1) out.write(buf,0,n);}
                    }
                }
                marker.createNewFile();
            }
            String token=UUID.randomUUID().toString()+UUID.randomUUID().toString();
            File auth=new File(getFilesDir(),"resolver-auth");
            android.content.SharedPreferences settings=getSharedPreferences(PREFS,0);
            String source="qq".equals(settings.getString("source","kuwo"))?"qq":"kuwo";
            org.json.JSONObject configuration=new org.json.JSONObject();
            configuration.put("token",token);configuration.put("source",source);
            configuration.put("qqCookie",source.equals("qq")?settings.getString("qqCookie",""):"");
            try(FileOutputStream out=new FileOutputStream(auth)) {out.write(configuration.toString().getBytes("UTF-8"));}
            getSharedPreferences(PREFS,0).edit().putString("token",token).apply();
            File executable=new File(getApplicationInfo().nativeLibraryDir,"libnode.so");
            ProcessBuilder builder=new ProcessBuilder(executable.getAbsolutePath(),new File(directory,"resolver.js").getAbsolutePath(),auth.getAbsolutePath());
            builder.directory(directory); builder.redirectErrorStream(true);
            synchronized(this) {
                if(stopping||restartRequested) return;
                child=builder.start();
            }
            try(BufferedReader input=new BufferedReader(new InputStreamReader(child.getInputStream()))) {
                String line;
                while((line=input.readLine())!=null) {
                    if(line.matches("READY [0-9]{1,5}")) {
                        int port=Integer.parseInt(line.substring(6));
                        synchronized(this) {
                            if(!stopping&&!restartRequested) getSharedPreferences(PREFS,0).edit().putInt("port",port).apply();
                        }
                        event(this,"RESOLVER_READY engine=0.28.0 node=16.15.1 address=loopback source="+source);
                    }
                    // Upstream may log full media URLs; never copy arbitrary stdout to diagnostics.
                }
            }
            int code=child.waitFor(); if(!stopping&&!restartRequested) event(this,"RESOLVER_EXIT code="+code);
        } catch(Exception e) { if(!stopping&&!restartRequested) event(this,"RESOLVER_ERROR "+e.getClass().getSimpleName()); }
    }
    @Override public synchronized void onDestroy() {
        stopping=true;
        alive=false;
        getSharedPreferences(PREFS,0).edit().putBoolean("enabled",false).putInt("port",0).remove("token").apply();
        if(child!=null) child.destroy();
        new File(getFilesDir(),"resolver-auth").delete();
        stopForeground(STOP_FOREGROUND_REMOVE); super.onDestroy();
    }
    @Override public void onTimeout(int startId,int fgsType) {stopSelf();}
    @Override public IBinder onBind(Intent intent) {return null;}
}
