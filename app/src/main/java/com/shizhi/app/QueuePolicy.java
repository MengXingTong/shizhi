package com.shizhi.app;

/** Capacity only. There is deliberately no per-session request quota. */
public final class QueuePolicy {
    public static final int DEFAULT_PENDING=3, MAX_PENDING=10;
    private int limit,reserved;
    private long generation;
    private boolean active;
    public QueuePolicy(int limit){configure(limit);}
    public synchronized void configure(int n){if(n<1||n>MAX_PENDING)throw new IllegalArgumentException("待播上限需为 1–10 条");limit=n;}
    public synchronized long start(){generation++;active=true;reserved=0;return generation;}
    public synchronized void stop(){active=false;generation++;reserved=0;}
    public synchronized boolean active(long session){return active&&generation==session;}
    public synchronized boolean hasRoom(int pending){return pending+reserved<limit;}
    public synchronized boolean reserve(long session,int pending){if(!active(session)||!hasRoom(pending))return false;reserved++;return true;}
    public synchronized boolean complete(long session,int pending){if(!active(session)||reserved==0)return false;reserved--;return pending<limit;}
    public synchronized void release(long session){if(active(session)&&reserved>0)reserved--;}
    public synchronized int reserved(){return reserved;}
    public synchronized int pendingLimit(){return limit;}
}