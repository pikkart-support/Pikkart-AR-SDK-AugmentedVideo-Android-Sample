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

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.GLDebugHelper;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.view.View.OnLayoutChangeListener;

import java.io.Writer;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGL11;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;
import javax.microedition.khronos.opengles.GL;
import javax.microedition.khronos.opengles.GL10;

/**
 * An android texture view containing a renderable gl surface
 */
/**
 * \class GLTextureView
 * \brief An android texture view containing a renderable gl surface
 *
 * A TextureView that replicates the functionalities of a GLSurfaceView
 */
public class GLTextureView extends TextureView implements TextureView.SurfaceTextureListener, OnLayoutChangeListener
{
    private final static String TAG = "GLSurfaceView";
    private final static boolean LOG_ATTACH_DETACH = false;
    private final static boolean LOG_THREADS = false;
    private final static boolean LOG_PAUSE_RESUME = false;
    private final static boolean LOG_SURFACE = false;
    private final static boolean LOG_RENDERER = false;
    private final static boolean LOG_RENDERER_DRAW_FRAME = false;
    private final static boolean LOG_EGL = false;

    public final static int RENDERMODE_WHEN_DIRTY = 0;
    public final static int RENDERMODE_CONTINUOUSLY = 1;
    public final static int DEBUG_CHECK_GL_ERROR = 1;
    public final static int DEBUG_LOG_GL_CALLS = 2;

    public GLTextureView(Context context)
    {
        super(context);
        init();
    }

    public GLTextureView(Context context, AttributeSet attrs)
    {
        super(context, attrs);
        init();
    }

    @Override
    protected void finalize() throws Throwable 
    {
        try 
        {
            if (mGLThread != null) 
            {
                // GLThread may still be running if this view was never attached to a window.
                mGLThread.requestExitAndWait();
            }
        } 
        finally 
        {
            super.finalize();
        }
    }

    private void init() 
    {
    	setSurfaceTextureListener(this);
    	addOnLayoutChangeListener(this);
    }

    public void setGLWrapper(GLWrapper glWrapper) 
    {
        mGLWrapper = glWrapper;
    }

    public void setDebugFlags(int debugFlags) 
    {
        mDebugFlags = debugFlags;
    }

    public int getDebugFlags() 
    {
        return mDebugFlags;
    }

    public void setPreserveEGLContextOnPause(boolean preserveOnPause) 
    {
        mPreserveEGLContextOnPause = preserveOnPause;
    }

    public boolean getPreserveEGLContextOnPause() 
    {
        return mPreserveEGLContextOnPause;
    }

    public void setRenderer(Renderer renderer) 
    {
        checkRenderThreadState();
        if (mEGLConfigChooser == null) 
        {
            mEGLConfigChooser = new SimpleEGLConfigChooser(true);
        }
        if (mEGLContextFactory == null) 
        {
            mEGLContextFactory = new DefaultContextFactory();
        }
        if (mEGLWindowSurfaceFactory == null) 
        {
            mEGLWindowSurfaceFactory = new DefaultWindowSurfaceFactory();
        }
        mRenderer = renderer;
        mGLThread = new GLThread(mThisWeakRef);
        mGLThread.start();
    }

    public void setEGLContextFactory(EGLContextFactory factory) 
    {
        checkRenderThreadState();
        mEGLContextFactory = factory;
    }

    public void setEGLWindowSurfaceFactory(EGLWindowSurfaceFactory factory) 
    {
        checkRenderThreadState();
        mEGLWindowSurfaceFactory = factory;
    }

    public void setEGLConfigChooser(EGLConfigChooser configChooser) 
    {
        checkRenderThreadState();
        mEGLConfigChooser = configChooser;
    }

    public void setEGLConfigChooser(boolean needDepth) 
    {
        setEGLConfigChooser(new SimpleEGLConfigChooser(needDepth));
    }

    public void setEGLConfigChooser(int redSize, int greenSize, int blueSize, int alphaSize, int depthSize, int stencilSize) 
    {
        setEGLConfigChooser(new ComponentSizeChooser(redSize, greenSize, blueSize, alphaSize, depthSize, stencilSize));
    }

    public void setEGLContextClientVersion(int version) 
    {
        checkRenderThreadState();
        mEGLContextClientVersion = version;
    }

    public void setRenderMode(int renderMode) 
    {
        mGLThread.setRenderMode(renderMode);
    }

    public int getRenderMode()
    {
        return mGLThread.getRenderMode();
    }

