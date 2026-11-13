package com.shizhi.app;

/** Automatic detection is local-only; a session is verified once unless it changes or the user retries. */
public final class LoginAttemptGate {
    private String lastFingerprint="";
    private long generation;
    private boolean inFlight,closed;
    public synchronized long begin(String fingerprint,boolean manual){
        if(closed||inFlight||fingerprint==null||fingerprint.isEmpty()||(!manual&&fingerprint.equals(lastFingerprint)))return 0;
        lastFingerprint=fingerprint;inFlight=true;return ++generation;
    }
    public synchronized boolean current(long token){return !closed&&inFlight&&token==generation;}
    public synchronized boolean finish(long token){if(!current(token))return false;inFlight=false;return true;}
    public synchronized boolean inFlight(){return inFlight;}
    public synchronized void close(){closed=true;inFlight=false;generation++;}
}