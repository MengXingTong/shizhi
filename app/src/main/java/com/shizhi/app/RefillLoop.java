package com.shizhi.app;

/** Actual continuous-loop orchestration, shared by Android Engine and deterministic desktop tests. */
public final class RefillLoop {
    public interface Driver {
        boolean active();
        boolean awaitRoom()throws Exception;
        int produceBatch()throws Exception;
        void waitForMore(long delayMs,int emptyBatches)throws Exception;
    }
    public static void run(Driver driver,boolean single)throws Exception {
        RefillPolicy pace=new RefillPolicy();
        while(driver.active()){
            if(!driver.awaitRoom()||!driver.active())return;
            int produced=driver.produceBatch();
            if(single||!driver.active())return;
            long delay=pace.afterBatch(produced);
            if(delay>0)driver.waitForMore(delay,pace.emptyStreak());
        }
    }
    public interface Read<T>{T get()throws Exception;}
    public interface ReadWaiter {
        void check()throws Exception;
        void waitBeforeRetry(long delayMs,int attempt,Exception error)throws Exception;
    }
    public static <T>T read(Read<T> read,ReadWaiter waiter,boolean retry)throws Exception {
        int attempt=0;
        while(true){
            waiter.check();
            try{return read.get();}
            catch(Exception e){waiter.check();if(!retry||!RefillPolicy.retryableRead(e))throw e;waiter.waitBeforeRetry(RefillPolicy.readRetryDelay(++attempt),attempt,e);}
        }
    }
}
