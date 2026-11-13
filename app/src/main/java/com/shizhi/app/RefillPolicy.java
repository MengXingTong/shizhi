package com.shizhi.app;

import java.io.*;
import java.net.*;
import org.json.JSONObject;
import org.json.JSONArray;

/** Pacing is not a quota: no number of empty batches ends a continuous session. */
public final class RefillPolicy {
    private int emptyStreak;
    public int emptyStreak(){return emptyStreak;}
    public long afterBatch(int produced){
        if(produced>0){emptyStreak=0;return 0;}
        emptyStreak=Math.min(1000,emptyStreak+1);
        return emptyStreak<5?0:Math.min(60000L,5000L<<Math.min(4,emptyStreak-5));
    }
    public static long readRetryDelay(int attempt){return Math.min(120000L,5000L<<Math.min(5,Math.max(0,attempt-1)));}
    /** Called exclusively around idempotent Bilibili reads, never around a model POST. */
    public static boolean retryableRead(Exception error){
        if(error instanceof Net.HttpFailure){int s=((Net.HttpFailure)error).status;return s==408||s>=500&&s<=599;}
        return error instanceof SocketTimeoutException||error instanceof UnknownHostException||error instanceof ConnectException
            ||error instanceof NoRouteToHostException||error instanceof SocketException||error instanceof EOFException;
    }
    public static String preferences(JSONObject cfg){
        return SourceBinding.hash(new JSONArray().put(cfg.optString("interests").trim()).put(cfg.optString("blocked").trim()).put(cfg.optInt("minScore",65)).put(cfg.optInt("inputChars",12000)).toString());
    }
}
