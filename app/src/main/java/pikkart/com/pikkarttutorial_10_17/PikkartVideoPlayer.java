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
import android.content.pm.ActivityInfo;
import android.content.res.AssetFileDescriptor;
import android.graphics.SurfaceTexture;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * \class PikkartVideoPlayer
 * \brief The AR Video player class.
 *
 * The AR Video player class.
 * It encapsulates a MediaPlayer and a SurfaceTexture on which the video data is stored for
 * later use by out OpenGL renderer. The class also manages the video state.
 */
public class PikkartVideoPlayer implements MediaPlayer.OnBufferingUpdateListener, MediaPlayer.OnCompletionListener,
        MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener
{
    private Activity mParentActivity = null; /**< the parent activity */
    private MediaPlayer mMediaPlayer = null; /**< the media player that decode the media */
    private SurfaceTexture mSurfaceTexture = null; /**< the surface texture in which media(video) data is stored*/
    private String mMovieUrl = ""; /**< the movie file (URL or file path)*/
    private boolean mAutoStart = true; /**< should the video autostart when ready*/
    private VIDEO_STATE mVideoState = VIDEO_STATE.NOT_READY; /**< the media player current state*/
    private Intent mPlayFullScreenIntent = null; /**< play in ullscreen intent (in case AR videos are not supported) */
    private boolean mFullscreen = false; /**< should the movie play fullscreen*/
    private int mSeekPosition = -1; /**< where to start video playback*/
    private byte mTextureID = 0; /**< the opengl texture id in which to store video data*/

    private ReentrantLock mMediaPlayerLock = null; /**< media player mutex */
    private ReentrantLock mSurfaceTextureLock = null; /**< surfacetexture mutex*/

    private int mCurrentBufferingPercent = 0; /**< buffering percentage*/

    /**
     * \brief Get the movie file URL or file path.
     * @return the file path or URL as String.
     */
    public String getMovieUrl()
    {
        return mMovieUrl;
    }

    /**
     * \brief Get the movie status.
     * @return the movie status.
     */
    public VIDEO_STATE getVideoStatus()
    {
        return mVideoState;
    }

    /**
     * \brief Is the movie playing fullscreen.
     * @return true if fullscreen.
     */
    public boolean isFullscreen()
    {
        return mFullscreen;
    }

    /**
     * \brief Get current buffering percentage
     * @return true if fullscreen.
     */
    public int getCurrentBufferingPercent()
    {
        return mCurrentBufferingPercent;
    }

    /**
     * \brief Set parent activity
     * @return true if fullscreen.
     */
    public void setActivity(Activity newActivity)
    {
        mParentActivity = newActivity;
    }

    /**
     * \brief Callback for interface MediaPlayer.OnBufferingUpdateListener
     * @param mediaPlayer
     * @param i the buffering percentage (int from 0 to 100)
     */
    @Override
    public void onBufferingUpdate(MediaPlayer mediaPlayer, int i)
    {
        mVideoState = VIDEO_STATE.BUFFERING;
        if(i==100) mVideoState = VIDEO_STATE.READY;
        mMediaPlayerLock.lock();
        if (mMediaPlayer != null) {
            if (mediaPlayer == mMediaPlayer)
                mCurrentBufferingPercent = i;
        }
        mMediaPlayerLock.unlock();
    }

    /**
     * \brief Callback for interface MediaPlayer.OnCompletionListener
     * @param mediaPlayer
     */
    @Override
    public void onCompletion(MediaPlayer mediaPlayer)
    {
        mVideoState = VIDEO_STATE.END;
    }

    /**
     * \brief Callback for interface MediaPlayer.OnPreparedListener
     * @param mediaPlayer
     */
    @Override
    public void onPrepared(MediaPlayer mediaPlayer)
    {
        mVideoState = VIDEO_STATE.READY;
        // If requested an immediate play
        if (mAutoStart)
            play();
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
            }
            Log.e("Pikkart AR Video", "Error while opening the file. Unloading the media player (" + errorDescription + ", " + i1 + ")");
            unload();
            mVideoState = VIDEO_STATE.ERROR;
            return true;
        }
        return false;
    }

    /**
     * \brief Enum for the possible state of the video player
     */
    public enum VIDEO_STATE
    {
        END             (0),
        PAUSED          (1),
        STOPPED         (2),
        PLAYING         (3),
        READY           (4),
        NOT_READY       (5),
        BUFFERING       (6),
        ERROR           (7);

        private int type;

        VIDEO_STATE (int i)
        {
            this.type = i;
        }

        public int getNumericType()
        {
            return type;
        }
    }

    /**
     * \brief initialization, pretty dumb
     */
    public void init()
    {
        mMediaPlayerLock = new ReentrantLock();
        mSurfaceTextureLock = new ReentrantLock();
    }

    /**
     * \brief Deinitialization, unload stuff and release surface texture.
     */
    public void deinit()
    {
        unload();

        mSurfaceTextureLock.lock();
        mSurfaceTexture = null;
        mSurfaceTextureLock.unlock();
    }

    /**
     * \brief load a media file, either from file or from web
     * @param url file path or url (String)
     * @param playFullscreen whatever should play in fullscreen or in AR
     * @param autoStart auto-start when ready
     * @param seekPosition start position (in milliseconds)
     * @return true on success
     */
    public boolean load(String url, boolean playFullscreen, boolean autoStart, int seekPosition)
    {
        mMediaPlayerLock.lock();
        mSurfaceTextureLock.lock();

        //if it's in a different state than NOT_READY don't load it. unload() must be called first!
        if( (mVideoState != VIDEO_STATE.NOT_READY ) || (mMediaPlayer != null)) {
            Log.w("Pikkart AR Video", "Already loaded");
            mSurfaceTextureLock.unlock();
            mMediaPlayerLock.unlock();
            return false;
        }
        //if AR video (not fullscreen) was requested create and set the media player
        //we can play video in AR only if a SurfaceTexture has been created
        if(!playFullscreen && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) && mSurfaceTexture != null) {
            mMediaPlayer = new MediaPlayer();
            //first search for the video locally, then check online
            AssetFileDescriptor afd = null;
            boolean fileExist = true;
            try {
                afd = mParentActivity.getAssets().openFd(url);
            } catch (IOException e) {
                fileExist=false;
            }
            if(afd==null) {
                fileExist=false;
            }

            try {
                if (fileExist) {
                    mMovieUrl = url;
                    mMediaPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                    afd.close();
                } else {
                    String URL_REGEX = "^((https?|ftp)://|(www|ftp)\\.)[a-z0-9-]+(\\.[a-z0-9-]+)+((:[0-9]+)|)+([/?].*)?$"; //should be ok
                    Pattern p = Pattern.compile(URL_REGEX);
                    Matcher m = p.matcher(url);//replace with string to compare
                    if (m.find()) {
                        mMovieUrl = url;
                        mMediaPlayer.setDataSource(mMovieUrl);
                    }
                }
                mMediaPlayer.setOnPreparedListener(this);
                mMediaPlayer.setOnBufferingUpdateListener(this);
                mMediaPlayer.setOnCompletionListener(this);
                mMediaPlayer.setOnErrorListener(this);
                mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                mMediaPlayer.setSurface(new Surface(mSurfaceTexture));
                mFullscreen = false;
                mAutoStart = autoStart;
                mMediaPlayer.prepareAsync();
                mSeekPosition = seekPosition;
            } catch(IOException e) {
                Log.e("Pikkart AR Video", "Error while creating the MediaPlayer: " + e.toString());
                mMovieUrl = "";
                mVideoState = VIDEO_STATE.ERROR;
                mMediaPlayerLock.unlock();
                mSurfaceTextureLock.unlock();
                return false;
            }
        }
        else { //play full screen if requested or old android
            mPlayFullScreenIntent = new Intent(mParentActivity, FullscreenVideoPlayer.class);
            mPlayFullScreenIntent.setAction(android.content.Intent.ACTION_VIEW);
            mFullscreen = true;
            mMovieUrl = url;
            mSeekPosition = seekPosition;
            mVideoState = VIDEO_STATE.READY;
        }

        mSurfaceTextureLock.unlock();
        mMediaPlayerLock.unlock();

        return true;
    }

    /**
     * \brief unload a media file, media player and related data
     * @return true on success
     */
    public boolean unload()
    {
        mMediaPlayerLock.lock();
        if (mMediaPlayer != null) {
            try {
                mMediaPlayer.stop();
            } catch (Exception e) {
                mMediaPlayerLock.unlock();
                Log.e("Pikkart AR Video", "Could not start playback");
            }

            mMediaPlayer.release();
            mMediaPlayer = null;
        }
        mMediaPlayerLock.unlock();

        mVideoState = VIDEO_STATE.NOT_READY;
        mFullscreen = false;
        mAutoStart=false;
        mSeekPosition = -1;
        mMovieUrl = "";
        return true;
    }

    /**
     * \brief get video screen width
     * @return video screen width
     */
    public int getVideoWidth()
    {
        if (mFullscreen) {
            Log.w("Pikkart AR Video", "cannot get the video width if it is playing fullscreen");
            return -1;
        }

        if ((mVideoState == VIDEO_STATE.NOT_READY) || (mVideoState == VIDEO_STATE.ERROR)) {
            Log.w("Pikkart AR Video", "cannot get the video width if it is not ready");
            return -1;
        }

        int result = -1;
        mMediaPlayerLock.lock();
        if (mMediaPlayer != null) {
            result = mMediaPlayer.getVideoWidth();
        }
        mMediaPlayerLock.unlock();

        return result;
    }

    /**
     * \brief get video screen height
     * @return video screen height
     */
    public int getVideoHeight()
    {
        if (mFullscreen) {
            Log.w("Pikkart AR Video", "cannot get the video height if it is playing fullscreen");
            return -1;
        }

        if ((mVideoState == VIDEO_STATE.NOT_READY) || (mVideoState == VIDEO_STATE.ERROR)) {
            Log.w("Pikkart AR Video", "cannot get the video height if it is not ready");
            return -1;
        }

        int result = -1;
        mMediaPlayerLock.lock();
        if (mMediaPlayer != null) {
            result = mMediaPlayer.getVideoHeight();
        }
        mMediaPlayerLock.unlock();

        return result;
    }

    /**
     * \brief get video duration
     * @return video duration
     */
    public int getLength()
    {
        if (mFullscreen) {
            Log.w("Pikkart AR Video", "cannot get the video length if it is playing fullscreen");
            return -1;
        }

        if ((mVideoState == VIDEO_STATE.NOT_READY) || (mVideoState == VIDEO_STATE.ERROR)) {
            Log.w("Pikkart AR Video", "cannot get the video length if it is not ready");
            return -1;
        }

        int result = -1;
        mMediaPlayerLock.lock();
        if (mMediaPlayer != null) {
            result = mMediaPlayer.getDuration();
        }
        mMediaPlayerLock.unlock();

        return result;
    }

    /**
     * \brief request the video to be played
     * @return true on success
     */
    public boolean play()
    {
        if (mFullscreen) {
            mPlayFullScreenIntent.putExtra( "autostart", true);
            if(mSeekPosition!=-1)  mPlayFullScreenIntent.putExtra("seekposition", mSeekPosition);
            mPlayFullScreenIntent.putExtra("requestedorientation", ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            mPlayFullScreenIntent.putExtra("movieurl", mMovieUrl);
            mParentActivity.startActivity(mPlayFullScreenIntent);
            return true;
        }
        else {
            if ((mVideoState == VIDEO_STATE.NOT_READY) || (mVideoState == VIDEO_STATE.ERROR)) {
                Log.w("Pikkart AR Video", "cannot play this video if it is not ready");
                return false;
            }
            mMediaPlayerLock.lock();
            if(mSeekPosition!=-1) {
                try {
                    mMediaPlayer.seekTo(mSeekPosition);
                } catch (Exception e) {}
            }
            else {
                try {
                    mMediaPlayer.seekTo(0);
                } catch (Exception e) {}
            }
            try {
                mMediaPlayer.start();
            } catch (Exception e) {
                Log.e("Pikkart AR Video", "could not start playback");
            }
            mVideoState = VIDEO_STATE.PLAYING;
            mMediaPlayerLock.unlock();
            return true;
        }
    }

    /**
     * \brief pauses the current movie being played
     * @return true on success
     */
    public boolean pause()
    {
        if (mFullscreen) {
            Log.w("Pikkart AR Video", "cannot pause this video since it is fullscreen");
            return false;
        }
        if ((mVideoState == VIDEO_STATE.NOT_READY) || (mVideoState == VIDEO_STATE.ERROR)) {
            Log.w("Pikkart AR Video", "cannot pause this video if it is not ready");
            return false;
        }
        boolean result = false;
        mMediaPlayerLock.lock();
        if (mMediaPlayer != null) {
            if (mMediaPlayer.isPlaying()) {
                try {
                    mMediaPlayer.pause();
                } catch (Exception e) {
                    mMediaPlayerLock.unlock();
                    Log.e("Pikkart AR Video", "could not pause playback");
                }
                mVideoState = VIDEO_STATE.PAUSED;
                result = true;
            }
        }
        mMediaPlayerLock.unlock();
        return result;
    }

    /**
     * \brief stop the current movie being played
     * @return true on success
     */
    public boolean stop()
    {
        if (mFullscreen) {
            Log.d("Pikkart AR Video", "cannot stop this video since it is not on texture");
            return false;
        }
        if ((mVideoState == VIDEO_STATE.NOT_READY) || (mVideoState == VIDEO_STATE.ERROR)) {
            Log.d("Pikkart AR Video", "cannot stop this video if it is not ready");
            return false;
        }
        boolean result = false;
        mMediaPlayerLock.lock();
        if (mMediaPlayer != null) {
            mVideoState = VIDEO_STATE.STOPPED;
            try {
                mMediaPlayer.stop();
            } catch (Exception e) {
                mMediaPlayerLock.unlock();
                Log.e("Pikkart AR Video", "Could not stop playback");
            }
            result = true;
        }
        mMediaPlayerLock.unlock();
        return result;
    }

    /**
     * \brief update the surface texture with new video data
     * @return OpenGL texture id assigned to the surfacetexture
     */
    public byte updateVideoData()
    {
        if (mFullscreen) {
            return -1;
        }
        byte result = -1;
        mSurfaceTextureLock.lock();
        if (mSurfaceTexture != null) {
            if (mVideoState == VIDEO_STATE.PLAYING)
                mSurfaceTexture.updateTexImage();
            result = mTextureID;
        }
        mSurfaceTextureLock.unlock();
        return result;
    }

    /**
     * \brief move video playback to seek position
     * @param position seek to position (in milliseconds)
     * @return true on success
     */
    public boolean seekTo(int position)
    {
        if (mFullscreen) {
            Log.d("Pikkart AR Video", "cannot seek-to on this video since it is fullscreen");
            return false;
        }
        if ((mVideoState == VIDEO_STATE.NOT_READY) || (mVideoState == VIDEO_STATE.ERROR)) {
            Log.d("Pikkart AR Video", "cannot seek-to on this video if it is not ready");
            return false;
        }
        boolean result = false;
        mMediaPlayerLock.lock();
        if (mMediaPlayer != null) {
            try {
                mMediaPlayer.seekTo(position);
            } catch (Exception e) {
                mMediaPlayerLock.unlock();
                Log.e("Pikkart AR Video", "could not seek to position");
            }
            result = true;
        }
        mMediaPlayerLock.unlock();
        return result;
    }

    /**
     * \brief get current playback position
     * @return playback position (in milliseconds)
     */
    public int getCurrentPosition()
    {
        if (mFullscreen) {
            return -1;
        }
        if ((mVideoState == VIDEO_STATE.NOT_READY) || (mVideoState == VIDEO_STATE.ERROR)) {
            return -1;
        }
        int result = -1;
        mMediaPlayerLock.lock();
        if (mMediaPlayer != null) {
            result = mMediaPlayer.getCurrentPosition();
        }
        mMediaPlayerLock.unlock();
        return result;
    }

    /**
     * \brief set video volume
     * @param value volume (0.0 to 1.0)
     * @return true on success
     */
    public boolean setVolume(float value)
    {
        if (mFullscreen) {
            return false;
        }
        if ((mVideoState == VIDEO_STATE.NOT_READY) || (mVideoState == VIDEO_STATE.ERROR)) {
            return false;
        }
        boolean result = false;
        mMediaPlayerLock.lock();
        if (mMediaPlayer != null) {
            mMediaPlayer.setVolume(value, value);
            result = true;
        }
        mMediaPlayerLock.unlock();
        return result;
    }

    /**
     * \brief set the surfacetexture with a given OpenGL texture id
     * @param TextureID the opengl texture id
     * @return true on success
     */
    public boolean setupSurfaceTexture(int TextureID)
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            mSurfaceTextureLock.lock();
            mSurfaceTexture = new SurfaceTexture(TextureID);
            mTextureID = (byte) TextureID;
            mSurfaceTextureLock.unlock();
            return true;
        }
        else {
            return false;
        }
    }

    /**
     * \brief get the surface texture transformation matrix (used to transform texture coordinates in OpenGL)
     * @param mtx the float array where to store matrix data
     */
    public void getSurfaceTextureTransformMatrix(float[] mtx)
    {
        mSurfaceTextureLock.lock();
        if (mSurfaceTexture != null)
            mSurfaceTexture.getTransformMatrix(mtx);
        mSurfaceTextureLock.unlock();
    }
}
