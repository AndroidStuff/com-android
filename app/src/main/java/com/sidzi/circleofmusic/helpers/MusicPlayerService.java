package com.sidzi.circleofmusic.helpers;


import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;

import com.j256.ormlite.android.apptools.OpenHelperManager;
import com.j256.ormlite.dao.Dao;
import com.sidzi.circleofmusic.entities.Track;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

public class MusicPlayerService extends Service {

    final public static String ACTION_UPDATE_METADATA = "com.sidzi.circleofmusic.UPDATE_METADATA";
    final public static String ACTION_PAUSE = "com.sidzi.circleofmusic.PAUSE";
    final public static String ACTION_PLAY = "com.sidzi.circleofmusic.PLAY";
    public static Track PLAYING_TRACK = null;
    private final IBinder mMIBinder = new MusicBinder();
    MediaPlayer mMediaPlayer = null;
    private int PLAYING_TRACK_POSITION = -1;
    private LocalBroadcastManager localBroadcastManager;
    private List<Track> mTrackList;
    private Context mContext;


    public MusicPlayerService() {
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mMediaPlayer = new MediaPlayer();
        mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        mMediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mediaPlayer) {
                mediaPlayer.start();
            }
        });
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mContext = this;
        localBroadcastManager = LocalBroadcastManager.getInstance(this);
        final OrmHandler ormHandler = OpenHelperManager.getHelper(this, OrmHandler.class);
        Dao<Track, String> dbTrack;
        try {
            dbTrack = ormHandler.getDao(Track.class);
            mTrackList = dbTrack.queryForAll();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void play(String track_path) {
        if (mMediaPlayer.isPlaying()) {
            mMediaPlayer.stop();
        }
        mMediaPlayer.reset();
        try {
            mMediaPlayer.setDataSource(track_path);
            if (track_path.startsWith("https://")) {
                mMediaPlayer.prepareAsync();
            } else {
                mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                    @Override
                    public void onCompletion(MediaPlayer mediaPlayer) {
                        next();
                    }
                });
                mMediaPlayer.prepare();
            }
            PLAYING_TRACK_POSITION = mTrackList.indexOf(new Track(track_path));
            PLAYING_TRACK = mTrackList.get(PLAYING_TRACK_POSITION);
            uiUpdate(PLAYING_TRACK);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void next() {
        play(mTrackList.get(++PLAYING_TRACK_POSITION).getPath());
    }

    public void pause() {
        mMediaPlayer.pause();
        uiUpdate(null);
    }

    public void unpause() {
        mMediaPlayer.start();
        uiUpdate(null);
    }

    public boolean bucketOperation() {
        Utils.bucketOps(PLAYING_TRACK.getPath(), !PLAYING_TRACK.getBucket(), mContext);
        PLAYING_TRACK.setBucket(!PLAYING_TRACK.getBucket());
        return PLAYING_TRACK.getBucket();
    }

    private void uiUpdate(Track track) {
        String action = (track == null) ? (mMediaPlayer.isPlaying() ? ACTION_PLAY : ACTION_PAUSE) : ACTION_UPDATE_METADATA;
        Intent intent = new Intent(action);
        intent.putExtra("track_metadata", track);
        localBroadcastManager.sendBroadcast(intent);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mMIBinder;
    }

    public class MusicBinder extends Binder {
        MusicPlayerService getService() {
            return MusicPlayerService.this;
        }
    }
}

