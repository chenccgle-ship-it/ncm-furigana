package dev.local.ncmfurigana;

import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;

/** Native settings surface; playback, IPC and lyric rendering remain in their existing classes. */
public final class MainActivity extends Activity {
    private static final int INK=0xff202124, MUTED=0xff66696d, SURFACE=0xfff3f4f5, ACCENT=0xffc9323d;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private boolean updating, selectingQQ;
    private Switch serviceSwitch;
    private Switch rubySwitch;
    private TextView rubyStatus;
    private TextView serviceStatus;
    private Button kuwo,qq,save;
    private LinearLayout cookieGroup;
    private EditText cookie;
    private android.content.SharedPreferences settings;
    private final Runnable updateStatus=new Runnable() {
        @Override public void run() {refresh();handler.postDelayed(this,1000);}
    };
    private int dp(float n) {return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    private LinearLayout column() {LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private LinearLayout row() {LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private void gap(LinearLayout parent,int height) {View v=new View(this);parent.addView(v,new LinearLayout.LayoutParams(1,dp(height)));}
    private TextView text(String value,int size,int color,boolean bold) {
        TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(color);
        v.setFontFeatureSettings("kern");v.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));
        v.setIncludeFontPadding(false);v.setLineSpacing(dp(3),1);return v;
    }
    private void background(View v,int color) {
        v.setBackground(new RippleDrawable(ColorStateList.valueOf(0x18000000),new ColorDrawable(color),new ColorDrawable(Color.WHITE)));
    }
    private Button button(String label,int fill,int ink,Runnable action) {
        Button v=new Button(this);v.setText(label);v.setTextSize(15);v.setTextColor(ink);v.setAllCaps(false);
        v.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));v.setMinHeight(0);v.setMinimumHeight(dp(48));
        v.setPadding(dp(12),dp(12),dp(12),dp(12));v.setStateListAnimator(null);v.setElevation(0);background(v,fill);
        v.setOnClickListener(w -> action.run());return v;
    }
    private Switch featureSwitch(String description) {
        Switch v=new Switch(this);v.setContentDescription(description);v.setShowText(false);v.setMinHeight(dp(48));
        v.setThumbTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{ACCENT,0xff777b80}));
        v.setTrackTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{0xffefbfc3,0xffd5d7da}));
        return v;
    }
    private String reportText() {
        return "网易云注音与音源 0.5.4 / API 102\n设备："+Build.MANUFACTURER+" "+Build.MODEL+" / Android "+Build.VERSION.RELEASE
            +"\n\n"+ReportProvider.read(this);
    }
    private void refresh() {
        if(serviceStatus==null) return;
        boolean ruby=getSharedPreferences("features",0).getBoolean("rubyEnabled",true);
        String rubyDescription=ruby?"已开启 · 日语歌词注音":"已关闭";
        if(!rubyDescription.contentEquals(rubyStatus.getText())) rubyStatus.setText(rubyDescription);
        if(rubySwitch.isChecked()!=ruby) {
            updating=true;rubySwitch.setChecked(ruby);updating=false;
        }
        Bundle c=ResolverService.config(this);boolean enabled=c.getBoolean("enabled",false);
        String name="qq".equals(settings.getString("source","kuwo"))?"QQ 音乐":"酷我音乐";
        String description=enabled?(c.getInt("port",0)>0?name+" · 运行中":"正在启动音源服务…"):"已关闭，注音不受影响";
        if(!description.contentEquals(serviceStatus.getText())) serviceStatus.setText(description);
        if(serviceSwitch.isChecked()!=enabled) {
            updating=true;serviceSwitch.setChecked(enabled);updating=false;
        }
    }
    private boolean saveSettings() {
        String value=cookie.getText().toString().trim();
        if(value.length()>16384||value.contains("\r")||value.contains("\n")) {
            Toast.makeText(this,"Cookie 应为一行文本，且长度不超过 16 KB",Toast.LENGTH_LONG).show();return false;
        }
        settings.edit().putString("source",selectingQQ?"qq":"kuwo").putString("qqCookie",value).apply();
        save.setText("保存设置");return true;
    }
    private void choose(boolean useQQ,boolean changed) {
        selectingQQ=useQQ;
        background(kuwo,useQQ?SURFACE:INK);kuwo.setTextColor(useQQ?INK:Color.WHITE);
        background(qq,useQQ?INK:SURFACE);qq.setTextColor(useQQ?Color.WHITE:INK);
        kuwo.setSelected(!useQQ);qq.setSelected(useQQ);cookieGroup.setVisibility(useQQ?View.VISIBLE:View.GONE);
        if(changed) save.setText("保存音源设置");
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        settings=getSharedPreferences(ResolverService.PREFS,0);
        getWindow().setStatusBarColor(Color.WHITE);getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(Color.WHITE);
        LinearLayout page=column();page.setPadding(dp(24),dp(24),dp(24),dp(24));page.setFocusableInTouchMode(true);scroll.addView(page);
        if(Build.VERSION.SDK_INT>=30) scroll.setOnApplyWindowInsetsListener((view,insets) -> {
            android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());
            view.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;
        });
        page.addView(text("注音与音源",26,INK,true));gap(page,8);
        page.addView(text("日语歌词注音，播放更自在。",14,MUTED,false));gap(page,28);

        LinearLayout rubyRow=row();rubyRow.setPadding(dp(18),dp(18),dp(12),dp(18));rubyRow.setBackgroundColor(SURFACE);
        LinearLayout rubyLabels=column();rubyLabels.addView(text("注音",17,INK,true));gap(rubyLabels,8);
        rubyStatus=text("已开启 · 日语歌词注音",13,MUTED,false);rubyLabels.addView(rubyStatus);
        rubyRow.addView(rubyLabels,new LinearLayout.LayoutParams(0,-2,1));
        rubySwitch=featureSwitch("开启或关闭注音");rubyRow.addView(rubySwitch);page.addView(rubyRow);gap(page,12);
        rubySwitch.setOnCheckedChangeListener((v,checked) -> {
            if(updating) return;
            getSharedPreferences("features",0).edit().putBoolean("rubyEnabled",checked).apply();
            refresh();
            Toast.makeText(this,checked?"注音已开启，返回歌词页生效":"注音已关闭，返回歌词页生效",Toast.LENGTH_SHORT).show();
        });

        LinearLayout service=row();service.setPadding(dp(18),dp(18),dp(12),dp(18));service.setBackgroundColor(SURFACE);
        LinearLayout status=column();status.addView(text("备用音源",17,INK,true));gap(status,8);
        serviceStatus=text("已关闭，注音不受影响",13,MUTED,false);status.addView(serviceStatus);
        service.addView(status,new LinearLayout.LayoutParams(0,-2,1));
        serviceSwitch=featureSwitch("开启或停止备用音源");
        service.addView(serviceSwitch);page.addView(service);gap(page,28);

        page.addView(text("播放音源",17,INK,true));gap(page,8);
        page.addView(text("原曲不可用或仅可试听时，尝试所选音源。",14,MUTED,false));gap(page,16);
        LinearLayout choices=row();
        kuwo=button("酷我音乐",INK,Color.WHITE,() -> choose(false,true));
        qq=button("QQ 音乐",SURFACE,INK,() -> choose(true,true));
        LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,-2,1);left.rightMargin=dp(8);
        choices.addView(kuwo,left);choices.addView(qq,new LinearLayout.LayoutParams(0,-2,1));page.addView(choices);
        cookieGroup=column();gap(cookieGroup,20);
        cookieGroup.addView(text("QQ 音乐 Cookie · 可选",14,INK,false));gap(cookieGroup,10);
        cookie=new EditText(this);cookie.setSingleLine(true);cookie.setTextSize(14);cookie.setTextColor(INK);cookie.setHintTextColor(MUTED);
        cookie.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);cookie.setHint("粘贴你的 Cookie");
        cookie.setPadding(dp(14),dp(14),dp(14),dp(14));cookie.setBackgroundColor(SURFACE);
        cookie.setText(settings.getString("qqCookie",""));cookie.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        cookieGroup.addView(cookie,new LinearLayout.LayoutParams(-1,-2));gap(cookieGroup,10);
        cookieGroup.addView(text("仅保存在本机，不写入诊断日志。",13,MUTED,false));page.addView(cookieGroup);gap(page,16);
        save=button("保存设置",SURFACE,INK,() -> {
            if(!saveSettings()) return;
            if(ResolverService.config(this).getBoolean("enabled",false)) {
                startForegroundService(new Intent(this,ResolverService.class).setAction("reload"));
                Toast.makeText(this,"已保存，正在重新载入音源",Toast.LENGTH_SHORT).show();
            } else Toast.makeText(this,"已保存",Toast.LENGTH_SHORT).show();
            page.clearFocus();cookie.clearFocus();
            ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(cookie.getWindowToken(),0);
            refresh();
        });page.addView(save,new LinearLayout.LayoutParams(-1,-2));
        choose("qq".equals(settings.getString("source","kuwo")),false);
        serviceSwitch.setOnCheckedChangeListener((v,checked) -> {
            if(updating) return;
            if(checked) {
                if(!saveSettings()) {refresh();return;}
                try {startForegroundService(new Intent(this,ResolverService.class));}
                catch(Exception e) {Toast.makeText(this,"音源服务启动失败",Toast.LENGTH_SHORT).show();}
            } else stopService(new Intent(this,ResolverService.class));
            handler.postDelayed(this::refresh,200);
        });
        gap(page,24);
        page.addView(button("打开网易云",ACCENT,Color.WHITE,() -> {
            Intent launch=getPackageManager().getLaunchIntentForPackage("com.netease.cloudmusic");
            if(launch!=null) startActivity(launch);else Toast.makeText(this,"没有找到网易云音乐",Toast.LENGTH_SHORT).show();
        }),new LinearLayout.LayoutParams(-1,-2));gap(page,18);
        Button diagnostic=button("运行诊断  ›",Color.WHITE,INK,this::showDiagnostics);
        page.addView(diagnostic,new LinearLayout.LayoutParams(-1,-2));gap(page,16);
        page.addView(text("0.5.4 · 网易云 9.0.40 · API 102",13,MUTED,false));
        setContentView(scroll);page.requestFocus();scroll.requestApplyInsets();refresh();
    }
    private void showDiagnostics() {
        LinearLayout content=column();content.setPadding(dp(20),dp(8),dp(20),dp(4));
        TextView logs=text(ReportProvider.read(this),13,INK,false);logs.setTextIsSelectable(true);
        ScrollView scroll=new ScrollView(this);scroll.addView(logs);
        content.addView(scroll,new LinearLayout.LayoutParams(-1,dp(300)));
        LinearLayout actions=row();
        actions.addView(button("分享",Color.WHITE,INK,() -> {
            Intent share=new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,reportText());
            startActivity(Intent.createChooser(share,"分享诊断"));
        }),new LinearLayout.LayoutParams(0,-2,1));
        actions.addView(button("清空",Color.WHITE,INK,() -> {
            getSharedPreferences("probe",0).edit().remove("report").apply();logs.setText(ReportProvider.read(this));
        }),new LinearLayout.LayoutParams(0,-2,1));content.addView(actions);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("运行诊断").setView(content)
            .setPositiveButton("复制",null).setNeutralButton("测试连接",null).setNegativeButton("关闭",null).create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("模块诊断",reportText()));
                Toast.makeText(this,"诊断已复制",Toast.LENGTH_SHORT).show();
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(false);
                new Thread(() -> {
                    try {
                        ResolverClient client=new ResolverClient(() -> MainActivity.this);
                        org.json.JSONObject health=client.request("/health");
                        ResolverService.event(this,"RESOLVER_HEALTH ok="+health.optBoolean("ok")+" node="+health.optString("node"));
                        org.json.JSONObject song=client.resolve(347230L);
                        ResolverService.event(this,"RESOLVER_TEST song=347230 matched=true source="+song.optString("source"));
                    } catch(Exception e) {ResolverService.event(this,"RESOLVER_TEST failed="+e.getClass().getSimpleName());}
                    runOnUiThread(() -> {if(!isDestroyed()&&dialog.isShowing()) {logs.setText(ReportProvider.read(this));dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(true);}});
                },"NcmResolverTest").start();
            });
        });dialog.show();
    }
    @Override public void onResume() {super.onResume();handler.removeCallbacks(updateStatus);handler.post(updateStatus);}
    @Override public void onPause() {handler.removeCallbacks(updateStatus);super.onPause();}
}

