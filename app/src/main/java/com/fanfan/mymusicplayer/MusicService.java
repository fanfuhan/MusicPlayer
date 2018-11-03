package com.fanfan.mymusicplayer;

import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.widget.ListView;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MusicService extends Service {

    private List<String> musics;
    private List<String> simpleMusics;
    private MyBinder binder;
    public MediaPlayer player;
    private ExecutorService executorService = Executors.newCachedThreadPool();

    public MusicService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    public void play() {
        player.reset();
        try {
            player.setDataSource(musics.get(binder.getCurMusicIndex()));
            player.prepare();
            player.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    player.start();
                }
            });
            player.seekTo(binder.getPausePosition());
            binder.setTotalMusicTime(player.getDuration());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void pause() {
        if (player.isPlaying()) {
            player.pause();
            binder.setPause(true);
            binder.setPausePosition(player.getCurrentPosition());
        }
    }

    public void next() {
        int curMusicIndex = binder.getCurMusicIndex();
        curMusicIndex++;
        if (curMusicIndex >= musics.size()) {
            binder.setCurMusicIndex(0);
            binder.setPausePosition(0);
            play();
        } else {
            binder.setCurMusicIndex(curMusicIndex);
            binder.setPausePosition(0);
            play();
        }
    }

    public void pre() {
        int curMusicIndex = binder.getCurMusicIndex();
        curMusicIndex--;
        if (curMusicIndex < 0) {
            binder.setCurMusicIndex(musics.size() - 1);
            binder.setPausePosition(0);
            play();
        } else {
            binder.setCurMusicIndex(curMusicIndex);
            binder.setPausePosition(0);
            play();
        }
    }

    public void stop() {
        if (player != null) {
            player.pause();
            player.stop();
            binder.setPause(false);
        }
        binder.setPausePosition(0);
        binder.setCurMusicIndex(0);
        binder.setPlayPosition(0);
    }

    private void init() {
        player = new MediaPlayer();
        musics = new ArrayList<>();
        simpleMusics = new ArrayList<>();
        binder = new MyBinder(this);
    }

    private void initLiseView() {
        String path = Environment.getExternalStorageDirectory().getAbsolutePath() + "/mp3/";
        File mp3Dir = new File(path);
        if (mp3Dir.isDirectory()) {
            File[] files = mp3Dir.listFiles();
            for (File f : files) {
                musics.add(f.getAbsolutePath());
                simpleMusics.add(f.getName());
            }
        }
        binder.setMusics(musics);
        binder.setSimpleMusics(simpleMusics);
    }

    @Override
    public void onDestroy() {
        if (player != null && player.isPlaying()) {
            player.stop();
            player.release();
            player = null;
        }
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        init();
        // player.reset();
        // 自动播放下一首
        player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                Intent intent = new Intent();
                intent.setAction("com.fanfan.mymusicplayer.RECEIVER");
                sendBroadcast(intent);
            }
        });
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        initLiseView();

        executorService.execute(new Runnable() {
            @Override
            public void run() {
                while(true){
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    binder.setPlayPosition(player.getCurrentPosition());
                }
            }
        });

        return binder;
    }
}
