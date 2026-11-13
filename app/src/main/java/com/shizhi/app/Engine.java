package com.shizhi.app;

import android.content.*;
import android.media.*;
import android.os.*;
import android.speech.tts.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

/** One producer; UI and audio commands run on main. Never hold the monitor over I/O. */
public final class Engine {
    public interface Listener{void changed();}
    private final ShizhiApp app;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final CopyOnWriteArrayList<Listener> listeners=new CopyOnWriteArrayList<>();
    private final QueuePolicy policy;
    private volatile boolean continuousMode=true;
    private volatile String resumeBvid="";
    private volatile boolean pendingRestart,playWhenReady,scanPaused,scanning,workerActive,producing,speaking,ttsReady,userPaused,usageKnown;
    private volatile String phase="idle",message="按你的兴趣，留住有用的内容",lastSkip="",ttsStatus="正在初始化中文语音";
    private volatile int scanned,skipped,produced,page,requests,batchCandidates,eligibleCandidates,matchedCandidates,cachedSkipped;
    private volatile String playbackNotice="",activeVideo="",failedPhase="",subtitleHost="";
    private volatile long stageStartedAt=System.currentTimeMillis();
    private volatile long currentId,sessionTokens,retryDeadline;
    private volatile String retryKind="",errorAction="",runPreferences="";
    private volatile int emptyBatches,readRetryAttempt;
    private long restartVersion;
    private volatile Net.Cancel cancel;
    private final ArrayList<Long> trail=new ArrayList<>();private int trailPosition=-1;
    private TextToSpeech tts;private final AudioManager audio;private AudioFocusRequest focus;
    private ArrayList<String> chunks=new ArrayList<>();private int chunk,speechVersion;
    public Engine(ShizhiApp app){
        this.app=app;policy=new QueuePolicy(app.settings.read().optInt("pendingLimit",3));audio=(AudioManager)app.getSystemService(Context.AUDIO_SERVICE);
        if(app.db.counter("continuous_session")==1){scanPaused=true;phase="paused";message="上次刷取已中断，待播已保留；点继续刷取恢复，不会清空队列";}
        tts=new TextToSpeech(app,status->main.post(()->{
            if(status==TextToSpeech.SUCCESS){int lang=tts.setLanguage(Locale.SIMPLIFIED_CHINESE);ttsReady=lang!=TextToSpeech.LANG_MISSING_DATA&&lang!=TextToSpeech.LANG_NOT_SUPPORTED;ttsStatus=ttsReady?"中文语音已就绪":"当前语音引擎缺少中文，请在系统中安装";}
            else ttsStatus="未找到可用 TTS，请先安装支持中文的系统语音引擎";notifyChanged();
        }));
        tts.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
            public void onStart(String id){}
            public void onDone(String id){main.post(()->{if(id.equals(speechVersion+":"+chunk)&&speaking){chunk++;app.db.counter("progress:"+currentId,chunk);if(chunk<chunks.size()){speakChunk();notifyChanged();}else completed();}});}
            public void onError(String id){main.post(()->{if(id.startsWith(speechVersion+":")){pause();ttsStatus="语音播放失败，请检查系统 TTS 后重试";notifyChanged();}});}
        });
        BroadcastReceiver noisy=new BroadcastReceiver(){public void onReceive(Context c,Intent i){pause();}};
        if(Build.VERSION.SDK_INT>=33)app.registerReceiver(noisy,new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),Context.RECEIVER_NOT_EXPORTED);
        else app.registerReceiver(noisy,new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
    }
    public void addListener(Listener l){listeners.add(l);}public void removeListener(Listener l){listeners.remove(l);}
    public void notifyChanged(){main.post(()->{for(Listener l:listeners)l.changed();PlaybackService.refresh(app);});}
    public JSONObject snapshot()throws Exception{
        int previousPending=app.db.pending();app.db.prune();if(app.db.pending()<previousPending)synchronized(this){notifyAll();}
        JSONObject cfg=app.settings.publicView(),item=currentId==0?null:app.db.get(currentId);
        return new JSONObject().put("appVersion",app.versionName).put("settings",cfg).put("scanning",scanning).put("runMode",continuousMode?"continuous":"single").put("scanPaused",scanPaused).put("reviewCount",app.db.reviewCount()).put("workerActive",busy()).put("waitingForNext",playWhenReady).put("emptyBatches",emptyBatches).put("retryKind",retryKind).put("retryAttempt",readRetryAttempt).put("retryRemainingMs",Math.max(0,retryDeadline-SystemClock.elapsedRealtime())).put("errorAction",errorAction).put("preferencesPending",(scanning||scanPaused)&&!runPreferences.isEmpty()&&!runPreferences.equals(RefillPolicy.preferences(app.settings.read()))).put("speaking",speaking).put("phase",phase).put("message",message).put("lastSkip",lastSkip).put("playbackNotice",playbackNotice).put("activeVideo",activeVideo).put("failedPhase",failedPhase).put("stageStartedAt",stageStartedAt).put("subtitleHost",subtitleHost).put("batchCandidates",batchCandidates).put("eligibleCandidates",eligibleCandidates).put("matchedCandidates",matchedCandidates).put("page",page).put("cachedSkipped",cachedSkipped)
            .put("scanned",scanned).put("skipped",skipped).put("produced",produced).put("requests",requests).put("tokens",usageKnown?sessionTokens:JSONObject.NULL)
            .put("pending",app.db.pending()).put("reserved",policy.reserved()).put("pendingLimit",policy.pendingLimit()).put("current",item==null?JSONObject.NULL:item)
            .put("queue",app.db.list("queue")).put("history",app.db.list("history")).put("favorites",app.db.list("favorites"))
            .put("ttsReady",ttsReady).put("ttsStatus",ttsStatus).put("speechProgress",chunks.isEmpty()?0:(double)chunk/chunks.size()).put("native",true);
    }
    public synchronized void configure(){policy.configure(app.settings.read().optInt("pendingLimit",3));notifyAll();notifyChanged();}
    public boolean busy(){return workerActive||pendingRestart;}
    public synchronized void restartFeed(java.util.function.Consumer<Exception> callback)throws Exception {
        if(pendingRestart)throw new IllegalStateException("正在切换刷取任务，请稍候");
        if(!workerActive&&!scanning){start();callback.accept(null);return;}
        pauseScan();resumeBvid="";continuousMode=true;if(cancel!=null)cancel.stop();policy.stop();notifyAll();pendingRestart=true;final long ticket=++restartVersion;
        phase="restarting";message="正在取消旧任务，然后按最新偏好重新刷取";notifyChanged();
        worker.execute(()->main.post(()->{synchronized(Engine.this){
            if(ticket!=restartVersion||!pendingRestart){callback.accept(new Exception("已取消重新刷取，原有记录保留"));return;}
            pendingRestart=false;try{start();callback.accept(null);}catch(Exception e){phase="error";scanPaused=true;errorAction="api";message="未能开始新刷取："+e.getMessage()+"。旧待播保留";callback.accept(e);notifyChanged();}
        }}));
    }
    public void start()throws Exception{startRun("",true);}
    public void resumeScan()throws Exception{if(!scanPaused)throw new IllegalStateException("没有可继续的暂停任务");startRun(resumeBvid,false);}
    public void startVideo(String bvid)throws Exception{startRun(bvid,false);}
    private synchronized void startRun(String bvid,boolean freshFeed)throws Exception{
        if(scanning)throw new IllegalStateException("已经在刷取中，请先暂停当前任务");if(workerActive||pendingRestart)throw new IllegalStateException("正在取消上个请求，请稍后再开始");
        JSONObject cfg=app.settings.read();if(cfg.optString("apiKey").isEmpty()||cfg.optString("model").isEmpty())throw new IllegalStateException("请先在底部设置填写接口、Key 和模型名");
        if(cfg.optString("cookie").isEmpty())throw new IllegalStateException("请先在功能设置登录 B 站");
        if(cfg.optString("interests").trim().isEmpty())throw new IllegalStateException("请先在功能设置填写兴趣与学习目标");
        if(!bvid.isEmpty()&&!bvid.matches("BV[A-Za-z0-9]{10}"))throw new IllegalArgumentException("请填写有效 BVID");
        configure();if(currentId==0)userPaused=false;
        if(freshFeed||!bvid.isEmpty()){scanned=skipped=produced=page=requests=0;sessionTokens=0;usageKnown=false;}lastSkip="";playbackNotice=activeVideo=failedPhase=subtitleHost=errorAction=retryKind="";retryDeadline=0;emptyBatches=readRetryAttempt=0;runPreferences=RefillPolicy.preferences(cfg);continuousMode=bvid.isEmpty();resumeBvid=bvid;batchCandidates=eligibleCandidates=matchedCandidates=cachedSkipped=0;stageStartedAt=System.currentTimeMillis();
        final Net.Cancel token=new Net.Cancel();cancel=token;final long session=policy.start();scanning=workerActive=producing=true;phase="checking";message="正在检查 B 站登录状态";
        try{PlaybackService.ensure(app);}catch(RuntimeException e){scanning=workerActive=producing=false;policy.stop();throw new IllegalStateException("无法启动后台服务，请在前台重试");}
        // A new feed run is intentionally different from Resume and a single-video extraction.
        // All entry points and enqueue completion share this monitor; old generations cannot refill it.
        try{
            if(freshFeed){app.db.archiveQueued();app.db.resetSelectionSeen();}
            app.db.counter("continuous_session",bvid.isEmpty()?1:0);
            if(!bvid.isEmpty()&&currentId!=0){JSONObject playing=app.db.get(currentId);if(playing!=null&&bvid.equals(playing.optString("bvid"))){pause();app.db.status(currentId,"played");currentId=0;chunks.clear();chunk=0;}}
        }catch(Exception e){token.stop();policy.stop();scanning=workerActive=producing=false;throw e;}scanPaused=false;
        notifyChanged();worker.execute(()->run(cfg,bvid,session,token));
        main.post(()->{if(policy.active(session)&&!token.cancelled.get())maybePlayReady();});
    }
    private boolean awaitRoom(long session,Net.Cancel c)throws Exception{
        synchronized(this){while(scanning&&policy.active(session)&&!policy.hasRoom(app.db.pending())){producing=false;setStatus("buffered","待播已满，已暂停筛选与提炼");wait();}
            c.check();if(!scanning||!policy.active(session))return false;producing=true;return true;}
    }
    private boolean modelAllowed(long session,Net.Cancel c)throws Exception{if(!awaitRoom(session,c))return false;synchronized(this){c.check();if(!policy.active(session))return false;requests++;notifyChanged();return true;}}
    private void setStatus(String p,String msg){if(!p.equals(phase)||!msg.equals(message))stageStartedAt=System.currentTimeMillis();phase=p;message=msg;notifyChanged();}
    private void workState(String p,String msg){if(cancel!=null&&cancel.cancelled.get())return;producing=true;setStatus(p,msg);}
    private final class RunContext {
        final JSONObject cfg;final String single;final long session,baseTokens;final boolean previousUsage;
        final Net.Cancel cancel;final BiliClient bili;final LlmClient llm;final PowerManager.WakeLock wake;
        int invalidStreak;
        RunContext(JSONObject cfg,String single,long session,Net.Cancel cancel){
            this.cfg=cfg;this.single=single;this.session=session;this.cancel=cancel;baseTokens=sessionTokens;previousUsage=usageKnown;
            bili=new BiliClient(cfg.optString("cookie"));llm=new LlmClient(cfg);
            wake=((PowerManager)app.getSystemService(Context.POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"shizhi:producer");wake.setReferenceCounted(false);
        }
    }
    private void recordUsage(RunContext r){sessionTokens=r.baseTokens+r.llm.tokens();usageKnown=r.previousUsage||r.llm.usageKnown();}
    private void waitForMore(RunContext r,long delay,String kind,int empty,int attempt)throws Exception {
        if(r.wake.isHeld())r.wake.release();
        synchronized(this){
            r.cancel.check();emptyBatches=empty;readRetryAttempt=attempt;retryKind=kind;retryDeadline=SystemClock.elapsedRealtime()+delay;producing=false;
            String note=kind.equals("network")?"网络读取暂时失败，稍后自动重试；无需重新开始":"这一段推荐暂未找到合适内容，稍后自动继续翻页；无需重新开始";
            if(kind.equals("empty"))activeVideo="";
            setStatus(kind.equals("network")?"retrying":"waiting",note);
            try{while(scanning&&policy.active(r.session)){
                r.cancel.check();long remaining=retryDeadline-SystemClock.elapsedRealtime();if(remaining<=0)break;wait(remaining);
            }}finally{retryDeadline=0;retryKind="";notifyChanged();}
            r.cancel.check();
        }
    }
    private <T>T read(RunContext r,RefillLoop.Read<T> operation)throws Exception {
        final String readPhase=phase,readMessage=message;
        T result=RefillLoop.read(operation,new RefillLoop.ReadWaiter(){
            public void check()throws Exception{r.cancel.check();if(!scanning||!policy.active(r.session))throw new java.io.IOException("刷取已取消");}
            public void waitBeforeRetry(long delay,int attempt,Exception error)throws Exception{
                waitForMore(r,delay,"network",emptyBatches,attempt);check();r.wake.acquire(600000);workState(readPhase,readMessage);
            }
        },r.single.isEmpty());readRetryAttempt=0;return result;
    }
    private void run(JSONObject cfg,String single,long session,Net.Cancel c){
        RunContext r=new RunContext(cfg,single,session,c);final int before=produced;
        try{
            r.wake.acquire(120000);read(r,()->r.bili.username(c));if(r.wake.isHeld())r.wake.release();
            RefillLoop.run(new RefillLoop.Driver(){
                public boolean active(){return scanning&&policy.active(session)&&!c.cancelled.get();}
                public boolean awaitRoom()throws Exception{return Engine.this.awaitRoom(session,c);}
                public int produceBatch()throws Exception{int count=Engine.this.produceBatch(r);if(count>0)emptyBatches=0;return count;}
                public void waitForMore(long delay,int empty)throws Exception{Engine.this.waitForMore(r,delay,"empty",empty,0);}
            },!single.isEmpty());
            if(!single.isEmpty()&&!c.cancelled.get())synchronized(this){
                scanning=false;scanPaused=false;phase="completed";stageStartedAt=System.currentTimeMillis();message=produced>before?"指定视频已处理，待播已保留":"指定视频没有可用结果；可查看跳过原因";
            }
        }catch(Exception e){synchronized(this){if(!c.cancelled.get()){
            failedPhase=phase;stageStartedAt=System.currentTimeMillis();phase="error";scanPaused=true;
            String why=e.getMessage()==null?"处理失败":e.getMessage();boolean model=failedPhase.equals("ranking")||failedPhase.equals("summarizing")||failedPhase.equals("validating");
            errorAction=why.contains("登录")?"login":model?"api":e instanceof Net.HttpFailure&&(((Net.HttpFailure)e).status==412||((Net.HttpFailure)e).status==429)?"risk":"retry";
            message=why+(single.isEmpty()?"。已有待播保留；处理后可继续补货，不必清空队列":"。原记录和其他待播保留，处理后可重试这条");policy.stop();
        }scanning=false;}}
        finally{recordUsage(r);if(r.wake.isHeld())r.wake.release();policy.release(session);workerActive=producing=false;retryDeadline=0;retryKind="";notifyChanged();}
    }
    private int produceBatch(RunContext r)throws Exception {
        JSONObject cfg=r.cfg;String single=r.single;long session=r.session;Net.Cancel c=r.cancel;
        BiliClient bili=r.bili;LlmClient llm=r.llm;PowerManager.WakeLock wake=r.wake;
        if(!awaitRoom(session,c))return 0;wake.acquire(600000);page++;activeVideo="";batchCandidates=eligibleCandidates=matchedCandidates=0;workState("discovering",single.isEmpty()?"查找第 "+page+" 批 B 站推荐":"读取指定视频");
        JSONArray raw=read(r,()->single.isEmpty()?bili.feed(page,c):new JSONArray().put(bili.video(single,c))),candidates=new JSONArray();
        batchCandidates=raw.length();String[] blocks=cfg.optString("blocked").toLowerCase(Locale.ROOT).split("[,，、\\n]");
        for(int i=0;i<raw.length();i++){JSONObject v=raw.getJSONObject(i);String bv=v.getString("bvid");if(app.db.wasSeen(bv)&&single.isEmpty()){cachedSkipped++;continue;}scanned++;
            boolean blocked=false;String text=(v.optString("title")+" "+v.optString("author")).toLowerCase(Locale.ROOT);
            for(String word:blocks)if(!word.trim().isEmpty()&&text.contains(word.trim())){blocked=true;break;}
            if(single.isEmpty()&&(blocked||v.optInt("duration")<45||v.optInt("duration")>5400)){skip(bv,"规则过滤（关键词或时长）");continue;}candidates.put(v);
        }
        eligibleCandidates=candidates.length();if(candidates.length()==0&&raw.length()>0)lastSkip="本批候选均已处理或被规则过滤，未调用模型";List<JSONObject> selected=new ArrayList<>();
        if(candidates.length()>0){
            if(single.isEmpty()){
                if(wake.isHeld())wake.release();if(!modelAllowed(session,c))return 0;wake.acquire(600000);workState("ranking","按你的兴趣筛选候选，不根据标题生成摘要");selected=llm.rank(candidates,c);c.check();
                Set<String> chosen=new HashSet<>();for(JSONObject v:selected)chosen.add(v.getString("bvid"));for(int i=0;i<candidates.length();i++){String bv=candidates.getJSONObject(i).getString("bvid");if(!chosen.contains(bv))skip(bv,"兴趣匹配度不足");}
            }else{JSONObject v=candidates.getJSONObject(0);v.put("score",100).put("reason","你指定的视频");selected.add(v);}
        }
        matchedCandidates=selected.size();notifyChanged();int before=produced;
        for(JSONObject video:selected){
            if(wake.isHeld())wake.release();if(!awaitRoom(session,c))break;wake.acquire(600000);String bv=video.getString("bvid");activeVideo=video.optString("title");subtitleHost="";workState("subtitles","读取字幕："+video.getString("title"));
            BiliClient.Transcript transcript;
            final JSONObject selectedVideo=video;try{transcript=read(r,()->bili.subtitles(selectedVideo,cfg.optInt("inputChars",12000),c));}catch(BiliClient.SubtitleUnavailable e){c.check();skip(bv,e.getMessage());workState("skipping","此视频字幕不可用，继续检查下一条");continue;}
            c.check();if(transcript==null){skip(bv,"无可用字幕，未调用摘要模型");workState("skipping","未找到可读字幕，继续检查下一条");continue;}
            if(!bv.equals(transcript.video().getString("bvid")))throw new BiliClient.SubtitleUnavailable("字幕来源 BVID 不一致，已停止");
            video=transcript.video();transcript.requireMatches(video);subtitleHost=transcript.sourceHost;
            try{app.db.validateSource(new JSONObject(video.toString()).put("source",transcript.source()));}
            catch(SourceBinding.Conflict e){skip(bv,e.getMessage());workState("skipping","检测到跨视频字幕复用，未调用摘要模型");continue;}
            if(wake.isHeld())wake.release();if(!awaitRoom(session,c))break;
            synchronized(this){c.check();if(!policy.reserve(session,app.db.pending()))continue;requests++;}
            JSONObject summary=null;
            try{wake.acquire(600000);workState("summarizing","提炼内容并核对字幕证据");summary=llm.summarize(video,transcript,c,()->workState("validating","模型已返回，正在核对摘要与字幕引用"));c.check();r.invalidStreak=0;
                synchronized(this){c.check();if(summary!=null&&policy.complete(session,app.db.pending())){transcript.requireMatches(summary);if(single.isEmpty())app.db.insert(summary);else app.db.replaceSummary(summary);produced++;app.db.seen(bv,"已提炼");workState("queued","已提炼并加入待播："+video.optString("title"));}else if(summary==null){policy.release(session);skip(bv,"字幕与兴趣不匹配，或知识信息不足");}}
            }catch(LlmClient.InvalidSummary|org.json.JSONException e){c.check();skip(bv,e instanceof LlmClient.InvalidSummary?e.getMessage():"模型 JSON 格式不正确，不自动重试");if(++r.invalidStreak>=2)throw new Exception("摘要连续未通过校验，为避免反复计费已暂停自动补货；请检查模型兼容性。最后原因："+lastSkip);}
            finally{policy.release(session);recordUsage(r);if(wake.isHeld())wake.release();notifyChanged();}
            if(summary!=null)main.post(()->{if(policy.active(r.session)&&!r.cancel.cancelled.get())maybePlayReady();});
        }
        if(wake.isHeld())wake.release();recordUsage(r);
        return produced-before;
    }
    private void skip(String bv,String why){skipped++;lastSkip=why;app.db.seen(bv,why);}
    public synchronized void pauseScan(){
        pendingRestart=false;restartVersion++;if(!scanning){notifyChanged();return;}scanning=false;scanPaused=true;policy.stop();if(cancel!=null)cancel.stop();
        phase="paused";stageStartedAt=System.currentTimeMillis();message="已暂停刷取，待播保留；继续刷取保留队列，重新刷视频会清空旧待播";notifyAll();notifyChanged();
    }
    public synchronized void stop(){app.db.counter("continuous_session",0);pendingRestart=false;restartVersion++;scanPaused=false;scanning=false;policy.stop();phase="stopped";stageStartedAt=System.currentTimeMillis();message="已停止刷取，已有内容保留";if(cancel!=null)cancel.stop();notifyAll();notifyChanged();}
    public void stopAll(){stop();pause();}
    public synchronized void backgroundTimedOut(){
        pauseScan();pause();phase="error";scanPaused=true;errorAction="system";failedPhase="background";
        message="系统暂停了长时间后台任务。回到应用后可继续补货，已有待播和收藏保留";notifyChanged();
    }
    public synchronized void wake(){if(retryKind.equals("empty")&&app.db.pending()==0)retryDeadline=0;notifyAll();notifyChanged();}
    public boolean serviceNeeded(){return scanning||pendingRestart||producing||speaking;}public boolean isProducing(){return producing;}
    public boolean isSpeaking(){return speaking;}public boolean isScanning(){return scanning;}
    public String notificationText(){try{if(speaking&&currentId!=0){JSONObject j=app.db.get(currentId);if(j!=null)return j.optString("title");}}catch(Exception ignored){}return message;}
    public void toggle(){if(speaking)pause();else playOrResume();}
    public void playOrResume(){if(speaking)return;if(currentId!=0)resume();else{long id=app.db.firstQueued();if(id!=0)play(id);else{
        playWhenReady=scanning;userPaused=false;playbackNotice=scanning?"正在等待下一条，准备好后会自动播放，不会重播旧内容":"待播为空，请先刷视频；重听旧内容可到历史或收藏";notifyChanged();
    }}}
    public void play(long id){playInternal(id,true);}
    private void playInternal(long id,boolean record){
        try{JSONObject item=app.db.get(id);if(item==null){playbackNotice="内容已过期或被清理";notifyChanged();return;}
            if(!SourceBinding.verified(item)){playbackNotice="旧内容尚未校验字幕来源，已阻止播报。请在历史/收藏中查看原视频或点重新提炼。";notifyChanged();return;}
            if(!ttsReady){playbackNotice=ttsStatus;notifyChanged();return;}
            playWhenReady=false;playbackNotice="";haltTts();if(currentId!=0&&currentId!=id)app.db.status(currentId,"played");currentId=id;app.db.status(id,"active");
            if(record){while(trail.size()>trailPosition+1)trail.remove(trail.size()-1);if(trail.isEmpty()||trail.get(trail.size()-1)!=id)trail.add(id);trailPosition=trail.size()-1;}
            chunks=SpeechChunks.split(item.getString("speech"));chunk=(int)app.db.counter("progress:"+id);if(chunk>=chunks.size())chunk=0;userPaused=false;resume();wake();
        }catch(Exception e){playbackNotice="播放失败："+e.getMessage();notifyChanged();}
    }
    public void pause(){playWhenReady=false;userPaused=true;haltTts();notifyChanged();}
    private void haltTts(){speaking=false;speechVersion++;tts.stop();if(focus!=null){audio.abandonAudioFocusRequest(focus);focus=null;}}
    private void resume(){
        if(!ttsReady||currentId==0||chunks.isEmpty())return;
        focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setOnAudioFocusChangeListener(change->{if(change!=AudioManager.AUDIOFOCUS_GAIN)main.post(this::pause);},main).build();
        if(audio.requestAudioFocus(focus)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED){playbackNotice="无法取得音频焦点，请稍后重试";notifyChanged();return;}
        try{PlaybackService.ensure(app);}catch(RuntimeException e){playbackNotice="无法启动音频服务，请回到应用再播放";audio.abandonAudioFocusRequest(focus);focus=null;notifyChanged();return;}
        speaking=true;userPaused=false;speechVersion++;tts.setSpeechRate((float)app.settings.read().optDouble("speechRate",1.0));if(chunk>=chunks.size())chunk=0;speakChunk();notifyChanged();
    }
    private void speakChunk(){if(chunk<chunks.size()&&tts.speak(chunks.get(chunk),TextToSpeech.QUEUE_FLUSH,null,speechVersion+":"+chunk)==TextToSpeech.ERROR){pause();playbackNotice="语音引擎无法播报，请在功能设置中测试";notifyChanged();}}
    private void completed(){long done=currentId;haltTts();if(done!=0){app.db.status(done,"played");app.db.counter("progress:"+done,0);}currentId=0;chunks.clear();chunk=0;notifyChanged();if(app.settings.read().optBoolean("autoPlay",true)&&!userPaused){playWhenReady=true;maybePlayReady();}wake();}
    private void maybePlayReady(){if(currentId==0&&(playWhenReady||app.settings.read().optBoolean("autoPlay",true)&&!userPaused))playNextQueued();}
    private void playNextQueued(){long id=app.db.firstQueued();if(id!=0)play(id);}
    public void next(){if(trailPosition+1<trail.size()){trailPosition++;playInternal(trail.get(trailPosition),false);return;}long id=app.db.firstQueued();if(id!=0){play(id);return;}
        haltTts();if(currentId!=0)app.db.status(currentId,"played");currentId=0;chunks.clear();chunk=0;userPaused=false;playWhenReady=scanning;playbackNotice=scanning?"暂无下一条，正在查找与提炼；进度见上方运行状态。":"刷取当前未运行。请查看上方原因；继续补货会保留队列，重新刷视频会清空旧待播。";wake();}
    public void previous(){if(trailPosition>0){trailPosition--;playInternal(trail.get(trailPosition),false);}else if(currentId!=0){haltTts();chunk=0;app.db.counter("progress:"+currentId,0);resume();}else{long id=app.db.latestPlayed();if(id!=0)play(id);}}
    public void testSpeech(){if(currentId!=0)pause();else haltTts();if(ttsReady)tts.speak("你好，我是拾知。我会替你筛选视频，把有用的知识讲给你听。",TextToSpeech.QUEUE_FLUSH,null,"test");notifyChanged();}
}