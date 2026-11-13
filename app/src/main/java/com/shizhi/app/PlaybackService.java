package com.shizhi.app;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.media.MediaMetadata;
import android.media.session.*;
import android.os.*;

/** User-started foreground service. No sticky restart or alarms. */
public final class PlaybackService extends Service {
    private static PlaybackService instance;
    private static final String CHANNEL="shizhi.playback";
    private MediaSession session;private ShizhiApp app;private PowerManager.WakeLock audioWake;
    public static void ensure(Context c){c.startForegroundService(new Intent(c,PlaybackService.class));}
    public static void refresh(Context c){if(instance!=null)instance.update();}
    @Override public void onCreate(){
        super.onCreate();instance=this;app=(ShizhiApp)getApplication();
        NotificationChannel channel=new NotificationChannel(CHANNEL,"刷取与语音播报",NotificationManager.IMPORTANCE_LOW);channel.setSound(null,null);getSystemService(NotificationManager.class).createNotificationChannel(channel);
        audioWake=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"shizhi:audio");audioWake.setReferenceCounted(false);
        session=new MediaSession(this,"拾知");session.setCallback(new MediaSession.Callback(){
            @Override public void onPlay(){app.engine.playOrResume();}
            @Override public void onPause(){app.engine.pause();}
            @Override public void onSkipToNext(){app.engine.next();}
            @Override public void onSkipToPrevious(){app.engine.previous();}
            @Override public void onStop(){app.engine.stopAll();}
        });session.setActive(true);promote();
    }
    private int types(){int value=0;if(app.engine.isProducing())value|=ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;if(app.engine.isSpeaking())value|=ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK;return value==0?ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC:value;}
    private void promote(){if(Build.VERSION.SDK_INT>=29)startForeground(7,notification(),types());else startForeground(7,notification());}
    private PendingIntent action(String name,int id){return PendingIntent.getService(this,id,new Intent(this,PlaybackService.class).setAction(name),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    private Notification notification(){
        return new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_launcher).setContentTitle("拾知 · "+(app.engine.isSpeaking()?"正在播报":"自动补货中"))
            .setContentText(app.engine.notificationText()).setContentIntent(PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE))
            .setOnlyAlertOnce(true).setOngoing(true).setVisibility(Notification.VISIBILITY_PRIVATE)
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_previous,"上一个",action("previous",1)).build())
            .addAction(new Notification.Action.Builder(app.engine.isSpeaking()?android.R.drawable.ic_media_pause:android.R.drawable.ic_media_play,app.engine.isSpeaking()?"暂停":"播放",action("toggle",2)).build())
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_next,"下一个",action("next",3)).build())
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel,"全部停止",action("stop",4)).build())
            .setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken()).setShowActionsInCompactView(0,1,2)).build();
    }
    void update(){
        if(!app.engine.serviceNeeded()){stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();return;}
        if(app.engine.isSpeaking()){if(!audioWake.isHeld())audioWake.acquire(600000);}else if(audioWake.isHeld())audioWake.release();
        session.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE,app.engine.notificationText()).putString(MediaMetadata.METADATA_KEY_ARTIST,"拾知").build());
        session.setPlaybackState(new PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_PLAY_PAUSE|PlaybackState.ACTION_SKIP_TO_NEXT|PlaybackState.ACTION_SKIP_TO_PREVIOUS|PlaybackState.ACTION_STOP)
            .setState(app.engine.isSpeaking()?PlaybackState.STATE_PLAYING:PlaybackState.STATE_PAUSED,PlaybackState.PLAYBACK_POSITION_UNKNOWN,1f).build());promote();
    }
    @Override public int onStartCommand(Intent i,int flags,int id){
        if(i!=null&&i.getAction()!=null)switch(i.getAction()){case"previous":app.engine.previous();break;case"toggle":app.engine.toggle();break;case"next":app.engine.next();break;case"stop":app.engine.stopAll();break;}
        update();return START_NOT_STICKY;
    }
    @Override public void onTimeout(int id,int type){app.engine.backgroundTimedOut();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
    @Override public void onDestroy(){instance=null;if(audioWake!=null&&audioWake.isHeld())audioWake.release();if(session!=null)session.release();super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}