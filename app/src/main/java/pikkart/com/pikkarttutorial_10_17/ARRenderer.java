package pikkart.com.pikkarttutorial_10_17;

import android.app.Activity;
import android.content.Context;
import com.pikkart.ar.recognition.RecognitionFragment;
import com.pikkart.ar.recognition.items.Marker;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class ARRenderer implements GLTextureView.Renderer {
    public boolean IsActive = false;
    //the rendering viewport dimensions
    private int ViewportWidth;
    private int ViewportHeight;
    //normalized screen orientation (0=landscale, 90=portrait, 180=inverse landscale, 270=inverse portrait)
    private int Angle;
    //
    private Context context;
    //the 3d object we will render on the marker
    private Mesh monkeyMesh = null;
    private VideoMesh videoMesh = null;

    /* Constructor. */
    public ARRenderer(Context con) {
        context = con;
    }

    /** Called when the surface is created or recreated.
     * Reinitialize OpenGL related stuff here*/
    public void onSurfaceCreated(GL10 gl, EGLConfig config)  {
        gl.glClearColor(1.0f, 1.0f, 1.0f, 1.0f);
        //Here we create the 3D object and initialize textures, shaders, etc.
        monkeyMesh=new Mesh();
        monkeyMesh.InitMesh(context.getAssets(),"media/monkey.json", "media/texture.png");

        videoMesh = new VideoMesh((Activity)context);
        videoMesh.InitMesh(context.getAssets(), "media/pikkart_video.mp4", "media/pikkart_keyframe.png", 0, false, null);
    }

    /** Called when the surface changed size. */
    public void onSurfaceChanged(GL10 gl, int width, int height) {
    }

    /** Called when the surface is destroyed. */
    public void onSurfaceDestroyed() {
    }

    /** Here we compute the model-view-projection matrix for OpenGL rendering
     * from the model-view and projection matrix computed by Pikkart's AR SDK.
     * the projection matrix is rotated accordingly to the screen orientation */
    public boolean computeModelViewProjectionMatrix(float[] mvpMatrix) {
        RenderUtils.matrix44Identity(mvpMatrix);

        float w = (float)640;
        float h = (float)480;

        float ar = (float)ViewportHeight / (float)ViewportWidth;
        if (ViewportHeight > ViewportWidth) ar = 1.0f / ar;
        float h1 = h, w1 = w;
        if (ar < h/w)
            h1 = w * ar;
        else
            w1 = h / ar;

        float a = 0.f, b = 0.f;
        switch (Angle) {
            case 0: a = 1.f; b = 0.f;
                break;
            case 90: a = 0.f; b = 1.f;
                break;
            case 180: a = -1.f; b = 0.f;
                break;
            case 270: a = 0.f; b = -1.f;
                break;
            default: break;
        }

        float[] angleMatrix = new float[16];

        angleMatrix[0] = a; angleMatrix[1] = b; angleMatrix[2]=0.0f; angleMatrix[3] = 0.0f;
        angleMatrix[4] = -b; angleMatrix[5] = a; angleMatrix[6] = 0.0f; angleMatrix[7] = 0.0f;
        angleMatrix[8] = 0.0f; angleMatrix[9] = 0.0f; angleMatrix[10] = 1.0f; angleMatrix[11] = 0.0f;
        angleMatrix[12] = 0.0f; angleMatrix[13] = 0.0f; angleMatrix[14] = 0.0f; angleMatrix[15] = 1.0f;

        float[] projectionMatrix = RecognitionFragment.getCurrentProjectionMatrix().clone();
        projectionMatrix[5] = projectionMatrix[5] * (h / h1);

        float[] correctedProjection = new float[16];

        RenderUtils.matrixMultiply(4,4,angleMatrix,4,4,projectionMatrix,correctedProjection);

        if( RecognitionFragment.isTracking() ) {
            float[] modelviewMatrix = RecognitionFragment.getCurrentModelViewMatrix();
            float[] temp_mvp = new float[16];
            RenderUtils.matrixMultiply(4,4,correctedProjection,4,4,modelviewMatrix, temp_mvp);
            RenderUtils.matrix44Transpose(temp_mvp,mvpMatrix);
            return true;
        }
        return false;
    }

    /** Called to draw the current frame. */
    public void onDrawFrame(GL10 gl) {
        if (!IsActive) return;

        gl.glClear(GL10.GL_COLOR_BUFFER_BIT | GL10.GL_DEPTH_BUFFER_BIT);

        // Call our native function to render camera content
        RecognitionFragment.renderCamera(ViewportWidth, ViewportHeight, Angle);

        if(RecognitionFragment.isTracking()) {
            Marker currentMarker = RecognitionFragment.getCurrentMarker();
            //Here we decide which 3d object to draw and we draw it
            if(currentMarker.getId().compareTo("3_522")==0){
                float[] mvMatrix = new float[16];
                float[] pMatrix = new float[16];
                if (computeModelViewProjectionMatrix(mvMatrix, pMatrix)) {
                    videoMesh.DrawMesh(mvMatrix, pMatrix);
                    RenderUtils.checkGLError("completed video mesh Render");
                }
            }
            else {
                float[] mvpMatrix = new float[16];
                if (computeModelViewProjectionMatrix(mvpMatrix)) {
                    monkeyMesh.DrawMesh(mvpMatrix);
                    RenderUtils.checkGLError("completed Monkey head Render");
                }
            }
        }
        //if the video is still playing and we have lost tracking, we still draw the video,
        //but in a fixed frontal position
        if(!RecognitionFragment.isTracking() && videoMesh.isPlaying()) {
            float[] mvMatrix = new float[16];
            float[] pMatrix = new float[16];
            computeProjectionMatrix(pMatrix);

            if(Angle==0) {
                mvMatrix[0] = 1.0f; mvMatrix[1] = 0.0f; mvMatrix[2] = 0.0f; mvMatrix[3] = -0.5f;
                mvMatrix[4] = 0.0f; mvMatrix[5] = -1.0f; mvMatrix[6] = 0.0f; mvMatrix[7] = 0.4f;
                mvMatrix[8] = 0.0f; mvMatrix[9] = 0.0f; mvMatrix[10] = -1.0f; mvMatrix[11] = -1.3f;
                mvMatrix[12] = 0.0f; mvMatrix[13] = 0.0f; mvMatrix[14] = 0.0f; mvMatrix[15] = 1.0f;
            }
            else if(Angle==90) {
                mvMatrix[0] = 0.0f; mvMatrix[1] = 1.0f; mvMatrix[2] = 0.0f; mvMatrix[3] = -0.5f;
                mvMatrix[4] = 1.0f; mvMatrix[5] = 0.0f; mvMatrix[6] = 0.0f; mvMatrix[7] = -0.5f;
                mvMatrix[8] = 0.0f; mvMatrix[9] = 0.0f; mvMatrix[10] = -1.0f; mvMatrix[11] = -1.3f;
                mvMatrix[12] = 0.0f; mvMatrix[13] = 0.0f; mvMatrix[14] = 0.0f; mvMatrix[15] = 1.0f;
            }
            else if(Angle==180) {
                mvMatrix[0] = -1.0f; mvMatrix[1] = 0.0f; mvMatrix[2] = 0.0f; mvMatrix[3] = 0.5f;
                mvMatrix[4] = 0.0f; mvMatrix[5] = 1.0f; mvMatrix[6] = 0.0f; mvMatrix[7] = -0.4f;
                mvMatrix[8] = 0.0f; mvMatrix[9] = 0.0f; mvMatrix[10] = -1.0f; mvMatrix[11] = -1.3f;
                mvMatrix[12] = 0.0f; mvMatrix[13] = 0.0f; mvMatrix[14] = 0.0f; mvMatrix[15] = 1.0f;
            }
            else if(Angle==270) {
                mvMatrix[0] = 0.0f; mvMatrix[1] = -1.0f; mvMatrix[2] = 0.0f; mvMatrix[3] = 0.5f;
                mvMatrix[4] = -1.0f; mvMatrix[5] = 0.0f; mvMatrix[6] = 0.0f; mvMatrix[7] = 0.5f;
                mvMatrix[8] = 0.0f; mvMatrix[9] = 0.0f; mvMatrix[10] = -1.0f; mvMatrix[11] = -1.3f;
                mvMatrix[12] = 0.0f; mvMatrix[13] = 0.0f; mvMatrix[14] = 0.0f; mvMatrix[15] = 1.0f;
            }

            videoMesh.DrawMesh(mvMatrix, pMatrix);
            RenderUtils.checkGLError("completed video mesh Render");
        }

        gl.glFinish();
    }

    /* this will be called by our GLTextureView-derived class to update screen sizes and orientation */
    public void UpdateViewport(int viewportWidth, int viewportHeight, int angle) {
        ViewportWidth = viewportWidth;
        ViewportHeight = viewportHeight;
        Angle = angle;
    }

    public boolean computeModelViewProjectionMatrix(float[] mvMatrix, float[] pMatrix) {
        RenderUtils.matrix44Identity(mvMatrix);
        RenderUtils.matrix44Identity(pMatrix);

        float w = (float)640;
        float h = (float)480;
        float ar = (float)ViewportHeight / (float)ViewportWidth;
        if (ViewportHeight > ViewportWidth) ar = 1.0f / ar;
        float h1 = h, w1 = w;
        if (ar < h/w)
            h1 = w * ar;
        else
            w1 = h / ar;

        float a = 0.f, b = 0.f;
        switch (Angle) {
            case 0: a = 1.f; b = 0.f;
                break;
            case 90: a = 0.f; b = 1.f;
                break;
            case 180: a = -1.f; b = 0.f;
                break;
            case 270: a = 0.f; b = -1.f;
                break;
            default: break;
        }
        float[] angleMatrix = new float[16];
        angleMatrix[0] = a; angleMatrix[1] = b; angleMatrix[2]=0.0f; angleMatrix[3] = 0.0f;
        angleMatrix[4] = -b; angleMatrix[5] = a; angleMatrix[6] = 0.0f; angleMatrix[7] = 0.0f;
        angleMatrix[8] = 0.0f; angleMatrix[9] = 0.0f; angleMatrix[10] = 1.0f; angleMatrix[11] = 0.0f;
        angleMatrix[12] = 0.0f; angleMatrix[13] = 0.0f; angleMatrix[14] = 0.0f; angleMatrix[15] = 1.0f;

        float[] projectionMatrix = RecognitionFragment.getCurrentProjectionMatrix().clone();
        projectionMatrix[5] = projectionMatrix[5] * (h / h1);

        RenderUtils.matrixMultiply(4,4,angleMatrix,4,4,projectionMatrix,pMatrix);

        if( RecognitionFragment.isTracking() ) {
            float[] tMatrix = RecognitionFragment.getCurrentModelViewMatrix();
            mvMatrix[0]=tMatrix[0]; mvMatrix[1]=tMatrix[1]; mvMatrix[2]=tMatrix[2]; mvMatrix[3]=tMatrix[3];
            mvMatrix[4]=tMatrix[4]; mvMatrix[5]=tMatrix[5]; mvMatrix[6]=tMatrix[6]; mvMatrix[7]=tMatrix[7];
            mvMatrix[8]=tMatrix[8]; mvMatrix[9]=tMatrix[9]; mvMatrix[10]=tMatrix[10]; mvMatrix[11]=tMatrix[11];
            mvMatrix[12]=tMatrix[12]; mvMatrix[13]=tMatrix[13]; mvMatrix[14]=tMatrix[14]; mvMatrix[15]=tMatrix[15];
            return true;
        }
        return false;
    }

    public boolean computeProjectionMatrix(float[] pMatrix) {
        RenderUtils.matrix44Identity(pMatrix);

        float w = (float)640;
        float h = (float)480;

        float ar = (float)ViewportHeight / (float)ViewportWidth;
        if (ViewportHeight > ViewportWidth) ar = 1.0f / ar;
        float h1 = h, w1 = w;
        if (ar < h/w)
            h1 = w * ar;
        else
            w1 = h / ar;

        float a = 0.f, b = 0.f;
        switch (Angle) {
            case 0: a = 1.f; b = 0.f;
                break;
            case 90: a = 0.f; b = 1.f;
                break;
            case 180: a = -1.f; b = 0.f;
                break;
            case 270: a = 0.f; b = -1.f;
                break;
            default: break;
        }

        float[] angleMatrix = new float[16];

        angleMatrix[0] = a; angleMatrix[1] = b; angleMatrix[2]=0.0f; angleMatrix[3] = 0.0f;
        angleMatrix[4] = -b; angleMatrix[5] = a; angleMatrix[6] = 0.0f; angleMatrix[7] = 0.0f;
        angleMatrix[8] = 0.0f; angleMatrix[9] = 0.0f; angleMatrix[10] = 1.0f; angleMatrix[11] = 0.0f;
        angleMatrix[12] = 0.0f; angleMatrix[13] = 0.0f; angleMatrix[14] = 0.0f; angleMatrix[15] = 1.0f;

        float[] projectionMatrix = RecognitionFragment.getCurrentProjectionMatrix().clone();
        projectionMatrix[5] = projectionMatrix[5] * (h / h1);

        RenderUtils.matrixMultiply(4,4,angleMatrix,4,4,projectionMatrix,pMatrix);

        return true;
    }
    public void playOrPauseVideo() {
        if(videoMesh!=null) videoMesh.playOrPauseVideo();
    }

    public void pauseVideo() { if(videoMesh!=null) videoMesh.pauseVideo(); }
}