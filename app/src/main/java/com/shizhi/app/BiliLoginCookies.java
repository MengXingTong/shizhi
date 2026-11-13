package com.shizhi.app;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

/** Cookie strings are only kept in memory and the encrypted credential store. */
public final class BiliLoginCookies {
    private static LinkedHashMap<String,String> parse(String... values){
        LinkedHashMap<String,String> result=new LinkedHashMap<>();
        for(String value:values){if(value==null)continue;for(String part:value.split(";")){
            int eq=part.indexOf('=');if(eq<1)continue;String name=part.substring(0,eq).trim(),v=part.substring(eq+1).trim();
            if(!name.matches("[A-Za-z0-9_!#$%&'*+.^`|~-]+")||v.isEmpty()||v.indexOf('\r')>=0||v.indexOf('\n')>=0)continue;
            if(v.equalsIgnoreCase("deleted")||v.equalsIgnoreCase("null")||v.equals("\"\""))continue;
            if(!result.containsKey(name))result.put(name,v);
        }}return result;
    }
    /** Earlier hosts take precedence; preserve encoded values and embedded '='. */
    public static String merge(String... cookies){StringBuilder out=new StringBuilder();for(Map.Entry<String,String> e:parse(cookies).entrySet()){if(out.length()>0)out.append("; ");out.append(e.getKey()).append('=').append(e.getValue());}return out.toString();}
    public static String fingerprint(String cookies){
        Map<String,String> values=parse(cookies);String session=values.get("SESSDATA");if(session==null)return "";
        try{byte[] digest=MessageDigest.getInstance("SHA-256").digest((session+"\n"+values.getOrDefault("DedeUserID","")).getBytes(StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(byte b:digest)out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return out.toString();}
        catch(Exception e){throw new IllegalStateException("无法计算登录态标识",e);}
    }
}