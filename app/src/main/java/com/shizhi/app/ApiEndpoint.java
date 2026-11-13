package com.shizhi.app;
import java.net.URI;
public final class ApiEndpoint {
    public static String normalize(String input){
        try{String s=input.trim().replaceAll("/+$","");URI u=new URI(s);
            if(!"https".equalsIgnoreCase(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null||u.getQuery()!=null||u.getFragment()!=null)throw new IllegalArgumentException();
            String suffix="/chat/completions";if(s.endsWith(suffix))s=s.substring(0,s.length()-suffix.length());
            if(s.isEmpty())throw new IllegalArgumentException();return s;
        }catch(Exception e){throw new IllegalArgumentException("请输入不含账号、查询参数和片段的 HTTPS 接口地址");}
    }
}