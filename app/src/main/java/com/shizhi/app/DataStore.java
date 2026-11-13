package com.shizhi.app;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONObject;

public final class DataStore extends SQLiteOpenHelper {
    public static final long RETENTION = RetentionPolicy.SEVEN_DAYS;
    public DataStore(Context c) { super(c, "shizhi.db", null, 1); setWriteAheadLoggingEnabled(true); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE items (id INTEGER PRIMARY KEY AUTOINCREMENT,bvid TEXT UNIQUE NOT NULL,created INTEGER NOT NULL,last_played INTEGER NOT NULL DEFAULT 0,favorite INTEGER NOT NULL DEFAULT 0,status TEXT NOT NULL DEFAULT 'queued',payload TEXT NOT NULL)");
        db.execSQL("CREATE INDEX items_retention ON items(created,favorite,status)");
        db.execSQL("CREATE TABLE seen (bvid TEXT PRIMARY KEY,created INTEGER NOT NULL,reason TEXT NOT NULL)");
        db.execSQL("CREATE TABLE counters (name TEXT PRIMARY KEY,value INTEGER NOT NULL)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int old,int newer) { throw new IllegalStateException("不支持的数据库升级"); }
    public synchronized void recover() {
        getWritableDatabase().execSQL("UPDATE items SET status='queued' WHERE status='active'");
        // Keep old summaries/history/favorites, but never autoplay an unbound legacy transcript.
        ArrayList<Long> invalid=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,payload FROM items WHERE status='queued'",null)){
            while(c.moveToNext()) {boolean valid=false;try{valid=SourceBinding.verified(new JSONObject(c.getString(1)));}catch(Exception ignored){}
                if(!valid)invalid.add(c.getLong(0));
            }
        }
        for(long id:invalid)getWritableDatabase().execSQL("UPDATE items SET status='review' WHERE id=?",new Object[]{id});
        prune();
    }
    public synchronized int archiveQueued() {
        int count=pending();getWritableDatabase().execSQL("UPDATE items SET status='archived' WHERE status='queued'");return count;
    }
    public synchronized void resetSelectionSeen(){getWritableDatabase().execSQL("DELETE FROM seen WHERE reason!='已提炼'");}
    public synchronized int reviewCount(){try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM items WHERE status='review'",null)){c.moveToFirst();return c.getInt(0);}}

    public synchronized void prune() {
        long cutoff = System.currentTimeMillis()-RETENTION;
        getWritableDatabase().execSQL("DELETE FROM items WHERE created < ? AND favorite=0 AND status!='active'",new Object[]{cutoff});
        getWritableDatabase().execSQL("DELETE FROM seen WHERE created < ?",new Object[]{cutoff});
        getWritableDatabase().execSQL("DELETE FROM counters WHERE name LIKE 'progress:%' AND substr(name,10) NOT IN (SELECT CAST(id AS TEXT) FROM items)");
    }
    public synchronized void seen(String bv,String reason) { getWritableDatabase().execSQL("INSERT OR REPLACE INTO seen VALUES (?,?,?)",new Object[]{bv,System.currentTimeMillis(),reason}); }
    public synchronized boolean wasSeen(String bv) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM seen WHERE bvid=? UNION SELECT 1 FROM items WHERE bvid=?",new String[]{bv,bv})) { return c.moveToFirst(); }
    }
    public synchronized long insert(JSONObject item) throws Exception {
        validateSource(item);
        android.content.ContentValues v = new android.content.ContentValues(); v.put("bvid",item.getString("bvid")); v.put("created",System.currentTimeMillis()); v.put("payload",item.toString());
        return getWritableDatabase().insertOrThrow("items",null,v);
    }
    public synchronized void validateSource(JSONObject item)throws Exception {
        SourceBinding.requireVerified(item);
        try(Cursor c=getReadableDatabase().rawQuery("SELECT payload FROM items WHERE bvid!=?",new String[]{item.getString("bvid")})) {
            while(c.moveToNext())SourceBinding.rejectReusedSource(item,new JSONObject(c.getString(0)));
        }
    }
    /** Only called for an explicit single-video extraction. Preserve the row ID and favorite. */
    public synchronized long replaceSummary(JSONObject item)throws Exception {
        validateSource(item);long id=findVideo(item.getString("bvid"));if(id==0)return insert(item);
        android.content.ContentValues v=new android.content.ContentValues();v.put("payload",item.toString());v.put("status","queued");v.put("created",System.currentTimeMillis());
        getWritableDatabase().update("items",v,"id=?",new String[]{""+id});counter("progress:"+id,0);return id;
    }
    public synchronized int pending() { try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM items WHERE status='queued'",null)){c.moveToFirst(); return c.getInt(0);} }
    public synchronized long firstQueued() { try(Cursor c=getReadableDatabase().rawQuery("SELECT id FROM items WHERE status='queued' ORDER BY id LIMIT 1",null)){return c.moveToFirst()?c.getLong(0):0;} }
    private JSONObject item(Cursor c) throws Exception {
        return new JSONObject(c.getString(c.getColumnIndexOrThrow("payload"))).put("id",c.getLong(c.getColumnIndexOrThrow("id")))
            .put("sourceVerified",SourceBinding.verified(new JSONObject(c.getString(c.getColumnIndexOrThrow("payload"))))).put("created",c.getLong(c.getColumnIndexOrThrow("created"))).put("lastPlayed",c.getLong(c.getColumnIndexOrThrow("last_played")))
            .put("favorite",c.getInt(c.getColumnIndexOrThrow("favorite"))==1).put("status",c.getString(c.getColumnIndexOrThrow("status")));
    }
    public synchronized JSONObject get(long id) throws Exception { try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM items WHERE id=?",new String[]{""+id})){return c.moveToFirst()?item(c):null;} }
    public synchronized JSONArray list(String kind) throws Exception { return list(kind,0,50); }
    public synchronized JSONArray list(String kind,int offset,int limit) throws Exception {
        prune(); String where = "queue".equals(kind)?"status='queued'":"favorites".equals(kind)?"favorite=1":"1=1";
        String order = "queue".equals(kind)?"id ASC":"created DESC";
        JSONArray result=new JSONArray(); try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM items WHERE "+where+" ORDER BY "+order+" LIMIT "+Math.min(100,Math.max(1,limit))+" OFFSET "+Math.max(0,offset),null)){while(c.moveToNext())result.put(item(c));}return result;
    }
    public synchronized void favorite(long id) { getWritableDatabase().execSQL("UPDATE items SET favorite=1-favorite WHERE id=?",new Object[]{id}); }
    public synchronized void status(long id,String s) { getWritableDatabase().execSQL("UPDATE items SET status=?,last_played=CASE WHEN ?='active' THEN ? ELSE last_played END WHERE id=?",new Object[]{s,s,System.currentTimeMillis(),id}); }
    public synchronized long findVideo(String bv){try(Cursor c=getReadableDatabase().rawQuery("SELECT id FROM items WHERE bvid=?",new String[]{bv})){return c.moveToFirst()?c.getLong(0):0;}}
    public synchronized long latestPlayed() { try(Cursor c=getReadableDatabase().rawQuery("SELECT id FROM items WHERE status='played' ORDER BY last_played DESC LIMIT 1",null)){return c.moveToFirst()?c.getLong(0):0;} }
    public synchronized long counter(String name) { try(Cursor c=getReadableDatabase().rawQuery("SELECT value FROM counters WHERE name=?",new String[]{name})){return c.moveToFirst()?c.getLong(0):0;} }
    public synchronized void counter(String name,long value) { getWritableDatabase().execSQL("INSERT OR REPLACE INTO counters VALUES (?,?)",new Object[]{name,value}); }
}