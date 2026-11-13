package com.shizhi.app;
public final class RetentionPolicy {
    public static final long SEVEN_DAYS=7L*24*60*60*1000;
    public static boolean expires(long created,long now,boolean favorite,boolean active){return !favorite&&!active&&created<now-SEVEN_DAYS;}
}