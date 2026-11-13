package com.shizhi.app;

import java.net.URI;

/** Source browsing is deliberately independent of login detection and app deeplinks. */
public final class SourceNavigation {
    public static String url(String bvid,int seconds){
        if(bvid==null||!bvid.matches("BV[A-Za-z0-9]{10}"))throw new IllegalArgumentException("无效视频 ID");
        return "https://www.bilibili.com/video/"+bvid+"?p=1&t="+Math.max(0,Math.min(86400,seconds));
    }
    public static boolean allow(String raw){
        try{URI u=new URI(raw);String h=u.getHost();return "https".equalsIgnoreCase(u.getScheme())&&h!=null&&u.getUserInfo()==null&&(u.getPort()==-1||u.getPort()==443)
            &&(h.equalsIgnoreCase("bilibili.com")||h.toLowerCase(java.util.Locale.ROOT).endsWith(".bilibili.com"));}catch(Exception e){return false;}
    }
}