    public void onSurfaceTextureUpdated(SurfaceTexture surface) 
    {
        mGLThread.requestRender();
    }

    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) 
    {
        mGLThread.surfaceCreated();
    }

    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface)
    {
        // Surface will be destroyed when we return
        mGLThread.surfaceDestroyed();
        return true;
    }

    public void onSurfaceTextureSizeChanged (SurfaceTexture surface, int w, int h)
    {
        mGLThread.onWindowResize(w, h);
    }

    public void onPause() 
    {
        mGLThread.onPause();
    }

    public void onResume() 
    {
        mGLThread.onResume();
    }

    public void queueEvent(Runnable r)
    {
        mGLThread.queueEvent(r);
    }

    @Override
    protected void onAttachedToWindow() 
    {
        super.onAttachedToWindow();
        if (LOG_ATTACH_DETACH) 
        {
            Log.d(TAG, "onAttachedToWindow reattach =" + mDetached);
        }
        if (mDetached && (mRenderer != null)) 
        {
            int renderMode = RENDERMODE_CONTINUOUSLY;
            if (mGLThread != null) 
            {
                renderMode = mGLThread.getRenderMode();
            }
            mGLThread = new GLThread(mThisWeakRef);
            if (renderMode != RENDERMODE_CONTINUOUSLY) 
            {
                mGLThread.setRenderMode(renderMode);
            }
            mGLThread.start();
        }
        mDetached = false;
    }

    @Override
    protected void onDetachedFromWindow() 
    {
        if (LOG_ATTACH_DETACH) 
        {
            Log.d(TAG, "onDetachedFromWindow");
        }
        if (mGLThread != null) 
        {
            mGLThread.requestExitAndWait();
        }
        mDetached = true;
        super.onDetachedFromWindow();
    }

    /*public void OnLayoutChangeListener()
    {
    	surfaceChanged(getSurfaceTexture(), 0, right - left, bottom - top);
    }*/
    // ----------------------------------------------------------------------

    public interface GLWrapper 
    {
        /**
         * Wraps a gl interface in another gl interface.
         * @param gl a GL interface that is to be wrapped.
         * @return either the input argument or another GL object that wraps the input argument.
         */
        GL wrap(GL gl);
    }

    public interface Renderer 
    {
        /**
         * Called when the surface is created or recreated.
         * <p>
         * Called when the com.pikkart.ar.rendering thread
         * starts and whenever the EGL context is lost. The EGL context will typically
         * be lost when the Android device awakes after going to sleep.
         * <p>
         * Since this method is called at the beginning of com.pikkart.ar.rendering, as well as
         * every time the EGL context is lost, this method is a convenient place to put
         * code to create resources that need to be created when the com.pikkart.ar.rendering
         * starts, and that need to be recreated when the EGL context is lost.
         * Textures are an example of a resource that you might want to create
         * here.
         * <p>
         * Note that when the EGL context is lost, all OpenGL resources associated
         * with that context will be automatically deleted. You do not need to call
         * the corresponding "glDelete" methods such as glDeleteTextures to
         * manually delete these lost resources.
         * <p>
         * @param gl the GL interface. Use <code>instanceof</code> to
         * test if the interface supports GL11 or higher interfaces.
         * @param config the EGLConfig of the created surface. Can be used
         * to create matching pbuffers.
         */
        void onSurfaceCreated(GL10 gl, EGLConfig config);

        /**
         * Called when the surface changed size.
         * <p>
         * Called after the surface is created and whenever
         * the OpenGL ES surface size changes.
         * <p>
         * Typically you will set your viewport here. If your camera
         * is fixed then you could also set your projection matrix here:
         * <pre class="prettyprint">
         * void onSurfaceChanged(GL10 gl, int width, int height) {
         *     gl.glViewport(0, 0, width, height);
         *     // for a fixed camera, set the projection too
         *     float ratio = (float) width / height;
         *     gl.glMatrixMode(GL10.GL_PROJECTION);
         *     gl.glLoadIdentity();
         *     gl.glFrustumf(-ratio, ratio, -1, 1, 1, 10);
         * }
         * </pre>
         * @param gl the GL interface. Use <code>instanceof</code> to
         * test if the interface supports GL11 or higher interfaces.
         * @param width
         * @param height
         */
        void onSurfaceChanged(GL10 gl, int width, int height);

        void onSurfaceDestroyed();

        /**
         * Called to draw the current frame.
         * <p>
         * This method is responsible for drawing the current frame.
         * <p>
         * The implementation of this method typically looks like this:
         * <pre class="prettyprint">
         * void onDrawFrame(GL10 gl) {
         *     gl.glClear(GL10.GL_COLOR_BUFFER_BIT | GL10.GL_DEPTH_BUFFER_BIT);
         *     //... other gl calls to render the scene ...
         * }
         * </pre>
         * @param gl the GL interface. Use <code>instanceof</code> to
         * test if the interface supports GL11 or higher interfaces.
         */
        void onDrawFrame(GL10 gl);
    }

    public interface EGLContextFactory 
    {
        EGLContext createContext(EGL10 egl, EGLDisplay display, EGLConfig eglConfig);
        void destroyContext(EGL10 egl, EGLDisplay display, EGLContext context);
    }

    private class DefaultContextFactory implements EGLContextFactory 
    {
        private int EGL_CONTEXT_CLIENT_VERSION = 0x3098;

        public EGLContext createContext(EGL10 egl, EGLDisplay display, EGLConfig config) 
        {
            int[] attrib_list = {EGL_CONTEXT_CLIENT_VERSION, mEGLContextClientVersion, EGL10.EGL_NONE };

            return egl.eglCreateContext(display, config, EGL10.EGL_NO_CONTEXT, mEGLContextClientVersion != 0 ? attrib_list : null);
        }

        public void destroyContext(EGL10 egl, EGLDisplay display, EGLContext context) 
        {
            if (!egl.eglDestroyContext(display, context)) 
            {
                Log.e("DefaultContextFactory", "display:" + display + " context: " + context);
                if (LOG_THREADS) 
                {
                    Log.i("DefaultContextFactory", "tid=" + Thread.currentThread().getId());
                }
                EglHelper.throwEglException("eglDestroyContex", egl.eglGetError());
            }
        }
    }

    public interface EGLWindowSurfaceFactory 
    {
        /**
         *  @return null if the surface cannot be constructed.
         */
        EGLSurface createWindowSurface(EGL10 egl, EGLDisplay display, EGLConfig config, Object nativeWindow);
        void destroySurface(EGL10 egl, EGLDisplay display, EGLSurface surface);
    }

    private static class DefaultWindowSurfaceFactory implements EGLWindowSurfaceFactory 
    {
        public EGLSurface createWindowSurface(EGL10 egl, EGLDisplay display, EGLConfig config, Object nativeWindow) 
        {
            EGLSurface result = null;
            try 
            {
                result = egl.eglCreateWindowSurface(display, config, nativeWindow, null);
            } 
            catch (IllegalArgumentException e) 
            {
                // This exception indicates that the surface flinger surface
                // is not valid. This can happen if the surface flinger surface has
                // been torn down, but the application has not yet been
                // notified via SurfaceHolder.Callback.surfaceDestroyed.
                // In theory the application should be notified first,
                // but in practice sometimes it is not. See b/4588890
                Log.e(TAG, "eglCreateWindowSurface", e);
            }
            return result;
        }

        public void destroySurface(EGL10 egl, EGLDisplay display, EGLSurface surface) 
        {
            egl.eglDestroySurface(display, surface);
        }
    }

    public interface EGLConfigChooser 
    {
        /**
         * Choose a configuration from the list. Implementors typically
         * implement this method by calling
         * {@link EGL10#eglChooseConfig} and iterating through the results. Please consult the
         * EGL specification available from The Khronos Group to learn how to call eglChooseConfig.
         * @param egl the EGL10 for the current display.
         * @param display the current display.
         * @return the chosen configuration.
         */
        EGLConfig chooseConfig(EGL10 egl, EGLDisplay display);
    }

    private abstract class BaseConfigChooser implements EGLConfigChooser 
    {
        public BaseConfigChooser(int[] configSpec) 
        {
            mConfigSpec = filterConfigSpec(configSpec);
        }

        public EGLConfig chooseConfig(EGL10 egl, EGLDisplay display) 
        {
            int[] num_config = new int[1];
            if (!egl.eglChooseConfig(display, mConfigSpec, null, 0, num_config)) 
            {
                throw new IllegalArgumentException("eglChooseConfig failed");
            }

            int numConfigs = num_config[0];

            if (numConfigs <= 0) 
            {
                throw new IllegalArgumentException("No configs match configSpec");
            }

            EGLConfig[] configs = new EGLConfig[numConfigs];
            if (!egl.eglChooseConfig(display, mConfigSpec, configs, numConfigs, num_config)) 
            {
                throw new IllegalArgumentException("eglChooseConfig#2 failed");
            }
            EGLConfig config = chooseConfig(egl, display, configs);
            if (config == null) 
            {
                throw new IllegalArgumentException("No config chosen");
            }
            return config;
        }

        abstract EGLConfig chooseConfig(EGL10 egl, EGLDisplay display, EGLConfig[] configs);

        protected int[] mConfigSpec;

        private int[] filterConfigSpec(int[] configSpec) 
        {
            if (mEGLContextClientVersion != 2) 
            {
                return configSpec;
            }
            /* We know none of the subclasses define EGL_RENDERABLE_TYPE.
             * And we know the configSpec is well formed.
             */
            int len = configSpec.length;
            int[] newConfigSpec = new int[len + 2];
            System.arraycopy(configSpec, 0, newConfigSpec, 0, len-1);
            newConfigSpec[len-1] = EGL10.EGL_RENDERABLE_TYPE;
            newConfigSpec[len] = 4; /* EGL_OPENGL_ES2_BIT */
            newConfigSpec[len+1] = EGL10.EGL_NONE;
            return newConfigSpec;
        }
    }

    private class ComponentSizeChooser extends BaseConfigChooser 
    {
        public ComponentSizeChooser(int redSize, int greenSize, int blueSize, int alphaSize, int depthSize, int stencilSize) 
        {
            super(new int[] {
                    EGL10.EGL_RED_SIZE, redSize,
                    EGL10.EGL_GREEN_SIZE, greenSize,
                    EGL10.EGL_BLUE_SIZE, blueSize,
                    EGL10.EGL_ALPHA_SIZE, alphaSize,
                    EGL10.EGL_DEPTH_SIZE, depthSize,
                    EGL10.EGL_STENCIL_SIZE, stencilSize,
                    EGL10.EGL_NONE});
            mValue = new int[1];
            mRedSize = redSize;
            mGreenSize = greenSize;
            mBlueSize = blueSize;
            mAlphaSize = alphaSize;
            mDepthSize = depthSize;
            mStencilSize = stencilSize;
       }

        @Override
        public EGLConfig chooseConfig(EGL10 egl, EGLDisplay display, EGLConfig[] configs) 
        {
            for (EGLConfig config : configs) 
            {
                int d = findConfigAttrib(egl, display, config, EGL10.EGL_DEPTH_SIZE, 0);
                int s = findConfigAttrib(egl, display, config, EGL10.EGL_STENCIL_SIZE, 0);
                if ((d >= mDepthSize) && (s >= mStencilSize)) {
                    int r = findConfigAttrib(egl, display, config, EGL10.EGL_RED_SIZE, 0);
                    int g = findConfigAttrib(egl, display, config, EGL10.EGL_GREEN_SIZE, 0);
                    int b = findConfigAttrib(egl, display, config, EGL10.EGL_BLUE_SIZE, 0);
                    int a = findConfigAttrib(egl, display, config, EGL10.EGL_ALPHA_SIZE, 0);
                    if ((r == mRedSize) && (g == mGreenSize) && (b == mBlueSize) && (a == mAlphaSize)) 
                    {
                        return config;
                    }
                }
            }
            return null;
        }

        private int findConfigAttrib(EGL10 egl, EGLDisplay display, EGLConfig config, int attribute, int defaultValue) 
        {
            if (egl.eglGetConfigAttrib(display, config, attribute, mValue)) 
            {
                return mValue[0];
            }
            return defaultValue;
        }

        private int[] mValue;
        // Subclasses can adjust these values:
        protected int mRedSize;
        protected int mGreenSize;
        protected int mBlueSize;
        protected int mAlphaSize;
        protected int mDepthSize;
        protected int mStencilSize;
    }

    private class SimpleEGLConfigChooser extends ComponentSizeChooser
    {
        public SimpleEGLConfigChooser(boolean withDepthBuffer) 
        {
            super(8, 8, 8, 0, withDepthBuffer ? 16 : 0, 0);
        }
    }

    private static class EglHelper 
    {
        public EglHelper(WeakReference<GLTextureView> glSurfaceViewWeakRef)
        {
            mGLSurfaceViewWeakRef = glSurfaceViewWeakRef;
        }

        /**
         * Initialize EGL for a given configuration spec.
         * @param configSpec
         */
        public void start() 
        {
            if (LOG_EGL) 
            {
                Log.w("EglHelper", "start() tid=" + Thread.currentThread().getId());
            }
            /*
             * Get an EGL instance
             */
            mEgl = (EGL10) EGLContext.getEGL();

            /*
             * Get to the default display.
             */
            mEglDisplay = mEgl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);

            if (mEglDisplay == EGL10.EGL_NO_DISPLAY) 
            {
                throw new RuntimeException("eglGetDisplay failed");
            }

            /*
             * We can now initialize EGL for that display
             */
            int[] version = new int[2];
            if(!mEgl.eglInitialize(mEglDisplay, version)) 
            {
                throw new RuntimeException("eglInitialize failed");
            }
            GLTextureView view = mGLSurfaceViewWeakRef.get();
            if (view == null) 
            {
                mEglConfig = null;
                mEglContext = null;
            } 
            else 
            {
                mEglConfig = view.mEGLConfigChooser.chooseConfig(mEgl, mEglDisplay);
                /*
                * Create an EGL context. We want to do this as rarely as we can, because an
                * EGL context is a somewhat heavy object.
                */
                mEglContext = view.mEGLContextFactory.createContext(mEgl, mEglDisplay, mEglConfig);
            }
            if (mEglContext == null || mEglContext == EGL10.EGL_NO_CONTEXT) 
            {
                mEglContext = null;
                throwEglException("createContext");
            }
            if (LOG_EGL) 
            {
                Log.w("EglHelper", "createContext " + mEglContext + " tid=" + Thread.currentThread().getId());
            }
            mEglSurface = null;
        }

        /**
         * Create an egl surface for the current SurfaceHolder surface. If a surface
         * already exists, destroy it before creating the new surface.
         *
         * @return true if the surface was created successfully.
         */
        public boolean createSurface() 
        {
            if (LOG_EGL) 
            {
                Log.w("EglHelper", "createSurface()  tid=" + Thread.currentThread().getId());
            }
            /*
             * Check preconditions.
             */
            if (mEgl == null) 
            {
                throw new RuntimeException("egl not initialized");
            }
            if (mEglDisplay == null) 
            {
                throw new RuntimeException("eglDisplay not initialized");
            }
            if (mEglConfig == null) 
            {
                throw new RuntimeException("mEglConfig not initialized");
            }
            /*
             *  The window size has changed, so we need to create a new
             *  surface.
             */
            destroySurfaceImp();
            /*
             * Create an EGL surface we can render into.
             */
            GLTextureView view = mGLSurfaceViewWeakRef.get();
            if (view != null) 
            {
                mEglSurface = view.mEGLWindowSurfaceFactory.createWindowSurface(mEgl,mEglDisplay, mEglConfig, view.getSurfaceTexture());
            } 
            else 
            {
                mEglSurface = null;
            }
            if (mEglSurface == null || mEglSurface == EGL10.EGL_NO_SURFACE) 
            {
                int error = mEgl.eglGetError();
                if (error == EGL10.EGL_BAD_NATIVE_WINDOW) 
                {
                    Log.e("EglHelper", "createWindowSurface returned EGL_BAD_NATIVE_WINDOW.");
                }
                return false;
            }
            /*
             * Before we can issue GL commands, we need to make sure
             * the context is current and bound to a surface.
             */
            if (!mEgl.eglMakeCurrent(mEglDisplay, mEglSurface, mEglSurface, mEglContext)) 
            {
                /*
                 * Could not make the context current, probably because the underlying
                 * SurfaceView surface has been destroyed.
                 */
                logEglErrorAsWarning("EGLHelper", "eglMakeCurrent", mEgl.eglGetError());
                return false;
            }
            return true;
        }

        /**
         * Create a GL object for the current EGL context.
         * @return
         */
        GL createGL() 
        {
            GL gl = mEglContext.getGL();
            GLTextureView view = mGLSurfaceViewWeakRef.get();
            if (view != null)
            {
                if (view.mGLWrapper != null) 
                {
                    gl = view.mGLWrapper.wrap(gl);
                }

                if ((view.mDebugFlags & (DEBUG_CHECK_GL_ERROR | DEBUG_LOG_GL_CALLS)) != 0) 
                {
                    int configFlags = 0;
                    Writer log = null;
                    if ((view.mDebugFlags & DEBUG_CHECK_GL_ERROR) != 0) 
                    {
                        configFlags |= GLDebugHelper.CONFIG_CHECK_GL_ERROR;
                    }
                    if ((view.mDebugFlags & DEBUG_LOG_GL_CALLS) != 0) 
                    {
                        log = new LogWriter();
                    }
                    gl = GLDebugHelper.wrap(gl, configFlags, log);
                }
            }
            return gl;
        }

        /**
         * Display the current render surface.
         * @return the EGL error code from eglSwapBuffers.
         */
        public int swap() 
        {
            if (! mEgl.eglSwapBuffers(mEglDisplay, mEglSurface)) 
            {
                return mEgl.eglGetError();
            }
            return EGL10.EGL_SUCCESS;
        }

        public void destroySurface() 
        {
            if (LOG_EGL) 
            {
                Log.w("EglHelper", "destroySurface()  tid=" + Thread.currentThread().getId());
            }
            mGLSurfaceViewWeakRef.get().mRenderer.onSurfaceDestroyed();
            destroySurfaceImp();
        }

        private void destroySurfaceImp() 
        {
            if (mEglSurface != null && mEglSurface != EGL10.EGL_NO_SURFACE) 
            {
                mEgl.eglMakeCurrent(mEglDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
                GLTextureView view = mGLSurfaceViewWeakRef.get();
                if (view != null) 
                {
                    view.mEGLWindowSurfaceFactory.destroySurface(mEgl, mEglDisplay, mEglSurface);
                }
                mEglSurface = null;
            }
        }

        public void finish() 
        {
            if (LOG_EGL) 
            {
                Log.w("EglHelper", "finish() tid=" + Thread.currentThread().getId());
            }
            if (mEglContext != null) 
            {
                GLTextureView view = mGLSurfaceViewWeakRef.get();
                if (view != null) 
                {
                    view.mEGLContextFactory.destroyContext(mEgl, mEglDisplay, mEglContext);
                }
                mEglContext = null;
            }
            if (mEglDisplay != null) 
            {
                mEgl.eglTerminate(mEglDisplay);
                mEglDisplay = null;
            }
        }

        private void throwEglException(String function) 
        {
            throwEglException(function, mEgl.eglGetError());
        }

        public static void throwEglException(String function, int error) 
        {
            String message = formatEglError(function, error);
            if (LOG_THREADS) 
            {
                Log.e("EglHelper", "throwEglException tid=" + Thread.currentThread().getId() + " " + message);
            }
            throw new RuntimeException(message);
        }

        public static void logEglErrorAsWarning(String tag, String function, int error) 
        {
            Log.w(tag, formatEglError(function, error));
        }

        public static String formatEglError(String function, int error) 
        {
            return function + " failed: " + error;
        }

        private WeakReference<GLTextureView> mGLSurfaceViewWeakRef;
        EGL10 mEgl;
        EGLDisplay mEglDisplay;
        EGLSurface mEglSurface;
        EGLConfig mEglConfig;
        EGLContext mEglContext;

    }

    static class GLThread extends Thread 
    {
        GLThread(WeakReference<GLTextureView> glSurfaceViewWeakRef)
        {
            super();
            mWidth = 0;
            mHeight = 0;
            mRequestRender = true;
            mRenderMode = RENDERMODE_CONTINUOUSLY;
            mGLSurfaceViewWeakRef = glSurfaceViewWeakRef;
        }

        @Override
        public void run() 
        {
            setName("GLThread " + getId());
            if (LOG_THREADS)
            {
                Log.i("GLThread", "starting tid=" + getId());
            }
            try 
            {
                guardedRun();
            } 
            catch (InterruptedException e) 
            {
                // fall thru and exit normally
            } 
            finally 
            {
                sGLThreadManager.threadExiting(this);
            }
        }

        /*
         * This private method should only be called inside a
         * synchronized(sGLThreadManager) block.
         */
        private void stopEglSurfaceLocked() 
        {
            if (mHaveEglSurface) 
            {
                mHaveEglSurface = false;
                mEglHelper.destroySurface();
            }
        }

        /*
         * This private method should only be called inside a
         * synchronized(sGLThreadManager) block.
         */
        private void stopEglContextLocked() 
        {
            if (mHaveEglContext) 
            {
                mEglHelper.finish();
                mHaveEglContext = false;
                sGLThreadManager.releaseEglContextLocked(this);
            }
        }
        
        private void guardedRun() throws InterruptedException
        {
            mEglHelper = new EglHelper(mGLSurfaceViewWeakRef);
            mHaveEglContext = false;
            mHaveEglSurface = false;
            try 
            {
                GL10 gl = null;
                boolean createEglContext = false;
                boolean createEglSurface = false;
                boolean createGlInterface = false;
                boolean lostEglContext = false;
                boolean sizeChanged = false;
                boolean wantRenderNotification = false;
                boolean doRenderNotification = false;
                boolean askedToReleaseEglContext = false;
                int w = 0;
                int h = 0;
                Runnable event = null;

                while (true) 
                {
                    synchronized (sGLThreadManager) 
                    {
                        while (true) 
                        {
                            if (mShouldExit) 
                            {
                                return;
                            }

                            if (! mEventQueue.isEmpty()) 
                            {
                                event = mEventQueue.remove(0);
                                break;
                            }

                            // Update the pause state.
                            boolean pausing = false;
                            if (mPaused != mRequestPaused) 
                            {
                                pausing = mRequestPaused;
                                mPaused = mRequestPaused;
                                sGLThreadManager.notifyAll();
                                if (LOG_PAUSE_RESUME) 
                                {
                                    Log.i("GLThread", "mPaused is now " + mPaused + " tid=" + getId());
                                }
                            }

                            // Do we need to give up the EGL context?
                            if (mShouldReleaseEglContext) 
                            {
                                if (LOG_SURFACE)
                                {
                                    Log.i("GLThread", "releasing EGL context because asked to tid=" + getId());
                                }

                                stopEglSurfaceLocked();
                                stopEglContextLocked();
                                mShouldReleaseEglContext = false;
                                askedToReleaseEglContext = true;
                            }

                            // Have we lost the EGL context?
                            if (lostEglContext) 
                            {
                                stopEglSurfaceLocked();
                                stopEglContextLocked();
                                lostEglContext = false;
                            }

                            // When pausing, release the EGL surface:
                            if (pausing && mHaveEglSurface) 
                            {
                                if (LOG_SURFACE) 
                                {
                                    Log.i("GLThread", "releasing EGL surface because paused tid=" + getId());
                                }
                                stopEglSurfaceLocked();
                            }

                            // When pausing, optionally release the EGL Context:
                            if (pausing && mHaveEglContext)
                            {
                                GLTextureView view = mGLSurfaceViewWeakRef.get();
                                boolean preserveEglContextOnPause = view == null ? false : view.mPreserveEGLContextOnPause;
                                if (!preserveEglContextOnPause || sGLThreadManager.shouldReleaseEGLContextWhenPausing()) 
                                {
                                    stopEglContextLocked();
                                    if (LOG_SURFACE) 
                                    {
                                        Log.i("GLThread", "releasing EGL context because paused tid=" + getId());
                                    }
                                }
                            }

                            // When pausing, optionally terminate EGL:
                            if (pausing) 
                            {
                                if (sGLThreadManager.shouldTerminateEGLWhenPausing()) 
                                {
                                    mEglHelper.finish();
                                    if (LOG_SURFACE) 
                                    {
                                        Log.i("GLThread", "terminating EGL because paused tid=" + getId());
                                    }
                                }
                            }

                            // Have we lost the SurfaceView surface?
                            if ((! mHasSurface) && (! mWaitingForSurface)) 
                            {
                                if (LOG_SURFACE) 
                                {
                                    Log.i("GLThread", "noticed surfaceView surface lost tid=" + getId());
                                }
                                if (mHaveEglSurface)
                                {
                                    stopEglSurfaceLocked();
                                }
                                mWaitingForSurface = true;
                                mSurfaceIsBad = false;
                                sGLThreadManager.notifyAll();
                            }

                            // Have we acquired the surface view surface?
                            if (mHasSurface && mWaitingForSurface) 
                            {
                                if (LOG_SURFACE) 
                                {
                                    Log.i("GLThread", "noticed surfaceView surface acquired tid=" + getId());
                                }
                                mWaitingForSurface = false;
                                sGLThreadManager.notifyAll();
                            }

                            if (doRenderNotification) 
                            {
                                if (LOG_SURFACE) 
                                {
                                    Log.i("GLThread", "sending render notification tid=" + getId());
                                }
                                wantRenderNotification = false;
                                doRenderNotification = false;
                                mRenderComplete = true;
                                sGLThreadManager.notifyAll();
                            }

                            // Ready to draw?
                            if (readyToDraw()) 
                            {
                                // If we don't have an EGL context, try to acquire one.
                                if (! mHaveEglContext) 
                                {
                                    if (askedToReleaseEglContext) 
                                    {
                                        askedToReleaseEglContext = false;
                                    } 
                                    else if (sGLThreadManager.tryAcquireEglContextLocked(this)) 
                                    {
                                        try 
                                        {
                                            mEglHelper.start();
                                        } 
                                        catch (RuntimeException t) 
                                        {
                                            sGLThreadManager.releaseEglContextLocked(this);
                                            throw t;
                                        }
                                        mHaveEglContext = true;
                                        createEglContext = true;

                                        sGLThreadManager.notifyAll();
                                    }
                                }

                                if (mHaveEglContext && !mHaveEglSurface) 
                                {
                                    mHaveEglSurface = true;
                                    createEglSurface = true;
                                    createGlInterface = true;
                                    sizeChanged = true;
                                }

                                if (mHaveEglSurface) 
                                {
                                    if (mSizeChanged) 
                                    {
                                        sizeChanged = true;
                                        w = mWidth;
                                        h = mHeight;
                                        wantRenderNotification = true;
                                        if (LOG_SURFACE) 
                                        {
                                            Log.i("GLThread", "noticing that we want render notification tid=" + getId());
                                        }
                                        // Destroy and recreate the EGL surface.
                                        createEglSurface = true;
                                        mSizeChanged = false;
                                    }
                                    mRequestRender = false;
                                    sGLThreadManager.notifyAll();
                                    break;
                                }
                            }

                            // By design, this is the only place in a GLThread thread where we wait().
                            if (LOG_THREADS) 
                            {
                                Log.i("GLThread", "waiting tid=" + getId()
                                    + " mHaveEglContext: " + mHaveEglContext
                                    + " mHaveEglSurface: " + mHaveEglSurface
                                    + " mFinishedCreatingEglSurface: " + mFinishedCreatingEglSurface
                                    + " mPaused: " + mPaused
                                    + " mHasSurface: " + mHasSurface
                                    + " mSurfaceIsBad: " + mSurfaceIsBad
                                    + " mWaitingForSurface: " + mWaitingForSurface
                                    + " mWidth: " + mWidth
                                    + " mHeight: " + mHeight
                                    + " mRequestRender: " + mRequestRender
                                    + " mRenderMode: " + mRenderMode);
                            }
                            sGLThreadManager.wait();
                        }
                    } // end of synchronized(sGLThreadManager)

                    if (event != null) 
                    {
                        event.run();
                        event = null;
                        continue;
                    }

                    if (createEglSurface) 
                    {
                        if (LOG_SURFACE) 
                        {
                            Log.w("GLThread", "egl createSurface");
                        }
                        if (mEglHelper.createSurface()) 
                        {
                            synchronized(sGLThreadManager) 
                            {
                                mFinishedCreatingEglSurface = true;
                                sGLThreadManager.notifyAll();
                            }
                        } 
                        else 
                        {
                            synchronized(sGLThreadManager) 
                            {
                                mFinishedCreatingEglSurface = true;
                                mSurfaceIsBad = true;
                                sGLThreadManager.notifyAll();
                            }
                            continue;
                        }
                        createEglSurface = false;
                    }

                    if (createGlInterface) 
                    {
                        gl = (GL10) mEglHelper.createGL();

                        sGLThreadManager.checkGLDriver(gl);
                        createGlInterface = false;
                    }

                    if (createEglContext) 
                    {
                        if (LOG_RENDERER) 
                        {
                            Log.w("GLThread", "onSurfaceCreated");
                        }
                        GLTextureView view = mGLSurfaceViewWeakRef.get();
                        if (view != null)
                        {
                            view.mRenderer.onSurfaceCreated(gl, mEglHelper.mEglConfig);
                        }
                        createEglContext = false;
                    }

                    if (sizeChanged) 
                    {
                        if (LOG_RENDERER) 
                        {
                            Log.w("GLThread", "onSurfaceChanged(" + w + ", " + h + ")");
                        }
                        GLTextureView view = mGLSurfaceViewWeakRef.get();
                        if (view != null) 
                        {
                            view.mRenderer.onSurfaceChanged(gl, w, h);
                        }
                        sizeChanged = false;
                    }

                    if (LOG_RENDERER_DRAW_FRAME) 
                    {
                        Log.w("GLThread", "onDrawFrame tid=" + getId());
                    }
                    {
                        GLTextureView view = mGLSurfaceViewWeakRef.get();
                        if (view != null) 
                        {
                            view.mRenderer.onDrawFrame(gl);
                        }
                    }
                    int swapError = mEglHelper.swap();
                    switch (swapError) 
                    {
                        case EGL10.EGL_SUCCESS:
                            break;
                        case EGL11.EGL_CONTEXT_LOST:
                            if (LOG_SURFACE) 
                            {
                                Log.i("GLThread", "egl context lost tid=" + getId());
                            }
                            lostEglContext = true;
                            break;
                        default:
                            // Other errors typically mean that the current surface is bad,
                            // probably because the SurfaceView surface has been destroyed,
                            // but we haven't been notified yet.
                            // Log the error to help developers understand why com.pikkart.ar.rendering stopped.
                            EglHelper.logEglErrorAsWarning("GLThread", "eglSwapBuffers", swapError);

                            synchronized(sGLThreadManager) 
                            {
                                mSurfaceIsBad = true;
                                sGLThreadManager.notifyAll();
                            }
                            break;
                    }

                    if (wantRenderNotification) 
                    {
                        doRenderNotification = true;
                    }
                }

            } 
            finally 
            {
                /*
                 * clean-up everything...
                 */
                synchronized (sGLThreadManager) 
                {
                    stopEglSurfaceLocked();
                    stopEglContextLocked();
                }
            }
        }

        public boolean ableToDraw() 
        {
            return mHaveEglContext && mHaveEglSurface && readyToDraw();
        }

        private boolean readyToDraw() 
        {
            return (!mPaused) && mHasSurface && (!mSurfaceIsBad) && (mWidth > 0) && (mHeight > 0) && (mRequestRender || (mRenderMode == RENDERMODE_CONTINUOUSLY));
        }

        public void setRenderMode(int renderMode) 
        {
            if ( !((RENDERMODE_WHEN_DIRTY <= renderMode) && (renderMode <= RENDERMODE_CONTINUOUSLY)) ) 
            {
                throw new IllegalArgumentException("renderMode");
            }
            synchronized(sGLThreadManager) 
            {
                mRenderMode = renderMode;
                sGLThreadManager.notifyAll();
            }
        }

        public int getRenderMode() 
        {
            synchronized(sGLThreadManager) 
            {
                return mRenderMode;
            }
        }

        public void requestRender() 
        {
            synchronized(sGLThreadManager) 
            {
                mRequestRender = true;
                sGLThreadManager.notifyAll();
            }
        }

        public void surfaceCreated() 
        {
            synchronized(sGLThreadManager) 
            {
                if (LOG_THREADS) 
                {
                    Log.i("GLThread", "surfaceCreated tid=" + getId());
                }
                mHasSurface = true;
                mFinishedCreatingEglSurface = false;
                sGLThreadManager.notifyAll();
                while (mWaitingForSurface && !mFinishedCreatingEglSurface && !mExited) 
                {
                    try 
                    {
                        sGLThreadManager.wait();
                    } 
                    catch (InterruptedException e) 
                    {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        public void surfaceDestroyed()
        {
            synchronized(sGLThreadManager) 
            {
                if (LOG_THREADS)
                {
                    Log.i("GLThread", "surfaceDestroyed tid=" + getId());
                }
                mHasSurface = false;
                sGLThreadManager.notifyAll();
                while((!mWaitingForSurface) && (!mExited))
                {
                    try 
                    {
                        sGLThreadManager.wait();
                    } 
                    catch (InterruptedException e) 
                    {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        public void onPause() 
        {
            synchronized (sGLThreadManager)
            {
                if (LOG_PAUSE_RESUME)
                {
                    Log.i("GLThread", "onPause tid=" + getId());
                }

                mRequestPaused = true;
                sGLThreadManager.notifyAll();

                while ((! mExited) && (! mPaused))
                {
                    if (LOG_PAUSE_RESUME) 
                    {
                        Log.i("Main thread", "onPause waiting for mPaused.");
                    }
                    try 
                    {
                        sGLThreadManager.wait();
                    } catch (InterruptedException ex) 
                    {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        public void onResume() 
        {
            synchronized (sGLThreadManager) 
            {
                if (LOG_PAUSE_RESUME)
                {
                    Log.i("GLThread", "onResume tid=" + getId());
                }
                mRequestPaused = false;
                mRequestRender = true;
                mRenderComplete = false;
                sGLThreadManager.notifyAll();
                while ((! mExited) && mPaused && (!mRenderComplete)) 
                {
                    if (LOG_PAUSE_RESUME) 
                    {
                        Log.i("Main thread", "onResume waiting for !mPaused.");
                    }
                    try 
                    {
                        sGLThreadManager.wait();
                    } 
                    catch (InterruptedException ex) 
                    {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        public void onWindowResize(int w, int h) 
        {
            synchronized (sGLThreadManager) 
            {
                mWidth = w;
                mHeight = h;
                mSizeChanged = true;
                mRequestRender = true;
                mRenderComplete = false;
                sGLThreadManager.notifyAll();

                // Wait for thread to react to resize and render a frame
                while (! mExited && !mPaused && !mRenderComplete  && ableToDraw()) 
                {
                    if (LOG_SURFACE) 
                    {
                        Log.i("Main thread", "onWindowResize waiting for render complete from tid=" + getId());
                    }
                    try 
                    {
                        sGLThreadManager.wait();
                    } 
                    catch (InterruptedException ex) 
                    {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        public void requestExitAndWait() 
        {
            // don't call this from GLThread thread or it is a guaranteed deadlock!
            synchronized(sGLThreadManager) 
            {
                mShouldExit = true;
                sGLThreadManager.notifyAll();
                while (! mExited)
                {
                    try 
                    {
                        sGLThreadManager.wait();
                    } 
                    catch (InterruptedException ex) 
                    {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        public void requestReleaseEglContextLocked() 
        {
            mShouldReleaseEglContext = true;
            sGLThreadManager.notifyAll();
        }

        /**
         * Queue an "event" to be run on the GL com.pikkart.ar.rendering thread.
         * @param r the runnable to be run on the GL com.pikkart.ar.rendering thread.
         */
        public void queueEvent(Runnable r) 
        {
            if (r == null) 
            {
                throw new IllegalArgumentException("r must not be null");
            }
            synchronized(sGLThreadManager) 
            {
                mEventQueue.add(r);
                sGLThreadManager.notifyAll();
            }
        }

        // Once the thread is started, all accesses to the following member
        // variables are protected by the sGLThreadManager monitor
        private boolean mShouldExit;
        private boolean mExited;
        private boolean mRequestPaused;
        private boolean mPaused;
        private boolean mHasSurface;
        private boolean mSurfaceIsBad;
        private boolean mWaitingForSurface;
        private boolean mHaveEglContext;
        private boolean mHaveEglSurface;
        private boolean mFinishedCreatingEglSurface;
        private boolean mShouldReleaseEglContext;
        private int mWidth;
        private int mHeight;
        private int mRenderMode;
        private boolean mRequestRender;
        private boolean mRenderComplete;
        private ArrayList<Runnable> mEventQueue = new ArrayList<Runnable>();
        private boolean mSizeChanged = true;

        // End of member variables protected by the sGLThreadManager monitor.

        private EglHelper mEglHelper;

        /**
         * Set once at thread construction time, nulled out when the parent view is garbage
         * called. This weak reference allows the GLSurfaceView to be garbage collected while
         * the GLThread is still alive.
         */
        private WeakReference<GLTextureView> mGLSurfaceViewWeakRef;

    }

    static class LogWriter extends Writer 
    {
        @Override public void close()
        {
            flushBuilder();
        }
        @Override public void flush() 
        {
            flushBuilder();
        }
        @Override public void write(char[] buf, int offset, int count) 
        {
            for(int i = 0; i < count; i++) 
            {
                char c = buf[offset + i];
                if ( c == '\n') 
                {
                    flushBuilder();
                }
                else 
                {
                    mBuilder.append(c);
                }
            }
        }

        private void flushBuilder() 
        {
            if (mBuilder.length() > 0) 
            {
                Log.v("GLSurfaceView", mBuilder.toString());
                mBuilder.delete(0, mBuilder.length());
            }
        }

        private StringBuilder mBuilder = new StringBuilder();
    }


    private void checkRenderThreadState() 
    {
        if (mGLThread != null) 
        {
            throw new IllegalStateException("setRenderer has already been called for this instance.");
        }
    }

    private static class GLThreadManager
    {
        private static String TAG = "GLThreadManager";

        public synchronized void threadExiting(GLThread thread) 
        {
            if (LOG_THREADS) 
            {
                Log.i("GLThread", "exiting tid=" +  thread.getId());
            }
            thread.mExited = true;
            if (mEglOwner == thread) 
            {
                mEglOwner = null;
            }
            notifyAll();
        }

        /*
         * Tries once to acquire the right to use an EGL
         * context. Does not block. Requires that we are already
         * in the sGLThreadManager monitor when this is called.
         *
         * @return true if the right to use an EGL context was acquired.
         */
        public boolean tryAcquireEglContextLocked(GLThread thread) 
        {
            if (mEglOwner == thread || mEglOwner == null) 
            {
                mEglOwner = thread;
                notifyAll();
                return true;
            }
            checkGLESVersion();
            if (mMultipleGLESContextsAllowed) 
            {
                return true;
            }
            // Notify the owning thread that it should release the context.
            if (mEglOwner != null)
            {
                mEglOwner.requestReleaseEglContextLocked();
            }
            return false;
        }

        /*
         * Releases the EGL context. Requires that we are already in the
         * sGLThreadManager monitor when this is called.
         */
        public void releaseEglContextLocked(GLThread thread) 
        {
            if (mEglOwner == thread) 
            {
                mEglOwner = null;
            }
            notifyAll();
        }

        public synchronized boolean shouldReleaseEGLContextWhenPausing() 
        {
            // Release the EGL context when pausing even if
            // the hardware supports multiple EGL contexts.
            // Otherwise the device could run out of EGL contexts.
            return mLimitedGLESContexts;
        }

        public synchronized boolean shouldTerminateEGLWhenPausing() 
        {
            checkGLESVersion();
            return !mMultipleGLESContextsAllowed;
        }

        public synchronized void checkGLDriver(GL10 gl) 
        {
            if (! mGLESDriverCheckComplete) 
            {
                checkGLESVersion();
                String renderer = gl.glGetString(GL10.GL_RENDERER);
                if (mGLESVersion < kGLES_20) 
                {
                    mMultipleGLESContextsAllowed = ! renderer.startsWith(kMSM7K_RENDERER_PREFIX);
                    notifyAll();
                }
                mLimitedGLESContexts = !mMultipleGLESContextsAllowed;
                if (LOG_SURFACE) 
                {
                    Log.w(TAG, "checkGLDriver renderer = \"" + renderer + "\" multipleContextsAllowed = " + mMultipleGLESContextsAllowed + " mLimitedGLESContexts = " + mLimitedGLESContexts);
                }
                mGLESDriverCheckComplete = true;
            }
        }

        private void checkGLESVersion() 
        {
            if (! mGLESVersionCheckComplete) 
            {
                mMultipleGLESContextsAllowed = true;
                mGLESVersionCheckComplete = true;
            }
        }

        /**
         * This check was required for some pre-Android-3.0 hardware. Android 3.0 provides
         * support for hardware-accelerated views, therefore multiple EGL contexts are
         * supported on all Android 3.0+ EGL drivers.
         */
        private boolean mGLESVersionCheckComplete;
        private int mGLESVersion;
        private boolean mGLESDriverCheckComplete;
        private boolean mMultipleGLESContextsAllowed;
        private boolean mLimitedGLESContexts;
        private static final int kGLES_20 = 0x20000;
        private static final String kMSM7K_RENDERER_PREFIX =
            "Q3Dimension MSM7500 ";
        private GLThread mEglOwner;
    }

    private static final GLThreadManager sGLThreadManager = new GLThreadManager();

    private final WeakReference<GLTextureView> mThisWeakRef = new WeakReference<GLTextureView>(this);
    private GLThread mGLThread;
    private Renderer mRenderer;
    private boolean mDetached;
    private EGLConfigChooser mEGLConfigChooser;
    private EGLContextFactory mEGLContextFactory;
    private EGLWindowSurfaceFactory mEGLWindowSurfaceFactory;
    private GLWrapper mGLWrapper;
    private int mDebugFlags;
    private int mEGLContextClientVersion;
    private boolean mPreserveEGLContextOnPause;

	@Override
	public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) 
	{
		onSurfaceTextureSizeChanged(getSurfaceTexture(), right - left, bottom - top);
	}
}
