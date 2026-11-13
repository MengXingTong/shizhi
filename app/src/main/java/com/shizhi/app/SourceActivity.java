package com.shizhi.app;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import java.util.*;

/** Remote content has no JS bridge and can never auto-close this page or launch another app. */
public final class SourceActivity extends Activity {
    private WebView web;private TextView status;private String source;
    private FrameLayout root;private View fullscreen;private WebChromeClient.CustomViewCallback fullscreenCallback;
    @Override public void onCreate(Bundle state){super.onCreate(state);
        try{source=SourceNavigation.url(getIntent().getStringExtra("bvid"),getIntent().getIntExtra("at",0));}catch(Exception e){finish();return;}
        root=new FrameLayout(this);root.setBackgroundColor(Color.rgb(246,247,242));LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);root.addView(layout);setContentView(root);
        getWindow().setStatusBarColor(Color.rgb(246,247,242));getWindow().setNavigationBarColor(Color.rgb(246,247,242));
        if(Build.VERSION.SDK_INT>=35){root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});root.requestApplyInsets();}
        LinearLayout toolbar=new LinearLayout(this);toolbar.setGravity(Gravity.CENTER_VERTICAL);layout.addView(toolbar);
        Button back=new Button(this);back.setText("返回拾知");back.setOnClickListener(v->finish());toolbar.addView(back);
        TextView title=new TextView(this);title.setText("B 站原视频");title.setTextColor(Color.rgb(32,42,36));title.setGravity(Gravity.CENTER);toolbar.addView(title,new LinearLayout.LayoutParams(0,52,1));
        Button browser=new Button(this);browser.setText("浏览器");browser.setOnClickListener(v->openBrowser());toolbar.addView(browser);
        status=new TextView(this);status.setTextSize(12);status.setPadding(16,8,16,8);status.setText("加载原视频中…；网页不能自动唤起外部应用");layout.addView(status);
        web=new WebView(this);layout.addView(web,new LinearLayout.LayoutParams(-1,0,1));WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setSupportMultipleWindows(false);s.setJavaScriptCanOpenWindowsAutomatically(false);s.setMediaPlaybackRequiresUserGesture(true);
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){boolean blocked=!SourceNavigation.allow(r.getUrl().toString());if(blocked&&r.isForMainFrame())status.setText("已阻止网页唤起外部应用，原视频页面仍保留；需要时可点右上角浏览器");return blocked;}
            @Override public void onPageFinished(WebView v,String url){status.setText("原视频页面 · 不会因登录成功自动返回 · 右上角可选浏览器");}
            @Override public void onReceivedError(WebView v,WebResourceRequest r,WebResourceError e){if(r.isForMainFrame())status.setText("原视频网页加载失败，可点右上角浏览器尝试；不会自动返回");}
            @Override public void onReceivedSslError(WebView v,android.webkit.SslErrorHandler handler,android.net.http.SslError error){handler.cancel();status.setText("证书校验失败，已阻止访问");}
        });
        web.setWebChromeClient(new WebChromeClient(){
            @Override public void onCloseWindow(WebView window){/* Ignore remote window.close(); only user controls close this activity. */}
            @Override public void onShowCustomView(View view,CustomViewCallback callback){if(fullscreen!=null){callback.onCustomViewHidden();return;}fullscreen=view;fullscreenCallback=callback;root.addView(view,new FrameLayout.LayoutParams(-1,-1));}
            @Override public void onHideCustomView(){hideFullscreen();}
        });
        if(state==null||web.restoreState(state)==null)web.loadUrl(source);
    }
    private void hideFullscreen(){if(fullscreen!=null){root.removeView(fullscreen);fullscreen=null;WebChromeClient.CustomViewCallback cb=fullscreenCallback;fullscreenCallback=null;if(cb!=null)cb.onCustomViewHidden();}}
    private void openBrowser(){
        // Resolve a generic HTTPS URL so a Bilibili-only handler cannot intercept the choice.
        Intent probe=new Intent(Intent.ACTION_VIEW,Uri.parse("https://example.org/"));probe.addCategory(Intent.CATEGORY_BROWSABLE);
        List<ResolveInfo> found=getPackageManager().queryIntentActivities(probe,PackageManager.MATCH_DEFAULT_ONLY);ArrayList<String> packages=new ArrayList<>(),labels=new ArrayList<>();
        for(ResolveInfo info:found){String pkg=info.activityInfo.packageName;if(!pkg.equals(getPackageName())&&!packages.contains(pkg)){packages.add(pkg);labels.add(info.loadLabel(getPackageManager()).toString());}}
        if(packages.isEmpty()){status.setText("未检测到可用浏览器，请继续在此查看原视频");return;}
        new AlertDialog.Builder(this).setTitle("选择浏览器（手动打开）").setItems(labels.toArray(new String[0]),(d,n)->{
            Intent intent=new Intent(Intent.ACTION_VIEW,Uri.parse(source));intent.addCategory(Intent.CATEGORY_BROWSABLE);intent.setPackage(packages.get(n));
            try{startActivity(intent);}catch(ActivityNotFoundException e){status.setText("该浏览器无法打开链接，请换一个浏览器");}
        }).setNegativeButton("留在当前页",null).show();
    }
    @Override public void onBackPressed(){if(fullscreen!=null)hideFullscreen();else if(web.canGoBack())web.goBack();else super.onBackPressed();}
    @Override public void onSaveInstanceState(Bundle state){web.saveState(state);super.onSaveInstanceState(state);}
    @Override public void onPause(){if(web!=null)web.onPause();super.onPause();}
    @Override public void onResume(){super.onResume();if(web!=null)web.onResume();}
    @Override public void onDestroy(){hideFullscreen();if(web!=null){web.stopLoading();web.destroy();}super.onDestroy();}
}
