package dev.local.ncmfurigana;

import android.content.Context;
import android.net.Uri;
import android.os.Looper;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.*;
import java.util.function.*;
import org.json.*;

/** Version-scoped response adapter. Originals are returned on any lookup or parsing error. */
final class PlaybackHook {
    interface Installer {void install(Method method,XposedInterface.Hooker callback);}
    private final Installer installer;
    private final Consumer<String> log;
    private final ResolverClient client;
    private final ClassLoader loader;
    PlaybackHook(ClassLoader loader, Installer installer,Consumer<String> log) {
        this.loader=loader; this.installer=installer; this.log=log;
        client=new ResolverClient(() -> {
            try {
                Context ctx=(Context)Class.forName("com.netease.cloudmusic.common.ApplicationWrapper",false,loader).getMethod("getInstance").invoke(null);
                if(ctx==null||ctx.getPackageManager().getPackageInfo("com.netease.cloudmusic",0).getLongVersionCode()!=9004000L) return null;
                return ctx;
            }
            catch(Exception e) {return null;}
        });
    }
    void install() {
        try {
            Class<?> response=Class.forName("aa1.a",false,loader);
            Field request=response.getDeclaredField("b"); request.setAccessible(true);
            Class<?> requestType=Class.forName("z91.f",false,loader);
            Field uri=requestType.getDeclaredField("k");uri.setAccessible(true);
            if(uri.getType()!=Uri.class||request.getType()!=requestType) throw new IllegalStateException("Response structure changed");
            Method result=response.getDeclaredMethod("b");
            if(result.getReturnType()!=Object.class||result.getParameterCount()!=0||result.getExceptionTypes().length!=2)
                throw new IllegalStateException("Response signature changed");
            installer.install(result,chain -> {
                Object original=chain.proceed();
                try {
                    if(!client.enabled()||Looper.myLooper()==Looper.getMainLooper()) return original;
                    Object req=request.get(chain.getThisObject()); Uri address=(Uri)uri.get(req);
                    String path=address==null?"":address.getPath();
                    if(path==null||!path.matches("/(?:eapi|api)/song/enhance/player/url(?:/v1)?")) return original;
                    if(!(original instanceof JSONObject)&&!(original instanceof String)) return original;
                    JSONObject json=new JSONObject(original.toString());
                    JSONArray data=json.optJSONArray("data"); if(data==null||data.length()>10) return original;
                    boolean changed=false; int attempted=0;
                    for(int i=0;i<data.length()&&attempted<1;i++) {
                        JSONObject item=data.optJSONObject(i);
                        if(item==null) continue;
                        boolean trial=!item.isNull("freeTrialInfo");
                        if(!PlaybackPolicy.needsFallback(item.optString("url",""),trial,item.optInt("code",200))) continue;
                        long id=item.optLong("id",0); if(id<=0) continue;
                        attempted++; log.accept("FALLBACK_LOOKUP song="+id+" reason="+(trial?"trial":"unavailable"));
                        try {
                            JSONObject replacement=client.resolve(id);
                            Uri media=Uri.parse(replacement.getString("url"));
                            if(!"http".equals(media.getScheme())&&!"https".equals(media.getScheme())) continue;
                            if(media.getHost()==null||media.getUserInfo()!=null) continue;
                            item.put("url",replacement.getString("url")); item.put("code",200);
                            item.put("br",replacement.optInt("br",128000)); item.put("size",replacement.optLong("size",0));
                            String format=replacement.optInt("br",128000)==999000?"flac":"mp3";
                            item.put("md5",replacement.optString("md5","")); item.put("type",format);
                            item.put("expi",180); item.put("level","standard"); item.put("encodeType",format);
                            item.put("freeTrialInfo",JSONObject.NULL); item.put("flag",0);
                            changed=true;
                            try {
                                Context ctx=(Context)Class.forName("com.netease.cloudmusic.common.ApplicationWrapper",false,loader).getMethod("getInstance").invoke(null);
                                android.os.Bundle matched=new android.os.Bundle();matched.putLong("song",id);
                                ctx.getContentResolver().call(Uri.parse("content://dev.local.ncmfurigana.report"),"matched",null,matched);
                            } catch(Exception e) {log.accept("MATCH_STATE_ERROR "+e.getClass().getSimpleName());}
                            log.accept("FALLBACK_MATCH song="+id+" source="+replacement.optString("source","unknown"));
                        } catch(Exception e) {log.accept("FALLBACK_MISS song="+id+" reason="+e.getClass().getSimpleName());}
                    }
                    return changed ? (original instanceof String?json.toString():json) : original;
                } catch(Throwable e) {log.accept("FALLBACK_ERROR "+e.getClass().getSimpleName());return original;}
            });
            Class<?> music=Class.forName("com.netease.cloudmusic.meta.MusicInfo",false,loader);
            Method cloud=music.getMethod("isPrivateCloudSong");
            // Permit an attempted player request for unavailable tracks only while the local service is ready.
            // This does not change account, download, trial, or purchase privileges.
            for(String name:new String[]{"hasCopyRight","canPlayMusicOnline"}) {
                Method method=music.getMethod(name);
                installer.install(method,chain -> {
                    Object original=chain.proceed();
                    if(Boolean.TRUE.equals(original)||!client.enabled()) return original;
                    try {
                        if(Boolean.TRUE.equals(cloud.invoke(chain.getThisObject()))) return original;
                        if(name.equals("canPlayMusicOnline")) {
                            // Invoke original rights method without going through its hook via a field instead.
                            Object privilege=music.getMethod("getSp").invoke(chain.getThisObject());
                            int status=((Number)privilege.getClass().getMethod("getOfflinestatus").invoke(privilege)).intValue();
                            if(status>=0) return original;
                        }
                        return true;
                    } catch(Exception e) {return original;}
                });
            }
            log.accept("PLAYBACK_HOOK_OK target=9.0.40 response=aa1.a.b unavailable-and-trial=true");
        } catch(Throwable e) {log.accept("PLAYBACK_HOOK_DISABLED "+e.getClass().getSimpleName());}
    }
}
