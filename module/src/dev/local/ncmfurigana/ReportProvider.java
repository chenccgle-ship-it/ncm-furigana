package dev.local.ncmfurigana;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Only the target app can append; only the companion UID can read or clear. */
public final class ReportProvider extends ContentProvider {
    private final java.util.LinkedHashMap<Long,Long> matches=new java.util.LinkedHashMap<>();
    public static String read(Context context) {
        return context.getSharedPreferences("probe", 0).getString("report", "尚无记录。请先启用模块，强行停止并重新打开网易云，再打开歌词页。");
    }

    private void requireCaller(boolean append) {
        if (Binder.getCallingUid() == Process.myUid()) return;
        String[] packages = getContext().getPackageManager().getPackagesForUid(Binder.getCallingUid());
        if (append && packages != null) {
            for (String name : packages) if ("com.netease.cloudmusic".equals(name)) return;
        }
        throw new SecurityException("Caller is outside the diagnostic scope");
    }

    @Override public boolean onCreate() { return true; }

    @Override public synchronized Bundle call(String method, String arg, Bundle extras) {
        requireCaller("append".equals(method)||"resolver".equals(method)||"features".equals(method)||"matched".equals(method)||"isMatched".equals(method));
        if ("matched".equals(method)||"isMatched".equals(method)) {
            long id=extras==null?0:extras.getLong("song",0);
            long now=android.os.SystemClock.elapsedRealtime();
            java.util.Iterator<java.util.Map.Entry<Long,Long>> it=matches.entrySet().iterator();
            while(it.hasNext()) if(it.next().getValue()<now) it.remove();
            if("matched".equals(method)&&id>0) {
                matches.put(id,now+30*60*1000L);
                if(matches.size()>128) matches.remove(matches.keySet().iterator().next());
            }
            Bundle result=new Bundle();result.putBoolean("matched",id>0&&matches.containsKey(id));return result;
        }
        if ("features".equals(method)) {
            Bundle result=new Bundle();
            result.putBoolean("rubyEnabled",getContext().getSharedPreferences("features",0).getBoolean("rubyEnabled",true));
            return result;
        }
        if ("resolver".equals(method)) return ResolverService.config(getContext());
        if (!"append".equals(method) || extras == null) throw new IllegalArgumentException("Unknown operation");
        String event = extras.getString("event", "");
        if (event.length() > 1600) event = event.substring(0, 1600);
        String previous = getContext().getSharedPreferences("probe", 0).getString("report", "");
        String stamp = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.ROOT).format(new Date());
        String next = previous + stamp + " " + event.replace('\r', ' ').replace('\n', ' ') + "\n";
        if (next.length() > 32000) {
            int start = next.indexOf('\n', next.length() - 30000);
            next = next.substring(start + 1);
        }
        getContext().getSharedPreferences("probe", 0).edit().putString("report", next).apply();
        return Bundle.EMPTY;
    }

    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String sort) { throw new UnsupportedOperationException(); }
    @Override public String getType(Uri u) { return "text/plain"; }
    @Override public Uri insert(Uri u, ContentValues v) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri u, String s, String[] a) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { throw new UnsupportedOperationException(); }
}
