package com.shizhi.app;

import org.json.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Persist only source identities and fingerprints, never signed subtitle URLs or cookies. */
public final class SourceBinding {
    public static final class Conflict extends IOException {Conflict(){super("不同视频返回了相同字幕资源或正文，已阻止串源入队");}}
    public static String hash(String text) {
        try { byte[] bytes=MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return out.toString(); }
        catch(Exception e){throw new IllegalStateException(e);}
    }
    public static boolean verified(JSONObject item) {
        JSONObject s=item.optJSONObject("source");
        return s!=null && s.optInt("version")==1 && item.optString("bvid").matches("BV[A-Za-z0-9]{10}")
            && item.optString("bvid").equals(s.optString("bvid")) && item.optLong("cid")>0 && item.optLong("cid")==s.optLong("cid")
            && item.optLong("aid")>0 && item.optLong("aid")==s.optLong("aid") && s.optInt("page")==1
            && item.optString("title").equals(s.optString("title")) && item.optString("author").equals(s.optString("author")) && !s.optString("subtitleId").isEmpty()
            && s.optString("resourceHash").matches("[a-f0-9]{64}") && s.optString("documentHash").matches("[a-f0-9]{64}")
            && s.optString("textHash").matches("[a-f0-9]{64}");
    }
    public static void requireVerified(JSONObject item)throws IOException {if(!verified(item))throw new IOException("来源绑定不完整，已阻止入队或播报；请重新提炼");}
    public static void rejectReusedSource(JSONObject candidate,JSONObject previous)throws IOException {
        if(!verified(candidate)||!verified(previous)||candidate.optString("bvid").equals(previous.optString("bvid")))return;
        JSONObject a=candidate.optJSONObject("source"),b=previous.optJSONObject("source");
        if(a.optString("resourceHash").equals(b.optString("resourceHash"))||a.optString("documentHash").equals(b.optString("documentHash")))
            throw new Conflict();
    }
}
