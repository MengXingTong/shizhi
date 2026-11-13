package com.shizhi.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.webkit.*;
import org.json.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MainActivity extends Activity implements Engine.Listener {
    private ShizhiApp app;private WebView web;private boolean ready;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final AtomicBoolean testing=new AtomicBoolean();private volatile Net.Cancel testCancel;
    @Override public void onCreate(Bundle b){super.onCreate(b);app=(ShizhiApp)getApplication();
        getWindow().setStatusBarColor(Color.rgb(246,247,242));getWindow().setNavigationBarColor(Color.rgb(246,247,242));
        android.widget.FrameLayout root=new android.widget.FrameLayout(this);root.setBackgroundColor(Color.rgb(246,247,242));web=new WebView(this);root.addView(web);setContentView(root);
        if(Build.VERSION.SDK_INT>=35){root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});root.requestApplyInsets();}
        web.setBackgroundColor(Color.rgb(246,247,242));WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setCacheMode(WebSettings.LOAD_NO_CACHE);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);settings.setDomStorageEnabled(false);settings.setSupportMultipleWindows(false);
        web.addJavascriptInterface(new Bridge(),"AndroidBridge");
        web.setWebViewClient(new WebViewClient(){
            @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){
                Uri u=r.getUrl();if(!"https".equals(u.getScheme())||!"app.shizhi.local".equals(u.getHost()))return empty();
                String file=u.getPath().substring(1);if(file.isEmpty())file="index.html";if(file.contains("..")||!file.matches("[a-zA-Z0-9_.-]+"))return empty();
                try{String mime=file.endsWith(".js")?"text/javascript":file.endsWith(".css")?"text/css":file.endsWith(".svg")?"image/svg+xml":"text/html";return new WebResourceResponse(mime,"UTF-8",getAssets().open(file));}catch(Exception e){return empty();}
            }
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return true;}
            @Override public void onPageFinished(WebView v,String url){ready=true;changed();}
        });web.loadUrl("https://app.shizhi.local/index.html");app.engine.addListener(this);
    }
    private WebResourceResponse empty(){return new WebResourceResponse("text/plain","UTF-8",new java.io.ByteArrayInputStream(new byte[0]));}
    @Override public void changed(){runOnUiThread(()->{if(!ready)return;try{send(new JSONObject().put("event","state").put("data",app.engine.snapshot()));}catch(Exception e){sendError("",e);}});}
    private void send(JSONObject value){if(!isDestroyed())web.evaluateJavascript("window.ShizhiNative&&window.ShizhiNative.receive("+value.toString()+")",null);}
    private void result(String id,Object data){try{send(new JSONObject().put("id",id).put("ok",true).put("data",data==null?JSONObject.NULL:data));}catch(Exception ignored){}}
    private void sendError(String id,Exception e){try{send(new JSONObject().put("id",id).put("ok",false).put("error",e.getMessage()==null?"操作失败":e.getMessage()));}catch(Exception ignored){}}
    private void notificationPermission(){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},8);}
    private void startProducer(String id,String bvid){startProducer(id,bvid,false);}
    private void startProducer(String id,String bvid,boolean resume){
        if(!resume&&bvid.isEmpty()&&app.db.pending()>0){
            new AlertDialog.Builder(this).setTitle("按最新偏好重新刷视频？").setMessage("旧待播将移到历史，当前播报与收藏不变。只想继续听和补货，请取消后选择继续刷取。")
                .setPositiveButton("重新刷视频",(d,w)->beginProducer(id,bvid,false)).setNegativeButton("保留当前队列",(d,w)->result(id,false)).setOnCancelListener(d->result(id,false)).show();return;
        }
        beginProducer(id,bvid,resume);
    }
    private void beginProducer(String id,String bvid,boolean resume){
        try{JSONObject cfg=app.settings.read();String url=cfg.optString("baseUrl");
            Runnable start=()->{try{notificationPermission();if(bvid.isEmpty()&&!resume){app.engine.restartFeed(error->{if(error==null)result(id,true);else sendError(id,error);});return;}if(resume)app.engine.resumeScan();else app.engine.startVideo(bvid);result(id,true);}catch(Exception e){sendError(id,e);}};
            if(!url.equals(cfg.optString("consentUrl"))){new AlertDialog.Builder(this).setTitle("确认提炼数据的去向").setMessage("偏好、视频元信息与所读字幕将发送至：\n"+url+"\n\n会产生该服务的模型费用。B 站 Cookie 不会发给模型；满待播上限即暂停提炼。").setPositiveButton("同意并开始",(d,w)->{try{JSONObject j=app.settings.read();j.put("consentUrl",url);app.settings.write(j);start.run();}catch(Exception e){sendError(id,e);}}).setNegativeButton("取消",(d,w)->sendError(id,new Exception("已取消，未开始请求"))).setOnCancelListener(d->sendError(id,new Exception("已取消，未开始请求"))).show();}
            else start.run();
        }catch(Exception e){sendError(id,e);}
    }
    private void testApi(String id){
        new AlertDialog.Builder(this).setTitle("测试模型接口").setMessage("将使用已保存的配置发送一次小型模型请求，可能产生少量费用。不会自动重试。").setPositiveButton("发送测试",(d,w)->{
            if(!testing.compareAndSet(false,true)){sendError(id,new Exception("已有测试请求进行中"));return;}
            final Net.Cancel token=new Net.Cancel();testCancel=token;
            io.execute(()->{try{JSONObject res=new LlmClient(app.settings.read()).chat("只返回 JSON。","返回 {\"ok\":true}",128,token);runOnUiThread(()->result(id,res));}catch(Exception e){runOnUiThread(()->sendError(id,e));}finally{testing.set(false);testCancel=null;}});
        }).setNegativeButton("取消",(d,w)->sendError(id,new Exception("已取消测试"))).setOnCancelListener(d->sendError(id,new Exception("已取消测试"))).show();
    }
    private void probe(String id,String bvid){
        if(!testing.compareAndSet(false,true)){sendError(id,new Exception("已有诊断请求进行中"));return;}
        final Net.Cancel token=new Net.Cancel();testCancel=token;
        io.execute(()->{try{BiliClient client=new BiliClient(app.settings.read().optString("cookie"));String name=client.username(token);JSONObject v;
            if(!bvid.isEmpty())v=client.video(bvid,token);else{JSONArray feed=client.feed(1,token);if(feed.length()==0)throw new Exception("没有读取到候选，请填写一个有字幕的 BVID 再诊断");v=feed.getJSONObject(0);}
            BiliClient.Transcript t=client.subtitles(v,12000,token);JSONObject bound=t==null?v:t.video();JSONObject data=new JSONObject().put("username",name).put("title",bound.optString("title")).put("bvid",bound.optString("bvid")).put("cid",bound.optLong("cid")).put("subtitleAvailable",t!=null).put("subtitleHost",t==null?"":t.sourceHost).put("characters",t==null?0:t.text.length()).put("scope",t==null?"无可用字幕":t.partial?"字幕开头节选":"完整可用字幕");runOnUiThread(()->result(id,data));
        }catch(Exception e){runOnUiThread(()->sendError(id,e));}finally{testing.set(false);testCancel=null;}});
    }
    private void toggleFavorite(String requestId,long itemId)throws Exception{
        JSONObject item=app.db.get(itemId);if(item==null)throw new Exception("内容已过期或被清理");
        Runnable toggle=()->{app.db.favorite(itemId);result(requestId,true);app.engine.notifyChanged();};
        if(item.optBoolean("favorite")&&RetentionPolicy.expires(item.optLong("created"),System.currentTimeMillis(),false,"active".equals(item.optString("status")))){
            new AlertDialog.Builder(this).setTitle("取消收藏？").setMessage("这条内容已超过 7 天。取消收藏后将按历史清理规则移除。")
                .setPositiveButton("取消收藏并清理",(d,w)->toggle.run()).setNegativeButton("保留收藏",(d,w)->result(requestId,false)).setOnCancelListener(d->result(requestId,false)).show();
        }else toggle.run();
    }
    private void reextract(String requestId,long id)throws Exception {
        JSONObject item=app.db.get(id);if(item==null)throw new Exception("内容已过期或被清理");
        if(app.engine.busy())throw new Exception("请先暂停刷取，待在途请求结束后再重新提炼");
        new AlertDialog.Builder(this).setTitle("重新提炼这条视频？").setMessage("将重新读取来源并调用你配置的模型，可能产生费用。成功后替换旧摘要，保留收藏和记录；失败不会删除旧内容。")
            .setPositiveButton("确认重新提炼",(d,w)->startProducer(requestId,item.optString("bvid"))).setNegativeButton("取消",(d,w)->result(requestId,false)).setOnCancelListener(d->result(requestId,false)).show();
    }
    private final class Bridge{
        @JavascriptInterface public void postMessage(String raw){if(raw.length()>50000)return;runOnUiThread(()->{String id="";
            try{JSONObject req=new JSONObject(raw);id=req.getString("id");JSONObject p=req.optJSONObject("payload");if(p==null)p=new JSONObject();
                switch(req.getString("action")){
                    case"state":result(id,app.engine.snapshot());return;
                    case"start":startProducer(id,"");return;
                    case"resumeScan":startProducer(id,"",true);return;
                    case"pauseScan":app.engine.pauseScan();break;
                    case"reextract":reextract(id,p.getLong("id"));return;
                    case"addVideo":{String bv=p.optString("bvid");long existing=app.db.findVideo(bv);if(existing!=0){JSONObject old=app.db.get(existing);if(!SourceBinding.verified(old)){reextract(id,existing);return;}notificationPermission();app.engine.play(existing);break;}startProducer(id,bv);return;}
                    case"stop":app.engine.stop();break;
                    case"stopAll":app.engine.stopAll();break;
                    case"toggle":notificationPermission();app.engine.toggle();break;
                    case"pausePlayback":app.engine.pause();break;
                    case"next":app.engine.next();break;
                    case"previous":app.engine.previous();break;
                    case"play":notificationPermission();app.engine.play(p.getLong("id"));break;
                    case"favorite":toggleFavorite(id,p.getLong("id"));return;
                    case"list":result(id,app.db.list(p.optString("kind"),p.optInt("offset"),50));return;
                    case"saveApi":if(app.engine.busy())throw new Exception("请先暂停刷取，待在途请求结束后再改接口");app.settings.saveApi(p);break;
                    case"saveFeature":app.settings.saveFeature(p);app.engine.configure();break;
                    case"testApi":testApi(id);return;
                    case"probe":probe(id,p.optString("bvid"));return;
                    case"testSpeech":app.engine.testSpeech();break;
                    case"ttsSettings":startActivity(new Intent("com.android.settings.TTS_SETTINGS"));break;
                    case"login":startActivity(new Intent(MainActivity.this,LoginActivity.class));break;
                    case"logout":app.engine.stop();app.settings.setLogin("","");CookieManager.getInstance().removeAllCookies(null);CookieManager.getInstance().flush();break;
                    case"openSource":{String bv=p.getString("bvid");int at=p.optInt("at");SourceNavigation.url(bv,at);if(app.engine.isSpeaking())app.engine.pause();startActivity(new Intent(MainActivity.this,SourceActivity.class).putExtra("bvid",bv).putExtra("at",at));break;}
                    default:throw new Exception("不支持的操作");
                }result(id,true);app.engine.notifyChanged();
            }catch(Exception e){sendError(id,e);}
        });}
    }
    @Override public void onResume(){super.onResume();if(app!=null)changed();}
    @Override public void onDestroy(){app.engine.removeListener(this);ready=false;if(testCancel!=null)testCancel.stop();web.removeJavascriptInterface("AndroidBridge");web.destroy();io.shutdownNow();super.onDestroy();}
}