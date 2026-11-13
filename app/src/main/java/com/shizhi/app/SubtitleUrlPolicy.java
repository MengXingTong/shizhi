package com.shizhi.app;

import java.net.URI;
import java.util.Locale;
import java.io.IOException;

/** Only known Bilibili-controlled origins. Never attach cookies to subtitle CDN requests. */
public final class SubtitleUrlPolicy {
    public static boolean trustedHost(String raw){if(raw==null)return false;String host=raw.toLowerCase(Locale.ROOT);for(String root:new String[]{"hdslb.com","bilibili.com","bilivideo.com"})if(host.equals(root)||host.endsWith("."+root))return true;return false;}
    public static String normalize(String input)throws IOException{
        if(input==null||input.trim().isEmpty())throw new IOException("字幕轨道没有可用下载地址");
        String raw=input.trim();if(raw.startsWith("//"))raw="https:"+raw;
        try{
            URI u=new URI(raw);String scheme=u.getScheme(),host=u.getHost();
            if(scheme==null||!(scheme.equalsIgnoreCase("http")||scheme.equalsIgnoreCase("https")))throw new IOException("字幕地址协议无效（只允许 B 站 HTTPS 资源）");
            if(u.getUserInfo()!=null||u.getFragment()!=null)throw new IOException("字幕地址包含不允许的账号或片段");
            if(!trustedHost(host))throw new IOException(host==null?"字幕地址缺少有效域名":"字幕 CDN 未识别："+host.toLowerCase(Locale.ROOT)+"（已跳过，未发送登录凭据）");
            int port=u.getPort();if(port!=-1&&port!=443&&!(scheme.equalsIgnoreCase("http")&&port==80))throw new IOException("字幕 CDN 使用不允许的端口");
            String path=u.getRawPath();if(path==null||path.isEmpty()||path.equals("/"))throw new IOException("字幕轨道没有有效的资源路径");
            // Upgrade legacy http addresses. Do not re-encode signed paths/query strings.
            return "https://"+host.toLowerCase(Locale.ROOT)+path+(u.getRawQuery()==null?"":"?"+u.getRawQuery());
        }catch(java.net.URISyntaxException e){throw new IOException("字幕地址格式无效（未显示可能含签名的完整地址）");}
    }
    public static String redirect(String current,String location)throws IOException{
        if(location==null||location.trim().isEmpty())throw new IOException("字幕跳转缺少目标地址");
        try{return normalize(new URI(current).resolve(location.trim()).toString());}catch(java.net.URISyntaxException e){throw new IOException("字幕跳转地址格式无效");}
    }
}