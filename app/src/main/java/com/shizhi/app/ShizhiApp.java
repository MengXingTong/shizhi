package com.shizhi.app;
import android.app.Application;
public final class ShizhiApp extends Application {
    public String versionName="unknown"; public SecureSettings settings; public DataStore db; public Engine engine;
    @Override public void onCreate(){super.onCreate();try{versionName=getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Exception ignored){}settings=new SecureSettings(this);db=new DataStore(this);db.recover();engine=new Engine(this);}
}