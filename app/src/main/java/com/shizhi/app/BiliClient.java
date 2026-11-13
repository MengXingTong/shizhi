package com.shizhi.app;

import org.json.*;
import java.io.IOException;
import java.net.URI;
import java.util.*;

public final class BiliClient {
    private final String cookie;
    public BiliClient(String cookie) { this.cookie=cookie; }
    private JSONObject api(String path,Net.Cancel cancel) throws Exception {
        JSONObject j=Net.json("https://api.bilibili.com/"+path,cookie,cancel); int code=j.optInt("code",-1);
        if(code!=0) { if(code==-101)throw new IOException("B 站登录已失效，请重新登录"); throw new IOException("B 站接口返回 "+code+"："+j.optString("message","请求失败")); }
        return j.getJSONObject("data");
    }
    public String username(Net.Cancel c) throws Exception { JSONObject j=api("x/web-interface/nav",c); if(!j.optBoolean("isLogin"))throw new IOException("尚未登录 B 站");return j.optString("uname","B 站用户"); }
    private JSONObject detail(String bv,Net.Cancel c)throws Exception {
        if(!bv.matches("BV[A-Za-z0-9]{10}"))throw new IllegalArgumentException("请填写有效 BVID");
        JSONObject d=api("x/web-interface/view?bvid="+bv,c);JSONArray pages=d.optJSONArray("pages");
        if(!bv.equals(d.optString("bvid"))||d.optLong("aid")<=0)throw new SubtitleUnavailable("视频详情缺少身份信息或 BVID 不匹配，已跳过");
        if(pages==null||pages.length()==0||pages.getJSONObject(0).optInt("page")!=1||d.optLong("cid")<=0||d.optLong("cid")!=pages.getJSONObject(0).optLong("cid"))
            throw new SubtitleUnavailable("视频详情与第一分P的 CID 不一致，已跳过");
        if(d.optString("title").trim().isEmpty()||d.optJSONObject("owner")==null)throw new SubtitleUnavailable("视频详情缺少标题或作者，已跳过");
        return d;
    }
    private JSONObject canonical(JSONObject d)throws Exception {
        return new JSONObject().put("bvid",d.getString("bvid")).put("aid",d.getLong("aid")).put("cid",d.getLong("cid"))
            .put("title",d.getString("title")).put("author",d.getJSONObject("owner").optString("name"))
            .put("duration",d.optInt("duration")).put("thumbnail",d.optString("pic").replaceFirst("^http:","https:"));
    }
    public JSONObject video(String bv,Net.Cancel c)throws Exception{return canonical(detail(bv,c));}
    public JSONArray feed(int page,Net.Cancel c) throws Exception {
        JSONObject data=api("x/web-interface/index/top/feed/rcmd?ps=12&fresh_type=3&fresh_idx="+page+"&fresh_idx_1h="+page+"&fetch_row="+page,c);
        JSONArray raw=data.optJSONArray("item"), out=new JSONArray(); if(raw==null)return out;
        for(int i=0;i<raw.length();i++){ JSONObject v=raw.getJSONObject(i); String bv=v.optString("bvid"); if(!"av".equals(v.optString("goto")) || !bv.matches("BV[A-Za-z0-9]{10}") || !v.isNull("business_info"))continue;
            out.put(new JSONObject().put("bvid",bv).put("cid",v.optLong("cid")).put("title",v.optString("title")).put("author",v.optJSONObject("owner")==null?"未知 UP 主":v.getJSONObject("owner").optString("name"))
                .put("duration",v.optInt("duration")).put("thumbnail",v.optString("pic").replaceFirst("^http:","https:")));
        }return out;
    }
    public static final class SubtitleUnavailable extends IOException { public SubtitleUnavailable(String reason){super(reason);} }
    public static final class Transcript {
        public final String text,language,sourceHost;public final boolean partial,multiPart;
        private final String linesJson,videoJson,sourceJson;
        Transcript(JSONObject video,JSONArray lines,String text,boolean partial,String language,boolean multiPart,String sourceHost,String subtitleId,String resource,String document)throws Exception {
            videoJson=video.toString();linesJson=lines.toString();this.text=text;this.partial=partial;this.language=language;this.multiPart=multiPart;this.sourceHost=sourceHost;
            sourceJson=new JSONObject().put("version",1).put("bvid",video.getString("bvid")).put("aid",video.getLong("aid")).put("cid",video.getLong("cid"))
                .put("page",1).put("title",video.getString("title")).put("author",video.getString("author")).put("subtitleId",subtitleId).put("resourceHash",SourceBinding.hash(resource))
                .put("documentHash",SourceBinding.hash(document)).put("textHash",SourceBinding.hash(text)).toString();
            SourceBinding.requireVerified(new JSONObject(videoJson).put("source",new JSONObject(sourceJson)));
        }
        public JSONArray lines()throws Exception{return new JSONArray(linesJson);}
        public JSONObject video()throws Exception{return new JSONObject(videoJson);}
        public JSONObject source()throws Exception{return new JSONObject(sourceJson);}
        public void requireMatches(JSONObject video)throws Exception {
            JSONObject v=video();
            if(!v.getString("bvid").equals(video.optString("bvid"))||v.getLong("cid")!=video.optLong("cid")||v.getLong("aid")!=video.optLong("aid")
                ||!v.getString("title").equals(video.optString("title"))||!v.getString("author").equals(video.optString("author")))
                throw new SubtitleUnavailable("视频与字幕的 BVID / CID / 标题绑定不一致，已阻止提炼或入队");
            if(video.has("source")){JSONObject actual=video.getJSONObject("source"),expected=source();
                for(String key:new String[]{"bvid","cid","aid","page","subtitleId","resourceHash","documentHash","textHash"})
                    if(!expected.optString(key).equals(actual.optString(key)))throw new SubtitleUnavailable("摘要与字幕来源指纹不一致，已阻止入队");
            }
        }
    }
    private static final class SubtitleResource {
        final String document,host;SubtitleResource(String document,String url)throws Exception{this.document=document;host=new URI(url).getHost();}
    }
    private SubtitleResource subtitleDocument(String first,Net.Cancel c)throws Exception{
        String url=first;
        for(int hops=0;hops<=3;hops++){
            c.check();try{return new SubtitleResource(Net.request(url,null,"","",c),url);}
            catch(Net.HttpFailure e){
                if(e.status>=300&&e.status<400){if(hops==3)throw new SubtitleUnavailable("字幕 CDN 跳转次数过多，已跳过");try{url=SubtitleUrlPolicy.redirect(url,e.location);}catch(IOException rejected){throw new SubtitleUnavailable(rejected.getMessage());}continue;}
                if(e.status==401||e.status==403||e.status==404||e.status==410)throw new SubtitleUnavailable("字幕资源不可读取（HTTP "+e.status+"），已跳过，不调用摘要模型");
                throw e;
            }catch(org.json.JSONException e){throw new SubtitleUnavailable("字幕资源不是有效 JSON，已跳过");}
        }throw new SubtitleUnavailable("字幕资源无法读取");
    }
    public Transcript subtitles(JSONObject video,int maxChars,Net.Cancel c)throws Exception{
        String bv=video.getString("bvid");JSONObject detail=detail(bv,c),bound=canonical(detail);
        for(String key:new String[]{"score","reason"})if(video.has(key))bound.put(key,video.get(key));
        long cid=detail.getLong("cid");JSONArray pages=detail.getJSONArray("pages");boolean multi=pages.length()>1;
        JSONObject player=api("x/player/v2?bvid="+bv+"&cid="+cid,c);
        if(player.optBoolean("need_login_subtitle")&&player.optLong("login_mid")==0)throw new IOException("读取字幕需要 B 站登录，请重新登录后再试");
        if(!bv.equals(player.optString("bvid"))||detail.getLong("aid")!=player.optLong("aid"))throw new SubtitleUnavailable("字幕元信息与当前视频不匹配，已跳过");
        if(cid!=player.optLong("cid"))throw new SubtitleUnavailable("字幕元信息与当前分P不匹配，已跳过");
        JSONObject sub=player.optJSONObject("subtitle");JSONArray tracks=sub==null?null:sub.optJSONArray("subtitles");if(tracks==null||tracks.length()==0)return null;
        JSONObject chosen=null;String selectedUrl="",rejected="";int best=-1;
        for(int i=0;i<tracks.length();i++){
            JSONObject track=tracks.getJSONObject(i);String lang=track.optString("lan");int score=lang.equals("zh-CN")?5:lang.equals("ai-zh")?4:lang.startsWith("zh")?3:lang.startsWith("en")?2:1;
            String accepted="";
            for(String key:new String[]{"subtitle_url","subtitle_url_v2"}){String raw=track.optString(key,"");if(raw.isEmpty()||raw.equals("null"))continue;try{accepted=SubtitleUrlPolicy.normalize(raw);break;}catch(IOException e){rejected=e.getMessage();}}
            if(!accepted.isEmpty()&&score>best){chosen=track;selectedUrl=accepted;best=score;}
        }
        if(chosen==null){if(!rejected.isEmpty())throw new SubtitleUnavailable(rejected);return null;}
        String subtitleId=chosen.optString("id_str",chosen.optString("id"));
        if(subtitleId.isEmpty()||subtitleId.equals("null")||subtitleId.equals("0"))throw new SubtitleUnavailable("字幕轨道缺少 ID，无法绑定来源，已跳过");
        SubtitleResource resource=subtitleDocument(selectedUrl,c);String document=resource.document;JSONObject body;
        try{body=new JSONObject(document);}catch(JSONException e){throw new SubtitleUnavailable("字幕资源不是有效 JSON，已跳过");}
        if((body.has("bvid")&&!bv.equals(body.optString("bvid")))||(body.has("cid")&&cid!=body.optLong("cid")))throw new SubtitleUnavailable("字幕正文的来源与当前视频不符，已跳过");
        JSONArray raw=body.optJSONArray("body");if(raw==null||raw.length()==0)return null;
        JSONArray lines=new JSONArray();StringBuilder text=new StringBuilder();boolean partial=false;
        for(int i=0;i<raw.length();i++){JSONObject line=raw.getJSONObject(i);String content=line.optString("content").trim();if(content.isEmpty())continue;
            double from=line.optDouble("from",-1),to=line.optDouble("to",-1);
            if(!Double.isFinite(from)||!Double.isFinite(to)||from<0||to<from)throw new SubtitleUnavailable("字幕时间轴无效，已跳过");
            String s="["+(int)from+"s] "+content+"\n";if(text.length()+s.length()>maxChars){partial=true;break;}text.append(s);lines.put(line);}
        if(text.length()<100)return null;
        return new Transcript(bound,lines,text.toString(),partial,chosen.optString("lan_doc",chosen.optString("lan")),multi,resource.host,subtitleId,selectedUrl,raw.toString());
    }
}