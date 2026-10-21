package dev.local.ncmfurigana;

import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.FrameLayout;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Consumer;

/** Scoped to the minibar hover next to minPlayBtn; no global TextView or membership hooks. */
final class VipHintHook {
    private final ClassLoader loader;
    private final PlaybackHook.Installer installer;
    private final Consumer<String> log;
    private final Map<FrameLayout,State> states=Collections.synchronizedMap(new WeakHashMap<>());
    private final Set<Long> reported=new LinkedHashSet<>();
    private final Set<Long> tracked=new LinkedHashSet<>();
    private Method invokeCallback;
    private static final class State {
        final long id;final WeakReference<Object> callback;
        long checked;boolean matched;int attempts;
        State(long id,Object callback) {this.id=id;this.callback=new WeakReference<>(callback);}
    }
    VipHintHook(ClassLoader loader,PlaybackHook.Installer installer,Consumer<String> log) {
        this.loader=loader;this.installer=installer;this.log=log;
    }
    void install() {
        try {
            Class<?> target=Class.forName("z10.e",false,loader);
            Class<?> lifecycle=Class.forName("androidx.lifecycle.LifecycleOwner",false,loader);
            Class<?> data=Class.forName("com.netease.cloudmusic.module.minibar.widget.IMiniBarHoverData",false,loader);
            Class<?> requestData=Class.forName("com.netease.cloudmusic.module.minibar.widget.MiniBarMusicHoverRequestData",false,loader);
            Class<?> remote=Class.forName("com.netease.cloudmusic.module.minibar.tool.MiniBarHoverData",false,loader);
            Class<?> function=Class.forName("kotlin.jvm.functions.Function2",false,loader);
            Method render=target.getDeclaredMethod("b",lifecycle,FrameLayout.class,data,function);
            Method inflate=target.getDeclaredMethod("g",FrameLayout.class,remote);
            if(render.getReturnType()!=void.class||inflate.getReturnType()!=View.class) throw new IllegalStateException("Minibar signature changed");
            Method id=requestData.getMethod("getId");invokeCallback=function.getMethod("invoke",Object.class,Object.class);
            installer.install(render,chain -> {
                FrameLayout container=(FrameLayout)chain.getArg(1);
                boolean supported=false;
                try {
                    supported=container.getContext().getPackageManager().getPackageInfo("com.netease.cloudmusic",0).getLongVersionCode()==9004000L;
                } catch(Exception ignored) { }
                if(!supported) return chain.proceed();
                State state=null;
                try {
                    Object input=chain.getArg(2);
                    long song=requestData.isInstance(input)?Long.parseLong(String.valueOf(id.invoke(input))):0;
                    if(song>0) {
                        state=new State(song,chain.getArg(3));states.put(container,state);
                        if(tracked.add(song)) {
                            if(tracked.size()>128) tracked.remove(tracked.iterator().next());
                            log.accept("VIP_HINT_TRACK song="+song);
                        }
                    }
                    else states.remove(container);
                } catch(Exception e) {states.remove(container);}
                if(state!=null&&matched(container,state)) {hide(container,state);return null;}
                Object result=chain.proceed();
                if(state!=null) watch(new WeakReference<>(container),state);
                return result;
            });
            installer.install(inflate,chain -> {
                FrameLayout container=(FrameLayout)chain.getArg(0);
                State state=states.get(container);
                if(state!=null&&matched(container,state)) {hide(container,state);return null;}
                return chain.proceed();
            });
            log.accept("VIP_HINT_HOOK_OK target=minibar_hover/vipText owner=z10.e matched-only=true");
        } catch(Throwable e) {log.accept("VIP_HINT_HOOK_DISABLED "+e.getClass().getSimpleName()+" "+e.getMessage());}
    }
    private boolean matched(FrameLayout container,State state) {
        if(state.matched) return true;
        long now=SystemClock.elapsedRealtime();
        if(now-state.checked<700) return false;
        state.checked=now;
        try {
            Bundle input=new Bundle();input.putLong("song",state.id);
            Bundle result=container.getContext().getContentResolver().call(Uri.parse("content://dev.local.ncmfurigana.report"),"isMatched",null,input);
            state.matched=result!=null&&result.getBoolean("matched",false);
        } catch(Exception ignored) { }
        return state.matched;
    }
    private void watch(WeakReference<FrameLayout> ref,State state) {
        FrameLayout container=ref.get();if(container==null||states.get(container)!=state) return;
        if(matched(container,state)) {hide(container,state);return;}
        if(++state.attempts<40) container.postDelayed(() -> watch(ref,state),750);
    }
    private void hide(FrameLayout container,State state) {
        try {
            int hoverId=container.getResources().getIdentifier("minibar_hover","id","com.netease.cloudmusic");
            int textId=container.getResources().getIdentifier("vipText","id","com.netease.cloudmusic");
            View hover=hoverId==0?null:container.findViewById(hoverId);
            if(hover!=null&&textId!=0&&hover.findViewById(textId)!=null) container.removeView(hover);
            Object callback=state.callback.get();
            if(callback!=null) invokeCallback.invoke(callback,Integer.valueOf(0),null);
            container.requestLayout();
            if(reported.add(state.id)) {
                if(reported.size()>128) reported.remove(reported.iterator().next());
                log.accept("VIP_HINT_HIDDEN target=minibar_hover/vipText song="+state.id);
            }
        } catch(Exception e) {log.accept("VIP_HINT_HIDE_ERROR "+e.getClass().getSimpleName());}
    }
}
