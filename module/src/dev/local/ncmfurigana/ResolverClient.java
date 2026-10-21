package dev.local.ncmfurigana;

import android.content.Context;
import android.os.*;
import android.net.Uri;
import org.json.JSONObject;
import java.io.*;
import java.net.*;

final class ResolverClient {
    private final java.util.function.Supplier<Context> context;
    private volatile Bundle cached=Bundle.EMPTY;
    private volatile long refreshed;
    ResolverClient(java.util.function.Supplier<Context> context) {this.context=context;}
    synchronized Bundle config() {
        long now=SystemClock.elapsedRealtime();
        if(now-refreshed<1000) return cached;
        refreshed=now;
        try {
            Context ctx=context.get();
            Bundle value=ctx==null?null:ctx.getContentResolver().call(Uri.parse("content://dev.local.ncmfurigana.report"),"resolver",null,null);
            cached=value==null?Bundle.EMPTY:value;
        } catch(Exception e) {cached=Bundle.EMPTY;}
        return cached;
    }
    boolean enabled() { Bundle c=config(); return c.getBoolean("enabled",false)&&c.getInt("port",0)>0; }
    JSONObject resolve(long id) throws Exception {
        if(Looper.myLooper()==Looper.getMainLooper()) throw new IOException("Main-thread lookup refused");
        return request("/resolve?id="+id);
    }
    JSONObject request(String path) throws Exception {
        Bundle c=config(); int port=c.getInt("port",0);
        String token=c.getString("token","");
        if(!c.getBoolean("enabled",false)||port<1||port>65535||!token.matches("[a-f0-9-]{72}")) throw new IOException("Resolver stopped");
        try(Socket socket=new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1",port),500); socket.setSoTimeout(12500);
            OutputStream out=socket.getOutputStream();
            out.write(("GET "+path+" HTTP/1.0\r\nHost: 127.0.0.1\r\nAuthorization: Bearer "+token+"\r\nConnection: close\r\n\r\n").getBytes("UTF-8"));out.flush();
            ByteArrayOutputStream buf=new ByteArrayOutputStream(); byte[] block=new byte[2048];int n;
            InputStream input=socket.getInputStream();
            while((n=input.read(block))!=-1) {if(buf.size()+n>32768) throw new IOException("Response too large");buf.write(block,0,n);}
            String response=buf.toString("UTF-8"); int split=response.indexOf("\r\n\r\n");
            if(split<0||!response.startsWith("HTTP/1.1 200")&&!response.startsWith("HTTP/1.0 200")) throw new IOException("No matching source");
            return new JSONObject(response.substring(split+4));
        }
    }
}
