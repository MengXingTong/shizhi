package com.shizhi.app;

import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Net {
    public interface Transport { String send(String url,JSONObject body,String cookie,String key,Cancel cancel) throws Exception; }
    public static final class Cancel {
        final AtomicBoolean cancelled=new AtomicBoolean();
        volatile HttpURLConnection connection;
        private int connectTimeout=15000,readTimeout=60000;
        public Cancel timeouts(int connect,int read){if(connect<1000||read<1000||connect>120000||read>120000)throw new IllegalArgumentException("无效超时");connectTimeout=connect;readTimeout=read;return this;}
        public void stop(){cancelled.set(true);HttpURLConnection c=connection;if(c!=null)c.disconnect();}
        public void check() throws IOException{if(cancelled.get()||Thread.currentThread().isInterrupted())throw new IOException("请求已取消");}
    }
    public static final class HttpFailure extends IOException {
        public final int status; public final String location;
        HttpFailure(int status){this(status,null);}
        HttpFailure(int status,String location){super(status==412||status==429?"服务限流/风控（HTTP "+status+"），已停止，请稍后手动重试":status==401||status==403?"认证失败（HTTP "+status+"），请检查登录或 API Key":"服务返回 HTTP "+status+"，不会自动重试付费请求");this.status=status;this.location=location;}
    }
    public static String request(String url,JSONObject body,String cookie,String key,Cancel cancel) throws Exception {
        try{return once(url,body,cookie,key,cancel,Proxy.NO_PROXY);}
        catch(IOException e){
            cancel.check();if(body!=null||e instanceof HttpFailure)throw e;
            // Only idempotent GETs may fall back, and only after direct networking fails.
            ProxySelector selector=ProxySelector.getDefault();if(selector!=null)for(Proxy p:selector.select(URI.create(url)))if(p!=null&&p.type()!=Proxy.Type.DIRECT)return once(url,null,cookie,key,cancel,p);
            throw e;
        }
    }
    private static String once(String url,JSONObject body,String cookie,String key,Cancel cancel,Proxy proxy) throws Exception {
        cancel.check();URL target=new URL(url);if(!"https".equalsIgnoreCase(target.getProtocol()))throw new IOException("仅允许 HTTPS 请求");
        HttpURLConnection c=(HttpURLConnection)target.openConnection(proxy);cancel.connection=c;
        c.setInstanceFollowRedirects(false);c.setConnectTimeout(cancel.connectTimeout);c.setReadTimeout(cancel.readTimeout);c.setRequestProperty("Accept","application/json");
        c.setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36");
        if(SubtitleUrlPolicy.trustedHost(target.getHost()))c.setRequestProperty("Referer","https://www.bilibili.com/");
        if(target.getHost().equals("api.bilibili.com")&&!cookie.isEmpty())c.setRequestProperty("Cookie",cookie);
        if(!key.isEmpty())c.setRequestProperty("Authorization","Bearer "+key);
        try{
            if(body!=null){c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=utf-8");byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(bytes.length);cancel.check();try(OutputStream out=c.getOutputStream()){out.write(bytes);}}
            cancel.check();int code=c.getResponseCode();if(code<200||code>=300)throw new HttpFailure(code,code>=300&&code<400?c.getHeaderField("Location"):null);
            ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=c.getInputStream()){byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1){cancel.check();if(out.size()+n>4*1024*1024)throw new IOException("响应过大，已停止");out.write(buf,0,n);}}
            cancel.check();return out.toString("UTF-8");
        }finally{c.disconnect();if(cancel.connection==c)cancel.connection=null;}
    }
    public static JSONObject json(String url,String cookie,Cancel c)throws Exception{return new JSONObject(request(url,null,cookie,"",c));}
}