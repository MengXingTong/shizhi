package com.shizhi.app;

import android.app.*;
import android.os.*;
import android.webkit.*;
import android.widget.*;
import android.view.View;
import android.graphics.Color;
import java.util.concurrent.*;

/** Official login, isolated from the application bridge. Never inspect password/captcha DOM. */
public final class LoginActivity extends Activity {
    private WebView web;private Button done;private TextView status;private ProgressBar progress;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final LoginAttemptGate attempts=new LoginAttemptGate();
    private Net.Cancel activeCancel;private boolean resumed,closing;
    private final Runnable poll=new Runnable(){public void run(){if(!resumed||closing||isDestroyed())return;detect(false);main.postDelayed(this,900);}};
    @Override public void onCreate(Bundle b){
        super.onCreate(b);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(16,0,16,8);root.setBackgroundColor(Color.rgb(246,247,242));
        if(Build.VERSION.SDK_INT>=35)root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(android.view.WindowInsets.Type.systemBars()|android.view.WindowInsets.Type.ime());v.setPadding(16+bars.left,bars.top,16+bars.right,8+bars.bottom);return insets;});
        TextView intro=new TextView(this);intro.setText("在 B 站官方网页完成验证码登录。确认成功后会自动返回，无需保存功能设置。登录凭据仅加密保存在本机。");intro.setPadding(8,12,8,6);root.addView(intro);
        LinearLayout feedback=new LinearLayout(this);feedback.setGravity(android.view.Gravity.CENTER_VERTICAL);feedback.setPadding(8,0,8,6);
        progress=new ProgressBar(this);LinearLayout.LayoutParams spinner=new LinearLayout.LayoutParams(48,48);spinner.setMargins(0,0,12,0);feedback.addView(progress,spinner);progress.setVisibility(View.GONE);
        status=new TextView(this);status.setText("等待完成官方网页登录，正在自动检测…");status.setTextSize(12);feedback.addView(status,new LinearLayout.LayoutParams(0,-2,1));root.addView(feedback);
        done=new Button(this);done.setText("已登录但未返回？重新检查");root.addView(done);done.setOnClickListener(v->detect(true));
        web=new WebView(this);root.addView(web,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
        WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        CookieManager.getInstance().setAcceptCookie(true);CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){android.net.Uri u=r.getUrl();String h=u.getHost();return !"https".equals(u.getScheme())||h==null||!(h.equals("bilibili.com")||h.endsWith(".bilibili.com"));}
            @Override public void onPageFinished(WebView v,String url){detect(false);}
            @Override public void doUpdateVisitedHistory(WebView v,String url,boolean reload){detect(false);}
        });
        web.loadUrl("https://passport.bilibili.com/login");
    }
    private String cookies(){CookieManager c=CookieManager.getInstance();return BiliLoginCookies.merge(
        c.getCookie("https://api.bilibili.com/"),c.getCookie("https://www.bilibili.com/"),c.getCookie("https://passport.bilibili.com/"),c.getCookie("https://bilibili.com/"));}
    private void detect(boolean manual){
        if(closing||isFinishing()||isDestroyed()||(!manual&&!resumed))return;
        String captured=cookies();String fingerprint=BiliLoginCookies.fingerprint(captured);
        if(fingerprint.isEmpty()){if(manual)status.setText("还未检测到完整登录凭据。请完成验证码步骤；成功后会自动返回。无需刷新网页。");return;}
        long token=attempts.begin(fingerprint,manual);if(token==0)return;
        Net.Cancel cancel=new Net.Cancel().timeouts(8000,10000);activeCancel=cancel;
        done.setEnabled(false);done.setText("正在确认登录状态…");progress.setVisibility(View.VISIBLE);status.setText("已检测到登录凭据，正在确认 B 站账号…");
        io.execute(()->{
            try{String name=new BiliClient(captured).username(cancel);cancel.check();main.post(()->verified(token,cancel,captured,name));}
            catch(Exception e){main.post(()->failed(token,cancel,e));}
        });
    }
    private void verified(long token,Net.Cancel cancel,String cookies,String name){
        if(closing||isFinishing()||isDestroyed()||!attempts.current(token)||cancel.cancelled.get())return;
        try{
            // Commit on main so closing/logout cannot race a late background credential write.
            ShizhiApp app=(ShizhiApp)getApplication();app.settings.setLogin(cookies,name);attempts.finish(token);closing=true;
            main.removeCallbacks(poll);progress.setVisibility(View.GONE);status.setText("已登录："+name+"，正在返回…");
            app.engine.notifyChanged();setResult(RESULT_OK);Toast.makeText(this,"已登录："+name,Toast.LENGTH_SHORT).show();finish();
        }catch(Exception e){failed(token,cancel,e);}
    }
    private void failed(long token,Net.Cancel cancel,Exception error){
        if(closing||isFinishing()||isDestroyed()||cancel.cancelled.get()||!attempts.finish(token))return;
        activeCancel=null;progress.setVisibility(View.GONE);done.setEnabled(true);done.setText("重新检查登录状态");
        status.setText("网页可能已经登录，但账号确认未完成："+(error.getMessage()==null?"网络验证失败":error.getMessage())+"。可以点上方按钮重试，不必重新获取验证码。");
    }
    @Override public void onResume(){super.onResume();resumed=true;main.removeCallbacks(poll);main.post(poll);}
    @Override public void onPause(){resumed=false;main.removeCallbacks(poll);super.onPause();}
    @Override public void onDestroy(){
        closing=true;attempts.close();if(activeCancel!=null)activeCancel.stop();main.removeCallbacksAndMessages(null);
        CookieManager.getInstance().removeAllCookies(value->CookieManager.getInstance().flush());WebStorage.getInstance().deleteAllData();
        web.destroy();io.shutdownNow();super.onDestroy();
    }
}