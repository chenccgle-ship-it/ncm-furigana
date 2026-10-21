package dev.local.ncmfurigana;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;

public final class ProbeModule extends XposedModule {
    private static final String TARGET = "com.netease.cloudmusic";
    private static final String TAG = "NcmFuriganaProbe";
    // Captured text coordinates were verified on-device in 0.2.1.
    private static final boolean RESERVE_HEIGHT = true;
    private final Map<View, SongState> views = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<Object, SongState> renderers = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<Object, LayoutData> layouts = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<View, Long> lastRecovery = Collections.synchronizedMap(new WeakHashMap<>());
    private final ThreadLocal<Frame> active = new ThreadLocal<>();
    private final ThreadPoolExecutor analyzer = new ThreadPoolExecutor(0, 1, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(4), r -> { Thread t = new Thread(r, "NcmFuriganaReadings"); t.setDaemon(true); return t; },
            new ThreadPoolExecutor.AbortPolicy());
    private ReadingEngine engine;
    private Method highFont;
    private Method sentenceMethod;
    private Field highlightPaint;
    private Field rendererInfo;
    private Class<?> playerRenderer;
    private final Set<String> seen = new LinkedHashSet<>();
    private final Set<Method> textMethods = new LinkedHashSet<>();
    private final Set<Class<?>> canvasTypes = new LinkedHashSet<>();
    private final ArrayDeque<String> pending = new ArrayDeque<>();
    private final ThreadPoolExecutor writer = new ThreadPoolExecutor(0, 1, 10, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(32), r -> { Thread t = new Thread(r, "NcmProbeReport"); t.setDaemon(true); return t; },
            new ThreadPoolExecutor.DiscardPolicy());
    private volatile Context context;
    private volatile String process = "unknown";
    private volatile long songId = -1;
    private boolean installed;
    private boolean playbackInstalled;
    private boolean providerError;
    private volatile boolean rubyEnabled=true;
    private long lastFeaturePoll;
    private final Set<View> lyricViews=Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<View,Boolean>()));
    private final Paint badgeText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgeBackground = new Paint(Paint.ANTI_ALIAS_FLAG);

    private static final class SongState {
        final long id;
        final WeakReference<View> view;
        final Object info;
        final LyricSnapshot snapshot;
        volatile boolean cancelled;
        volatile boolean analyzing;
        volatile boolean finished;
        volatile long retryAfter;
        int attempts;
        volatile String status="词典加载中";
        volatile Map<String,List<ReadingEngine.Ruby>> annotations=Collections.emptyMap();
        SongState(View view,Object info,LyricSnapshot snapshot) {
            this.id=snapshot.id;this.view=new WeakReference<>(view);this.info=info;this.snapshot=snapshot;
        }
    }
    private static final class LayoutData {
        final SongState song;
        final List<RubyLayout.Chunk> chunks;
        final RubyLayout.Reservation reservation;
        final List<Integer> heights;
        final int space;
        LayoutData(SongState song,List<RubyLayout.Chunk> chunks,RubyLayout.Reservation reservation,List<Integer> heights,int space) {
            this.song=song;this.chunks=chunks;this.reservation=reservation;this.heights=heights;this.space=space;
        }
    }
    private static final class Frame {
        final Canvas canvas;
        final LayoutData data;
        final Paint rubyPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
        int next;
        boolean busy;
        Frame(Canvas canvas,LayoutData data) { this.canvas=canvas;this.data=data; }
    }

    @Override public void onModuleLoaded(ModuleLoadedParam param) {
        process = param.getProcessName();
        emit("MODULE build=0.5.4 process=" + process + " framework=" + getFrameworkName() + " " + getFrameworkVersion() + " api=" + getApiVersion());
    }

    @Override public synchronized void onPackageReady(PackageReadyParam param) {
        if (!playbackInstalled && TARGET.equals(param.getPackageName()) && (TARGET.equals(process)||process.equals(TARGET+":play"))) {
            playbackInstalled=true;
            ClassLoader loader=param.getClassLoader();
            try {
                Method create=android.app.Application.class.getDeclaredMethod("onCreate");
                hook(create).setExceptionMode(ExceptionMode.PROTECTIVE).intercept(chain -> {
                    attachContext((Context)chain.getThisObject()); return chain.proceed();
                });
                // Obfuscated response names are supported only for the verified target build.
                new PlaybackHook(loader,(m,cb) -> hook(m).setExceptionMode(ExceptionMode.PROTECTIVE).intercept(cb),this::emit).install();
                if(TARGET.equals(process)) new VipHintHook(loader,(m,cb) -> hook(m).setExceptionMode(ExceptionMode.PROTECTIVE).intercept(cb),this::emit).install();
            } catch(Throwable e) {failure("PLAYBACK_INIT",e);}
        }
        if (installed || !TARGET.equals(param.getPackageName()) || !TARGET.equals(process)) return;
        installed = true;
        ClassLoader loader = param.getClassLoader();
        install(loader, "com.netease.cloudmusic.ui.LyricView", "setLyric", 2, this::loaded);
        install(loader, "com.netease.cloudmusic.module.lyric.meta.PlayerLyric", "realComputeSentenceInfoWithCache", 4, this::layout);
        install(loader, "com.netease.cloudmusic.module.lyric.meta.PlayerLyric", "drawSingleSentence", 8, this::sentence);
        install(loader, "com.netease.cloudmusic.ui.LyricView", "onDraw", 1, this::draw);
        try {
            highFont=Class.forName("com.netease.cloudmusic.module.lyric.meta.PlayerLyric",false,loader)
                    .getDeclaredMethod("getRealTextSize",boolean.class,boolean.class);
            playerRenderer=Class.forName("com.netease.cloudmusic.module.lyric.meta.PlayerLyric",false,loader);
            highFont.setAccessible(true);
            highlightPaint=Class.forName("com.netease.cloudmusic.meta.CommonLyric",false,loader).getDeclaredField("pHighLight");
            highlightPaint.setAccessible(true);
            rendererInfo=Class.forName("com.netease.cloudmusic.meta.CommonLyric",false,loader).getDeclaredField("mLyricInfo");
            rendererInfo.setAccessible(true);
            Method text=Canvas.class.getMethod("drawText",String.class,float.class,float.class,Paint.class);
            hook(text).setExceptionMode(ExceptionMode.PROTECTIVE).intercept(this::text);
            textMethods.add(text);
            emit("HOOK_OK Canvas.drawText(String,float,float,Paint) scoped-to-lyric-call");
            if (sentenceMethod!=null) emit("TEXT_CALLER_DEOPT="+deoptimize(sentenceMethod));
        } catch (Throwable e) { failure("TEXT_HOOK",e); }
    }

    private void install(ClassLoader loader, String name, String method, int count, XposedInterface.Hooker callback) {
        try {
            Class<?> type = Class.forName(name, false, loader);
            Method found = null;
            for (Method candidate : type.getDeclaredMethods()) {
                if (candidate.getName().equals(method) && candidate.getParameterCount() == count) {
                    if (found != null) throw new IllegalStateException("Ambiguous method: " + method);
                    found = candidate;
                }
            }
            if (found == null) throw new NoSuchMethodException(name + "." + method);
            hook(found).setExceptionMode(ExceptionMode.PROTECTIVE).intercept(callback);
            if (method.equals("drawSingleSentence")) sentenceMethod=found;
            emit("HOOK_OK " + name + "." + method);
        } catch (Throwable e) { failure("HOOK_FAIL " + name + "." + method, e); }
    }

    private Object loaded(Chain chain) throws Throwable {
        Object result = chain.proceed(); // Host exceptions must propagate, never retry the host call.
        try {
            View view = (View)chain.getThisObject();
            attachContext(view.getContext());
            pollFeatures();
            Object info = chain.getArg(0);
            Object renderer=invoke(view,"getLyric");
            initialize(view,renderer,info,"setLyric");
        } catch (Throwable e) { onceFailure("loaded", e); }
        return result;
    }

    private void initialize(View view,Object renderer,Object info,String reason) throws Exception {
        if(!rubyEnabled) return;
        SongState old=views.get(view);
        if (info==null || renderer==null) {
            if (old!=null) old.cancelled=true;
            views.remove(view);
            return;
        }
        if (playerRenderer!=null && !playerRenderer.isInstance(renderer)) return;
        long id=((Number)invoke(info,"getMusicId")).longValue();
        List<?> lines=(List<?>)invoke(info,"getSortLines");
        List<String> texts=new ArrayList<>();int yrcLines=0;
        if (lines!=null) for (Object line:lines) {
            if (line==null) continue;
            Object value=invoke(line,"getContent");
            String text=value==null ? "" : value.toString();
            if (Boolean.TRUE.equals(invoke(line,"isYrc"))) yrcLines++;
            if (texts.size()<1000 && text.length()<=2000) texts.add(text);
        }
        LyricSnapshot snapshot=new LyricSnapshot(id,texts,yrcLines);
        if (old!=null && !old.cancelled && old.info==info && renderers.get(renderer)==old && old.snapshot.sameContent(snapshot)) {
            schedule(old);
            return;
        }
        if (old!=null) old.cancelled=true;
        SongState song=new SongState(view,info,snapshot);
        views.put(view,song);renderers.put(renderer,song);songId=id;
        emit("LYRIC_LOADED song="+id+" lines="+(lines==null ? 0 : lines.size())
                +" romaji="+invoke(info,"isHasRome")+" yrc="+invoke(info,"isHasYrc")+" yrcIncluded="+yrcLines
                +" reason="+reason+" view="+view.getClass().getName());
        if (!snapshot.japanese) {
            song.finished=true;song.status="未检测到日语";
            emit("SKIP_NON_JAPANESE song="+id);
        } else schedule(song);
    }

    private void recover(View view) throws Exception {
        long now=SystemClock.elapsedRealtime();
        Long last=lastRecovery.get(view);
        if (last!=null && now-last<1000) return;
        lastRecovery.put(view,now);
        Object renderer=invoke(view,"getLyric");
        if (renderer==null || rendererInfo==null || !playerRenderer.isInstance(renderer)) return;
        attachContext(view.getContext());
        initialize(view,renderer,rendererInfo.get(renderer),"draw-recovery");
    }

    private void schedule(SongState song) {
        if(!rubyEnabled) return;
        synchronized (song) {
            if (song.cancelled || song.finished || song.analyzing || SystemClock.elapsedRealtime()<song.retryAfter) return;
            song.analyzing=true;
            try { analyzer.execute(() -> analyze(song)); }
            catch (RejectedExecutionException e) {
                song.analyzing=false;song.retryAfter=SystemClock.elapsedRealtime()+1000;
                if (first("queue:"+song.id)) emit("ANALYSIS_RETRY song="+song.id+" reason=queue-full");
            }
        }
    }

    private Object layout(Chain chain) throws Throwable {
        Object result = chain.proceed();
        if(!rubyEnabled) return result;
        try {
            reserve(chain,result);
            String key = "layout:" + songId;
            if (first(key)) {
                Object line = chain.getArg(0);
                String content = String.valueOf(invoke(line, "getContent"));
                emit("LAYOUT_HIT song=" + songId + " owner=" + chain.getThisObject().getClass().getName()
                        + " width=" + chain.getArg(2) + " height=" + chain.getArg(3)
                        + " textLength=" + content.length() + " result=" + (result == null ? "null" : result.getClass().getName()));
            }
        } catch (Throwable e) { onceFailure("layout", e); }
        return result;
    }

    private Object sentence(Chain chain) throws Throwable {
        if(!rubyEnabled) return chain.proceed();
        Frame previous=active.get();
        try {
            Canvas canvas=(Canvas)chain.getArg(0);
            if (canvas!=null) ensureCanvasHook(canvas);
            LayoutData data=layouts.get(chain.getArg(2));
            boolean highlighted=highlightPaint!=null && chain.getArg(1)==highlightPaint.get(chain.getThisObject());
            if (canvas!=null && data!=null && first("frame:"+data.song.id+":"+highlighted))
                emit("FRAME_READY song="+data.song.id+" highlight="+highlighted+" canvas="+canvas.getClass().getName()
                        +" chunks="+data.chunks.size());
            if (data!=null && !data.song.cancelled && canvas!=null)
                active.set(new Frame((Canvas)chain.getArg(0),data));
            else active.remove();
        } catch (Throwable e) { active.remove();onceFailure("sentence_context",e); }
        Object result;
        try { result=chain.proceed(); }
        finally { if (previous==null) active.remove();else active.set(previous); }
        try {
            if (chain.getArg(0) != null && first("sentence:" + songId)) {
                emit("DRAW_HIT song=" + songId + " owner=" + chain.getThisObject().getClass().getName()
                        + " index=" + chain.getArg(3) + " width=" + chain.getArg(4)
                        + " originY=" + chain.getArg(5) + " time=" + chain.getArg(7));
            }
        } catch (Throwable e) { onceFailure("sentence", e); }
        return result;
    }

    /** Hardware accelerated Canvas implementations can override the base Canvas method. */
    private synchronized void ensureCanvasHook(Canvas canvas) {
        if (!canvasTypes.add(canvas.getClass())) return;
        try {
            Method actual=canvas.getClass().getMethod("drawText",String.class,float.class,float.class,Paint.class);
            if (textMethods.contains(actual)) return;
            hook(actual).setExceptionMode(ExceptionMode.PROTECTIVE).intercept(this::text);
            textMethods.add(actual);
            emit("TEXT_IMPLEMENTATION canvas="+canvas.getClass().getName()+" owner="+actual.getDeclaringClass().getName());
            if (sentenceMethod!=null) emit("TEXT_CALLER_DEOPT="+deoptimize(sentenceMethod));
        } catch (Throwable e) { onceFailure("canvas_implementation:"+canvas.getClass().getName(),e); }
    }

    private Object draw(Chain chain) throws Throwable {
        View lyricView=(View)chain.getThisObject();lyricViews.add(lyricView);attachContext(lyricView.getContext());
        pollFeatures();
        if(!rubyEnabled) return chain.proceed();
        try { recover((View)chain.getThisObject()); }
        catch (Throwable e) { onceFailure("draw_recovery",e); }
        Object result = chain.proceed();
        try {
            View view = (View)chain.getThisObject();
            SongState song=views.get(view);
            if (song==null) return result;
            if (!"词典加载失败".equals(song.status)) return result;
            Canvas canvas = (Canvas)chain.getArg(0);
            float density = view.getResources().getDisplayMetrics().density;
            badgeText.setTextSize(11 * view.getResources().getDisplayMetrics().scaledDensity);
            badgeText.setColor(Color.WHITE);
            badgeBackground.setColor(0xB0224854);
            String text = "日语注音 · " + song.status;
            float x = 12 * density, y = 18 * density;
            int save = canvas.save();
            try {
                canvas.drawRoundRect(x - 5*density, y + badgeText.ascent() - 4*density,
                        x + badgeText.measureText(text) + 5*density, y + badgeText.descent() + 4*density,
                        4*density, 4*density, badgeBackground);
                canvas.drawText(text, x, y, badgeText);
            } finally { canvas.restoreToCount(save); }
            if (first("badge:" + songId)) emit("BADGE_DRAWN song=" + songId);
        } catch (Throwable e) { onceFailure("badge", e); }
        return result;
    }

    private void analyze(SongState song) {
        long started=SystemClock.elapsedRealtime();
        try {
            if (song.cancelled) return;
            song.attempts++;
            if (engine==null) {
                engine=new ReadingEngine();
                emit("ENGINE_READY kuromoji-ipadic-0.9.0 ms="+(SystemClock.elapsedRealtime()-started));
            }
            Map<String,List<ReadingEngine.Ruby>> annotations=new HashMap<>();
            int count=0;
            for (String text:song.snapshot.texts) {
                if (song.cancelled) return;
                List<ReadingEngine.Ruby> rubies=engine.annotate(text);
                if (!rubies.isEmpty()) { annotations.put(text,rubies);count+=rubies.size(); }
            }
            if (song.cancelled) return;
            song.annotations=Collections.unmodifiableMap(annotations);
            song.finished=true;
            song.status=count>0 ? "离线注音" : "无可注音歌词";
            emit("READINGS_READY song="+song.id+" lines="+annotations.size()+" rubies="+count+" yrcIncluded="+song.snapshot.yrcLines
                    +" ms="+(SystemClock.elapsedRealtime()-started));
            View view=song.view.get();
            if (view!=null) view.post(() -> {
                if (song.cancelled || views.get(view)!=song) return;
                try {
                    Object renderer=invoke(view,"getLyric");
                    if (renderer!=null && renderers.get(renderer)==song) {
                        for (String name:new String[]{"sentenceInfoMap","locationArray"}) {
                            Class<?> type=renderer.getClass();
                            while (type!=null) {
                                try {
                                    Field field=type.getDeclaredField(name);field.setAccessible(true);
                                    Object cache=field.get(renderer);
                                    if (cache!=null) cache.getClass().getMethod("clear").invoke(cache);
                                    break;
                                } catch (NoSuchFieldException ignored) { type=type.getSuperclass(); }
                            }
                        }
                    }
                } catch (Throwable e) { onceFailure("refresh_layout",e); }
                view.invalidate();
            });
        } catch (Throwable e) {
            song.status="词典加载失败";song.retryAfter=SystemClock.elapsedRealtime()+15000;
            song.finished=song.attempts>=3;failure("ENGINE_FAIL",e);
        } finally { song.analyzing=false; }
    }

    @SuppressWarnings("unchecked")
    private void reserve(Chain chain,Object info) throws Exception {
        SongState song=renderers.get(chain.getThisObject());
        if (song==null || song.cancelled || info==null) return;
        Object line=chain.getArg(0);
        String original=String.valueOf(invoke(line,"getContent"));
        List<ReadingEngine.Ruby> rubies=song.annotations.get(original);
        if (rubies==null || rubies.isEmpty()) return;
        Class<?> type=info.getClass();
        List<Integer> heights=(List<Integer>)type.getField("f").get(info);
        int total=type.getField("a").getInt(info);
        float font=((Paint)chain.getArg(1)).getTextSize();
        if (highFont!=null) font=Math.max(font,((Number)highFont.invoke(chain.getThisObject(),false,true)).floatValue());
        int space=RESERVE_HEIGHT ? (int)Math.ceil(font*0.85f) : 0;
        LayoutData old=layouts.get(info);
        if (old!=null && old.heights==heights && old.reservation.matches(heights,total)) {
            if (old.song==song && old.space==space) return;
            if (RESERVE_HEIGHT) {
                old.reservation.restore(heights);total=old.reservation.baseTotal;
                type.getField("a").setInt(info,total);
            }
        }
        List<List<?>> elements=(List<List<?>>)type.getField("d").get(info);
        int bodyCount=type.getField("i").getInt(info);
        if (bodyCount==0) bodyCount=elements.size();
        List<List<String>> rows=new ArrayList<>();
        for (int i=0;i<Math.min(bodyCount,elements.size());i++) {
            List<String> row=new ArrayList<>();
            for (Object element:elements.get(i)) {
                Object tx=invoke(element,"getTx");
                row.add(tx==null ? "" : tx.toString());
            }
            rows.add(row);
        }
        List<RubyLayout.Chunk> chunks=RubyLayout.chunks(original,rows,rubies);
        boolean[] rubyRows=new boolean[rows.size()];
        boolean any=false;
        for (RubyLayout.Chunk chunk:chunks) if (!chunk.rubies.isEmpty()) { rubyRows[chunk.row]=true;any=true; }
        if (!any) return;
        RubyLayout.Reservation reservation=new RubyLayout.Reservation(heights,total,rubyRows,space);
        if (RESERVE_HEIGHT) {
            reservation.apply(heights);
            type.getField("a").setInt(info,reservation.appliedTotal);
        }
        layouts.put(info,new LayoutData(song,chunks,reservation,heights,space));
        if (first("reserve:"+song.id)) emit("RUBY_LAYOUT song="+song.id+" rows="+rows.size()+" extra="+(reservation.appliedTotal-total)
                +" layoutUnchanged="+!RESERVE_HEIGHT+" preview=all-visible-lines");
    }

    private Object text(Chain chain) throws Throwable {
        if(!rubyEnabled) return chain.proceed();
        Frame frame=active.get();
        if (frame==null || frame.busy || frame.canvas!=chain.getThisObject()) return chain.proceed();
        frame.busy=true;
        try {
            Object result=chain.proceed();
            try {
                String drawn=(String)chain.getArg(0);
                if (frame.next>=frame.data.chunks.size()) return result;
                RubyLayout.Chunk chunk=frame.data.chunks.get(frame.next);
                if (first("capture:"+frame.data.song.id)) emit("TEXT_CAPTURE song="+frame.data.song.id
                        +" matched="+chunk.text.equals(drawn)+" chars="+drawn.length()+" expectedChars="+chunk.text.length());
                if (!chunk.text.equals(drawn)) return result;
                frame.next++;
                Paint body=(Paint)chain.getArg(3);
                float x=((Number)chain.getArg(1)).floatValue(), y=((Number)chain.getArg(2)).floatValue();
                float left=x;
                if (body.getTextAlign()==Paint.Align.CENTER) left-=body.measureText(drawn)/2;
                else if (body.getTextAlign()==Paint.Align.RIGHT) left-=body.measureText(drawn);
                for (ReadingEngine.Ruby ruby:chunk.rubies) {
                    int start=ruby.start-chunk.offset,end=ruby.end-chunk.offset;
                    float before=body.measureText(drawn,0,start), after=body.measureText(drawn,0,end);
                    float available=after-before;
                    if (available<=0) continue;
                    Paint p=frame.rubyPaint;p.set(body);p.setTextAlign(Paint.Align.LEFT);
                    p.setTextSize(body.getTextSize()*0.42f);
                    float width=p.measureText(ruby.reading);
                    if (width>available && width>0) {
                        p.setTextScaleX(p.getTextScaleX()*available/width);
                        width=p.measureText(ruby.reading);
                    }
                    float rubyX=left+before+(available-width)/2;
                    float rubyY=y+body.ascent()-body.getTextSize()*0.06f-p.descent();
                    frame.canvas.drawText(ruby.reading,rubyX,rubyY,p);
                    if (first("ruby:"+frame.data.song.id)) emit("RUBY_DRAWN song="+frame.data.song.id
                            +" bodyX="+x+" bodyY="+y+" rubyX="+rubyX+" rubyY="+rubyY+" size="+p.getTextSize());
                }
            } catch (Throwable e) { onceFailure("ruby_draw",e); }
            return result;
        } finally { frame.busy=false; }
    }

    private Object invoke(Object target, String name) throws Exception {
        return target.getClass().getMethod(name).invoke(target);
    }

    /** Binder reads run off the drawing thread; changing the option rebuilds host layout on main. */
    private synchronized void pollFeatures() {
        if(context==null) return;
        long now=SystemClock.elapsedRealtime();
        if(now-lastFeaturePoll<1000) return;
        lastFeaturePoll=now;
        writer.execute(() -> {
            try {
                Bundle options=context.getContentResolver().call(Uri.parse("content://dev.local.ncmfurigana.report"),"features",null,null);
                if(options==null) return;
                boolean enabled=options.getBoolean("rubyEnabled",true);
                if(enabled!=rubyEnabled) new android.os.Handler(context.getMainLooper()).post(() -> applyRubyOption(enabled));
            } catch(Throwable e) {onceFailure("feature_read",e);}
        });
    }

    private void applyRubyOption(boolean enabled) {
        if(enabled==rubyEnabled) return;
        rubyEnabled=enabled;active.remove();
        // Revert only layout values that still match our own reservation.
        synchronized(layouts) {
            for(Map.Entry<Object,LayoutData> entry:layouts.entrySet()) {
                try {
                    Object info=entry.getKey();LayoutData data=entry.getValue();
                    if(info!=null && data.reservation.matches(data.heights,info.getClass().getField("a").getInt(info))) {
                        data.reservation.restore(data.heights);
                        info.getClass().getField("a").setInt(info,data.reservation.baseTotal);
                    }
                } catch(Throwable e) {onceFailure("feature_restore",e);}
            }
            layouts.clear();
        }
        synchronized(views) {
            for(Map.Entry<View,SongState> entry:views.entrySet()) {
                entry.getValue().cancelled=true;
                View view=entry.getKey();if(view==null) continue;
                try {
                    Object renderer=invoke(view,"getLyric");
                    if(renderer!=null) for(String name:new String[]{"sentenceInfoMap","locationArray"}) {
                        Class<?> type=renderer.getClass();
                        while(type!=null) {
                            try {
                                Field field=type.getDeclaredField(name);field.setAccessible(true);
                                Object cache=field.get(renderer);
                                if(cache!=null) cache.getClass().getMethod("clear").invoke(cache);
                                break;
                            } catch(NoSuchFieldException e) {type=type.getSuperclass();}
                        }
                    }
                } catch(Throwable e) {onceFailure("feature_refresh",e);}
                view.requestLayout();view.invalidate();
            }
        }
        renderers.clear();lastRecovery.clear();
        synchronized(lyricViews) {
            for(View view:lyricViews) {view.requestLayout();view.invalidate();}
        }
        emit("RUBY_OPTION enabled="+enabled);
    }
    private synchronized boolean first(String key) {
        if (!seen.add(key)) return false;
        if (seen.size() > 128) seen.remove(seen.iterator().next());
        return true;
    }
    private void onceFailure(String stage, Throwable e) { if (first("error:" + stage)) failure(stage, e); }
    private void failure(String stage, Throwable e) {
        StringBuilder details=new StringBuilder();
        Throwable cause=e;
        for (int i=0;i<4 && cause!=null;i++,cause=cause.getCause()) {
            details.append(cause.getClass().getSimpleName()).append(": ").append(cause.getMessage()).append("; ");
        }
        emit("ERROR " + stage + " " + details);
    }
    private synchronized void attachContext(Context ctx) {
        if (context != null) return;
        context = ctx.getApplicationContext();
        if (context == null) return;
        pollFeatures();
        while (!pending.isEmpty()) send(pending.removeFirst());
        try {
            PackageInfo pi = context.getPackageManager().getPackageInfo(TARGET, 0);
            emit("TARGET version=" + pi.versionName + " code=" + pi.getLongVersionCode()
                    + " expected=9.0.40/9004000 uptime=" + SystemClock.elapsedRealtime());
        } catch (Exception e) { failure("version", e); }
    }
    private synchronized void emit(String event) {
        Log.i(TAG,event);
        try { log(Log.INFO, TAG, event); } catch (Throwable ignored) { }
        if (context == null) {
            if (pending.size() == 30) pending.removeFirst();
            pending.addLast(event);
        } else send(event);
    }
    private void send(String event) {
        writer.execute(() -> {
            try {
                Bundle data = new Bundle(); data.putString("event", event);
                context.getContentResolver().call(Uri.parse("content://dev.local.ncmfurigana.report"), "append", null, data);
            } catch (Throwable e) {
                if (!providerError) { providerError = true; Log.w(TAG, "Diagnostic storage unavailable; use Vector module log", e); }
            }
        });
    }
}

