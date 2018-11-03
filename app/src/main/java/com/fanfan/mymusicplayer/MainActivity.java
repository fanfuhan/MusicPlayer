package com.fanfan.mymusicplayer;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private List<String> musics;
    private List<String> simpleMusics;
    private ArrayAdapter<String> adapter;
    private ListView musicLv;
    private Button btnPlayOrPause;
    private Button btnStop;
    private Button btnPre;
    private Button btnNext;
    private SeekBar sb;
    private TextView textCur;
    private TextView textTotal;
    private MyBinder binder;
    private MusicService musicService;
    private Handler handler;
    private ExecutorService executorService;
    private BroadcastReceiver receiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 请求权限
        requestPerm();
        // 初始化变量
        initVarible();
        // 初始化控件
        initView();
        // 开启并绑定服务
        startBindService();
        // 配置监听
        setListener();

        // handler处理播放时间
        handler = new Handler(){
            @Override
            public void handleMessage(Message msg) {
                String playTime = msg.getData().getString("playTime");
                String totalTime = msg.getData().getString("totalTime");
                textCur.setText(playTime);
                textTotal.setText(totalTime);
            }
        };
    }

    private void initVarible() {
        executorService = Executors.newCachedThreadPool();

        //动态注册广播接收器
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("com.fanfan.mymusicplayer.RECEIVER");
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                musicLv.getChildAt(binder.getCurMusicIndex()).setBackgroundColor(Color.WHITE);
                musicService.next();
                musicLv.getChildAt(binder.getCurMusicIndex()).setBackgroundColor(Color.GREEN);
            }
        };
        registerReceiver(receiver, intentFilter);
    }

    private void setListener() {
        // 按钮监听
        InnerOnClickListerer listener = new InnerOnClickListerer();
        btnPlayOrPause.setOnClickListener(listener);
        btnStop.setOnClickListener(listener);
        btnPre.setOnClickListener(listener);
        btnNext.setOnClickListener(listener);

        // seekbar拖动设置
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    binder.setPausePosition(seekBar.getProgress());
                    musicService.player.seekTo(binder.getPausePosition());
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        // ListView 点击监听
        InnerItemOnCLickListener listener2 = new InnerItemOnCLickListener();
        musicLv.setOnItemClickListener(listener2);
    }


    private class InnerOnClickListerer implements View.OnClickListener {

        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.btn_play_pause:
                    musicLv.getChildAt(binder.getCurMusicIndex()).setBackgroundColor(Color.GREEN);
                    if (musicService.player.isPlaying()) {
                        musicService.pause();
                        btnPlayOrPause.setText("播放");
                    } else {
                        musicService.play();
                        sb.setProgress(binder.getPausePosition());
                        sb.setMax(binder.getTotalMusicTime());
                        btnPlayOrPause.setText("暂停");
                    }
                    break;
                case R.id.btn_next:
                    musicLv.getChildAt(binder.getCurMusicIndex()).setBackgroundColor(Color.WHITE);
                    musicService.next();
                    musicLv.getChildAt(binder.getCurMusicIndex()).setBackgroundColor(Color.GREEN);
                    sb.setMax(binder.getTotalMusicTime());
                    btnPlayOrPause.setText("暂停");
                    break;
                case R.id.btn_pre:
                    musicLv.getChildAt(binder.getCurMusicIndex()).setBackgroundColor(Color.WHITE);
                    musicService.pre();
                    musicLv.getChildAt(binder.getCurMusicIndex()).setBackgroundColor(Color.GREEN);
                    sb.setMax(binder.getTotalMusicTime());
                    btnPlayOrPause.setText("暂停");
                    break;
                case R.id.btn_stop:
                    musicLv.getChildAt(binder.getCurMusicIndex()).setBackgroundColor(Color.WHITE);
                    musicService.stop();
                    btnPlayOrPause.setText("播放");
                    sb.setProgress(0);
                    textCur.setText("00:00");
                    textTotal.setText("00:00");
                    break;

            }
        }
    }

    private class InnerItemOnCLickListener implements AdapterView.OnItemClickListener {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            binder.setCurMusicIndex(position);
            binder.setPausePosition(0);
        }
    }

    public void startBindService() {
        Intent intent = new Intent();
        intent.setClass(this, MusicService.class);
        startService(intent);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            binder = (MyBinder) service;
            musicService = binder.getMusicService();
            // 列出列表
            initListView();

            executorService.execute(new Runnable() {
                @Override
                public void run() {
                    while(true){
                        try {
                            Thread.sleep(200);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        // 设置seekbar
                        sb.setMax(binder.getTotalMusicTime());
                        sb.setProgress(binder.getPlayPosition());
                        // 设置时间显示
                        String playTime = getTime(binder.getPlayPosition());
                        String totalTime = getTime(binder.getTotalMusicTime());
                        Message msg = Message.obtain();
                        msg.getData().putString("playTime", playTime);
                        msg.getData().putString("totalTime", totalTime);
                        handler.sendMessage(msg);
                    }
                }
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            musicService = null;
        }
    };

    private void initListView() {
        musics = binder.getMusics();
        simpleMusics = binder.getSimpleMusics();
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, simpleMusics);
        musicLv.setAdapter(adapter);
    }

    private void initView() {
        musicLv = findViewById(R.id.musicLv);
        sb = findViewById(R.id.sb);
        btnPlayOrPause = findViewById(R.id.btn_play_pause);
        btnStop = findViewById(R.id.btn_stop);
        btnPre = findViewById(R.id.btn_pre);
        btnNext = findViewById(R.id.btn_next);
        textCur = findViewById(R.id.text_current);
        textTotal = findViewById(R.id.text_total);

        // 初始化滚动条及时间显示
        sb.setProgress(0);
        textCur.setText("00:00");
        textTotal.setText("00:00");
    }

    public void requestPerm() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        musicService.player.stop();
        musicService.player.release();
        unbindService(serviceConnection);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            moveTaskToBack(true);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    // 格式化时间
    private String getTime(int time) {
        SimpleDateFormat sdf = new SimpleDateFormat("mm:ss");
        return sdf.format(time);
    }

}
