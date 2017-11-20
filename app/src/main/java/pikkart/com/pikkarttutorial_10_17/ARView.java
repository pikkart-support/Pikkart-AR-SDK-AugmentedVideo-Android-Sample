package pikkart.com.pikkarttutorial_10_17;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import java.lang.reflect.Method;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;

public class ARView extends GLTextureView
{
    private Context _context;
    //our renderer implementation
    private ARRenderer _renderer;

    /* Called when device configuration has changed */
    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        //here we force our layout to fill the parent
        if (getParent() instanceof FrameLayout) {
            setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER));
        }
        else if (getParent() instanceof RelativeLayout) {
            setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT,
                    RelativeLayout.LayoutParams.MATCH_PARENT));
        }
    }

    /* Called when layout is created or modified (i.e. because of device rotation changes etc.) */
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if(!changed) return;
        int angle = 0;
        //here we compute a normalized orientation independent of the device class (tablet or phone)
        //so that an angle of 0 is always landscape, 90 always portrait etc.
        Display display = ((WindowManager) _context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        int rotation = display.getRotation();
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            switch (rotation) {
                case 0:
                    angle = 0;
                    break;
                case 1:
                    angle = 0;
                    break;
                case 2:
                    angle = 180;
                    break;
                case 3:
                    angle = 180;
                    break;
                default:
                    break;
            }
        } else {
            switch (rotation) {
                case 0:
                    angle = 90;
                    break;
                case 1:
                    angle = 270;
                    break;
                case 2:
                    angle = 270;
                    break;
                case 3:
                    angle = 90;
                    break;
                default:
                    break;
            }
        }

        int realWidth;
        int realHeight;
        if (Build.VERSION.SDK_INT >= 17) {
            //new pleasant way to get real metrics
            DisplayMetrics realMetrics = new DisplayMetrics();
            display.getRealMetrics(realMetrics);
            realWidth = realMetrics.widthPixels;
            realHeight = realMetrics.heightPixels;

        } else if (Build.VERSION.SDK_INT >= 14) {
            //reflection for this weird in-between time
            try {
                Method mGetRawH = Display.class.getMethod("getRawHeight");
                Method mGetRawW = Display.class.getMethod("getRawWidth");
                realWidth = (Integer) mGetRawW.invoke(display);
                realHeight = (Integer) mGetRawH.invoke(display);
            } catch (Exception e) {
                //this may not be 100% accurate, but it's all we've got
                realWidth = display.getWidth();
                realHeight = display.getHeight();
            }
        } else {
            //This should be close, as lower API devices should not have window navigation bars
            realWidth = display.getWidth();
            realHeight = display.getHeight();
        }
        _renderer.UpdateViewport(right-left, bottom-top, angle);
    }

    /* Constructor. */
    public ARView(Context context) {
        super(context);
        _context = context;
        init();
        _renderer = new ARRenderer(this._context);
        setRenderer(_renderer);
        ((ARRenderer)_renderer).IsActive = true;
        setOpaque(true);
    }

    /* Initialization. */
    public void init() {
        setEGLContextFactory(new ContextFactory());
        setEGLConfigChooser(new ConfigChooser(8, 8, 8, 0, 16, 0));
    }

    /* Checks the OpenGL error.*/
    private static void checkEglError(String prompt, EGL10 egl) {
        int error;
        while ((error = egl.eglGetError()) != EGL10.EGL_SUCCESS) {
            Log.e("PikkartCore3", String.format("%s: EGL error: 0x%x", prompt, error));
        }
    }

    /* A private class that manages the creation of OpenGL contexts. Pretty standard stuff*/
    private static class ContextFactory implements EGLContextFactory {
        private static int EGL_CONTEXT_CLIENT_VERSION = 0x3098;

        public EGLContext createContext(EGL10 egl, EGLDisplay display, EGLConfig eglConfig) {
            EGLContext context;
            //Log.i("PikkartCore3","Creating OpenGL ES 2.0 context");
            checkEglError("Before eglCreateContext", egl);
            int[] attrib_list_gl20 = {EGL_CONTEXT_CLIENT_VERSION, 2, EGL10.EGL_NONE};
            context = egl.eglCreateContext(display, eglConfig, EGL10.EGL_NO_CONTEXT, attrib_list_gl20);
            checkEglError("After eglCreateContext", egl);
            return context;
        }

        public void destroyContext(EGL10 egl, EGLDisplay display, EGLContext context) {
            egl.eglDestroyContext(display, context);
        }
    }

    /* A private class that manages the the config chooser. Pretty standard stuff */
    private static class ConfigChooser implements EGLConfigChooser {
        public ConfigChooser(int r, int g, int b, int a, int depth, int stencil) {
            mRedSize = r;
            mGreenSize = g;
            mBlueSize = b;
            mAlphaSize = a;
            mDepthSize = depth;
            mStencilSize = stencil;
        }

        private EGLConfig getMatchingConfig(EGL10 egl, EGLDisplay display, int[] configAttribs) {
            // Get the number of minimally matching EGL configurations
            int[] num_config = new int[1];
            egl.eglChooseConfig(display, configAttribs, null, 0, num_config);
            int numConfigs = num_config[0];
            if (numConfigs <= 0)
                throw new IllegalArgumentException("No matching EGL configs");
            // Allocate then read the array of minimally matching EGL configs
            EGLConfig[] configs = new EGLConfig[numConfigs];
            egl.eglChooseConfig(display, configAttribs, configs, numConfigs, num_config);
            // Now return the "best" one
            return chooseConfig(egl, display, configs);
        }

        public EGLConfig chooseConfig(EGL10 egl, EGLDisplay display) {
            // This EGL config specification is used to specify 2.0 com.pikkart.ar.rendering. We use a minimum size of 4 bits for
            // red/green/blue, but will perform actual matching in chooseConfig() below.
            final int EGL_OPENGL_ES2_BIT = 0x0004;
            final int[] s_configAttribs_gl20 = {EGL10.EGL_RED_SIZE, 4, EGL10.EGL_GREEN_SIZE, 4, EGL10.EGL_BLUE_SIZE, 4,
                    EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT, EGL10.EGL_NONE};
            return getMatchingConfig(egl, display, s_configAttribs_gl20);
        }

        public EGLConfig chooseConfig(EGL10 egl, EGLDisplay display, EGLConfig[] configs) {
            boolean bFoundDepth = false;
            for (EGLConfig config : configs) {
                int d = findConfigAttrib(egl, display, config, EGL10.EGL_DEPTH_SIZE, 0);
                if (d == mDepthSize) bFoundDepth = true;
            }
            if (bFoundDepth == false) mDepthSize = 16; //min value
            for (EGLConfig config : configs) {
                int d = findConfigAttrib(egl, display, config, EGL10.EGL_DEPTH_SIZE, 0);
                int s = findConfigAttrib(egl, display, config, EGL10.EGL_STENCIL_SIZE, 0);
                // We need at least mDepthSize and mStencilSize bits
                if (d < mDepthSize || s < mStencilSize)
                    continue;
                // We want an *exact* match for red/green/blue/alpha
                int r = findConfigAttrib(egl, display, config, EGL10.EGL_RED_SIZE, 0);
                int g = findConfigAttrib(egl, display, config, EGL10.EGL_GREEN_SIZE, 0);
                int b = findConfigAttrib(egl, display, config, EGL10.EGL_BLUE_SIZE, 0);
                int a = findConfigAttrib(egl, display, config, EGL10.EGL_ALPHA_SIZE, 0);

                if (r == mRedSize && g == mGreenSize && b == mBlueSize && a == mAlphaSize)
                    return config;
            }

            return null;
        }

        private int findConfigAttrib(EGL10 egl, EGLDisplay display, EGLConfig config, int attribute, int defaultValue) {
            if (egl.eglGetConfigAttrib(display, config, attribute, mValue))
                return mValue[0];
            return defaultValue;
        }

        // Subclasses can adjust these values:
        protected int mRedSize;
        protected int mGreenSize;
        protected int mBlueSize;
        protected int mAlphaSize;
        protected int mDepthSize;
        protected int mStencilSize;
        private int[] mValue = new int[1];
    }

    @Override
    public boolean onTouchEvent(MotionEvent e){
        if(e.getAction()== MotionEvent.ACTION_UP)
            _renderer.playOrPauseVideo();
        return true;
    }

    @Override
    public void onPause() {
        super.onPause();
        _renderer.pauseVideo();
    }
}