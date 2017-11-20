/* ===============================================================================
 * Copyright (c) 2016 Pikkart S.r.l. All Rights Reserved.
 * Pikkart is a trademark of Pikkart S.r.l., registered in Europe,
 * the United States and other countries.
 *
 * This file is part of Pikkart AR SDK Tutorial series, a series of tutorials
 * explaining how to use and fully exploits Pikkart's AR SDK.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ===============================================================================*/
package pikkart.com.pikkarttutorial_10_17;

import android.app.Activity;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.SurfaceHolder;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.MediaController;
import android.widget.VideoView;

import java.io.IOException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * \class FullscreenVideoPlayer
 * \brief A fullscreen video player activity
 *
 * The AR Video player class.
 * It encapsulates a MediaPlayer and a SurfaceTexture on which the video data is stored for
 * later use by out OpenGL renderer. The class also manages the video state.
 */
public class FullscreenVideoPlayer extends Activity implements MediaPlayer.OnPreparedListener,
        MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener, MediaPlayer.OnBufferingUpdateListener,
        SurfaceHolder.Callback
{
    private FrameLayout mRootLayout = null; /**< the root of the activity layout */
    private VideoView mVideoView = null; /**< the video view widget */
    private SurfaceHolder mHolder = null; /**< a surface holder pointer */
    private MediaPlayer mMediaPlayer = null; /**< the video mediaplayer */
    private MediaController mMediaController = null; /**< the video mediacontroller */

    private String mMovieUrl = ""; /**< the movie file (URL or file path)*/
    private int mSeekPosition = 0; /**< where to start video playback*/
    private int mRequestedOrientation = 0; /**< video starting orientation */
    private boolean mAutostart = false; /**< where to start video playback*/
    private int mCurrentBufferingPercent = 0; /**< buffering percentage*/

    private ReentrantLock mMediaPlayerLock = null; /**< media player mutex */
    private ReentrantLock mMediaControllerLock = null; /**< surfacetexture mutex*/


    /**
     * \brief The activity onCreate function
     * @param savedInstanceState
     */
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        //programmatically create the layout
        mRootLayout = new FrameLayout(this);
        FrameLayout.LayoutParams rootlp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
        mVideoView = new VideoView(this);
        FrameLayout.LayoutParams videolp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        mVideoView.setLayoutParams(videolp);
        mRootLayout.addView(mVideoView);
        setContentView(mRootLayout);
        //create the locks
        mMediaControllerLock = new ReentrantLock();
        mMediaPlayerLock = new ReentrantLock();
        //get all passed params
        mSeekPosition = getIntent().getIntExtra("seekposition", 0);
        mMovieUrl = getIntent().getStringExtra("movieurl");
        mRequestedOrientation = getIntent().getIntExtra("requestedorientation", 0);
        mAutostart = getIntent().getBooleanExtra("autostart", false);

        setRequestedOrientation(mRequestedOrientation);

        mHolder = mVideoView.getHolder();
        mHolder.addCallback(this);

        mVideoView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mMediaControllerLock.lock();
                // This simply toggles the MediaController visibility:
                if (mMediaController != null) {
                    if (mMediaController.isShowing()) {
                        mMediaController.hide();
                    }
                    else {
                        mMediaController.show();
                    }
                }
                mMediaControllerLock.unlock();
            }
        });
    }

    /**
     * \brief Create the media player and load video
     */
    private void createMediaPlayer()
    {
        mMediaPlayerLock.lock();
        mMediaControllerLock.lock();

        mMediaPlayer = new MediaPlayer();
        mMediaController = new MediaController(this);

        AssetFileDescriptor afd = null;
        boolean fileExist = true;
        try {
            afd = getAssets().openFd(mMovieUrl);
        } catch (IOException e) {
            fileExist=false;
        }
        if(afd==null) {
            fileExist=false;
        }
        try {
            if (fileExist) {
                mMediaPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                afd.close();
            } else {
                String URL_REGEX = "^((https?|ftp)://|(www|ftp)\\.)[a-z0-9-]+(\\.[a-z0-9-]+)+((:[0-9]+)|)+([/?].*)?$"; //should be ok
                Pattern p = Pattern.compile(URL_REGEX);
                Matcher m = p.matcher(mMovieUrl);//replace with string to compare
                if (m.find()) {
                    mMediaPlayer.setDataSource(mMovieUrl);
                }
            }

            mMediaPlayer.setDisplay(mHolder);
            mMediaPlayer.prepareAsync();
            mMediaPlayer.setOnPreparedListener(this);
            mMediaPlayer.setOnErrorListener(this);
            mMediaPlayer.setOnCompletionListener(this);
            mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        } catch (Exception e) {
            Log.e("PikkartFullscreenVideo", "error while creating the MediaPlayer: " + e.toString());
            prepareForTermination();
            destroyMediaPlayer();
            finish();
        }

        mMediaControllerLock.unlock();
        mMediaPlayerLock.unlock();
    }

    /**
     * \brief destroy the media player and the media controller
     */
    private void destroyMediaPlayer()
    {
        mMediaControllerLock.lock();
        if (mMediaController != null) {
            mMediaController.removeAllViews();
            mMediaController = null;
        }
        mMediaControllerLock.unlock();

        mMediaPlayerLock.lock();
        if (mMediaPlayer != null) {
            try {
                mMediaPlayer.stop();
            } catch (Exception e) {
                mMediaPlayerLock.unlock();
                Log.e("PikkartFullscreenVideo", "could not stop playback");
            }
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
        mMediaPlayerLock.unlock();
    }

    /**
     * \brief destroy al views and force garbage collection
     */
    private void destroyView()
    {
        mVideoView = null;
        mHolder = null;
        System.gc();
    }

    /**
     * \brief The activity onDestroy function
     */
    @Override
    protected void onDestroy()
    {
        prepareForTermination();
        super.onDestroy();
        destroyMediaPlayer();
        mMediaPlayerLock = null;
        mMediaControllerLock = null;
    }

    /**
     * \brief The activity onResume function
     */
    @Override
    protected void onResume()
    {
        super.onResume();
        setRequestedOrientation(mRequestedOrientation);
        mHolder = mVideoView.getHolder();
        mHolder.addCallback(this);
    }

    /**
     * \brief The activity onConfigurationChanged function
     */
    @Override
    public void onConfigurationChanged(Configuration config)
    {
        super.onConfigurationChanged(config);
    }

    /**
     * \brief prepare objects for termination (stop mediaplayers, unload video, etc.)
     */
    private void prepareForTermination()
    {
        mMediaControllerLock.lock();
        if (mMediaController != null) {
            mMediaController.hide();
            mMediaController.removeAllViews();
        }
        mMediaControllerLock.unlock();

        mMediaPlayerLock.lock();
        if (mMediaPlayer != null) {
            mSeekPosition = mMediaPlayer.getCurrentPosition();
            boolean wasPlaying = mMediaPlayer.isPlaying();
            if (wasPlaying) {
                try {
                    mMediaPlayer.pause();
                } catch (Exception e) {
                    mMediaPlayerLock.unlock();
                    Log.e("PikkartFullscreenVideo", "could not pause playback");
                }
            }
        }
        mMediaPlayerLock.unlock();
    }

    /**
     * \brief on back button pressure funtion
     */
    public void onBackPressed()
    {
        prepareForTermination();
        super.onBackPressed();
    }

    /**
     * \brief The activity onPause function
     */
    protected void onPause()
    {
        super.onPause();
        prepareForTermination();
        destroyMediaPlayer();
        destroyView();
    }

    /**
     * \brief Callback for interface SurfaceHolder.Callback, surface holder created
     * @param holder the created SurfaceHolder
     */
    public void surfaceCreated(SurfaceHolder holder)
    {
        createMediaPlayer();
    }

    /**
     * \brief Callback for interface SurfaceHolder.Callback, surface holder modified
     * @param holder the modified SurfaceHolder
     * @param format new data format
     * @param width new width
     * @param height new height
     */
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height)
    {
    }

    /**
     * \brief Callback for interface SurfaceHolder.Callback, surface holder destroyed
     * @param holder the created SurfaceHolder
     */
    public void surfaceDestroyed(SurfaceHolder holder)
    {
    }

    /**
     * \brief Callback for interface MediaPlayer.OnPreparedListener
     * @param mediaPlayer
     */
    @Override
    public void onPrepared(MediaPlayer mediaPlayer)
    {
        mMediaControllerLock.lock();
        mMediaPlayerLock.lock();
        if ((mMediaController != null) && (mVideoView != null) && (mMediaPlayer != null)) {
            if (mVideoView.getParent() != null) {
                mMediaController.setMediaPlayer(media_player_interface);
                View anchorView = mVideoView.getParent() instanceof View ? (View) mVideoView.getParent() : mVideoView;
                mMediaController.setAnchorView(anchorView);
                mVideoView.setMediaController(mMediaController);
                mMediaController.setEnabled(true);
                try {
                    mMediaPlayer.seekTo(mSeekPosition);
                } catch (Exception e) {
                    mMediaPlayerLock.unlock();
                    mMediaControllerLock.unlock();
                    Log.e("PikkartFullscreenVideo", "Could not seek to a position");
                }
                if (mAutostart) {
                    try {
                        mMediaPlayer.start();
                        //mAutostart = false;
                    } catch (Exception e) {
                        mMediaPlayerLock.unlock();
                        mMediaControllerLock.unlock();
                        Log.e("PikkartFullscreenVideo", "Could not start playback");
                    }
                }
                mMediaController.show();
            }
        }
        mMediaPlayerLock.unlock();
        mMediaControllerLock.unlock();
    }

    /**
     * \brief Callback for interface MediaPlayer.OnCompletionListener
     * @param mediaPlayer
     */
    @Override
    public void onCompletion(MediaPlayer mediaPlayer)
    {
        prepareForTermination();
        finish();
    }

    /**
     * \brief Callback for interface MediaPlayer.OnErrorListener
     * @param mediaPlayer
     * @param i error code
     * @param i1 error sub-code
     * @return true if the error is relative to this video player media player
     */
    @Override
    public boolean onError(MediaPlayer mediaPlayer, int i, int i1)
    {
        if (mediaPlayer == mMediaPlayer) {
            String errorDescription;
            switch (i) {
                case MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK:
                    errorDescription = "The video is streamed and its container is not valid for progressive playback";
                    break;
                case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
                    errorDescription = "Media server died";
                    break;
                case MediaPlayer.MEDIA_ERROR_UNKNOWN:
                    errorDescription = "Unspecified media player error";
                    break;
                default:
                    errorDescription = "Unknown error " + i;
                    break;
            }
            Log.e("PikkartFullscreenVideo", "Error while opening the file for fullscreen. Unloading the media player (" + errorDescription + ", " + i1 + ")");
            prepareForTermination();
            destroyMediaPlayer();
            finish();
            return true;
        }

        return false;
    }

    /**
     * \brief Callback for interface MediaPlayer.OnBufferingUpdateListener
     * @param mediaPlayer
     * @param i the buffering percentage (int from 0 to 100)
     */
    @Override
    public void onBufferingUpdate(MediaPlayer mediaPlayer, int i)
    {
        mCurrentBufferingPercent = i;
    }

    /**
     * \brief The media player interface controller
     */
    private MediaController.MediaPlayerControl media_player_interface = new MediaController.MediaPlayerControl()
    {
        public int getBufferPercentage() {
            return mCurrentBufferingPercent;
        }

        public int getCurrentPosition() {
            int result = 0;
            mMediaPlayerLock.lock();
            if (mMediaPlayer != null)
                result = mMediaPlayer.getCurrentPosition();
            mMediaPlayerLock.unlock();
            return result;
        }

        public int getDuration() {
            int result = 0;
            mMediaPlayerLock.lock();
            if (mMediaPlayer != null)
                result = mMediaPlayer.getDuration();
            mMediaPlayerLock.unlock();
            return result;
        }

        public boolean isPlaying() {
            boolean result = false;
            mMediaPlayerLock.lock();
            if (mMediaPlayer != null)
                result = mMediaPlayer.isPlaying();
            mMediaPlayerLock.unlock();
            return result;
        }

        public void pause() {
            mMediaPlayerLock.lock();
            if (mMediaPlayer != null) {
                try {
                    mMediaPlayer.pause();
                } catch (Exception e) {
                    mMediaPlayerLock.unlock();
                }
            }
            mMediaPlayerLock.unlock();
        }

        public void seekTo(int pos) {
            mMediaPlayerLock.lock();
            if (mMediaPlayer != null) {
                try {
                    mMediaPlayer.seekTo(pos);
                } catch (Exception e) {
                    mMediaPlayerLock.unlock();
                }
            }
            mMediaPlayerLock.unlock();
        }

        public void start() {
            mMediaPlayerLock.lock();
            if (mMediaPlayer != null) {
                try {
                    mMediaPlayer.start();
                } catch (Exception e) {
                    mMediaPlayerLock.unlock();
                }
            }
            mMediaPlayerLock.unlock();
        }

        public boolean canPause() {
            return true;
        }

        public boolean canSeekBackward() {
            return true;
        }

        public boolean canSeekForward() {
            return true;
        }

        public int getAudioSessionId() {
            return 0;
        }
    };

}
