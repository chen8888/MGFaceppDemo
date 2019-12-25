package com.facepp.demo;

import android.app.Activity;
import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PreviewCallback;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLSurfaceView.Renderer;
import android.opengl.Matrix;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.RelativeLayout;

import com.facepp.demo.bean.FaceActionInfo;
import com.facepp.demo.facecompare.FaceCompareManager;
import com.facepp.demo.util.CameraMatrix;
import com.facepp.demo.util.ConUtil;
import com.facepp.demo.util.ICamera;
import com.facepp.demo.util.OpenGLDrawRect;
import com.facepp.demo.util.OpenGLUtil;
import com.facepp.demo.util.PointsMatrix;
import com.facepp.demo.util.Screen;
import com.facepp.demo.util.SensorEventUtil;
import com.megvii.facepp.sdk.Facepp;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import static android.opengl.GLSurfaceView.RENDERMODE_WHEN_DIRTY;

public class OpenglActivity extends Activity
        implements PreviewCallback, Renderer, SurfaceTexture.OnFrameAvailableListener
{
    private static final String TAG = "OpenglActivity";

    private boolean isBackCamera;
    private String trackModel;
    private GLSurfaceView mGlSurfaceView;
    private ICamera mICamera;
    private Camera mCamera;
    private HandlerThread mHandlerThread = new HandlerThread("facepp");
    private Handler mHandler;
    private Facepp facepp;
    private HashMap<String, Integer> resolutionMap;
    private SensorEventUtil sensorUtil;

    private FaceActionInfo faceActionInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Screen.initialize(this);
        setContentView(R.layout.activity_opengl);
        init();

        FaceCompareManager.instance().loadFeature(this);
        ConUtil.toggleHideyBar(this);

        DisplayMetrics outMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(outMetrics);

    }

    private void init() {

        faceActionInfo = (FaceActionInfo) getIntent().getSerializableExtra("FaceAction");

        isBackCamera = faceActionInfo.isBackCamera;
        trackModel = faceActionInfo.trackModel;
        resolutionMap = faceActionInfo.resolutionMap;

        facepp = new Facepp();

        sensorUtil = new SensorEventUtil(this);

        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());

        mGlSurfaceView = (GLSurfaceView) findViewById(R.id.opengl_layout_surfaceview);
        mGlSurfaceView.setEGLContextClientVersion(2);// 创建一个OpenGL ES 2.0
        // context
        mGlSurfaceView.setRenderer(this);// 设置渲染器进入gl
        // RENDERMODE_CONTINUOUSLY不停渲染
        // RENDERMODE_WHEN_DIRTY懒惰渲染，需要手动调用 glSurfaceView.requestRender() 才会进行更新
        mGlSurfaceView.setRenderMode(RENDERMODE_WHEN_DIRTY);// 设置渲染器模式
        mGlSurfaceView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                autoFocus();
            }
        });

        mICamera = new ICamera();
    }

    private void autoFocus() {
        if (mCamera != null && isBackCamera) {
            mCamera.cancelAutoFocus();
            Parameters parameters = mCamera.getParameters();
            parameters.setFocusMode(Parameters.FOCUS_MODE_AUTO);
            mCamera.setParameters(parameters);
            mCamera.autoFocus(null);
        }
    }

    private int Angle;

    @Override
    protected void onResume() {
        super.onResume();
        ConUtil.acquireWakeLock(this);
        mCamera = mICamera.openCamera(isBackCamera, this, resolutionMap);
        if (mCamera != null) {
            Angle = 360 - mICamera.Angle;
            if (isBackCamera)
            {
                Angle = mICamera.Angle;
            }

            RelativeLayout.LayoutParams layout_params = mICamera.getLayoutParam();
            mGlSurfaceView.setLayoutParams(layout_params);

            int width = mICamera.cameraWidth;
            int height = mICamera.cameraHeight;

            int left = 0;
            int top = 0;
            int right = width;
            int bottom = height;

            String errorCode = facepp.init(this, ConUtil.getFileContent(this, R.raw.megviifacepp_0_5_2_model), 1);

            //sdk内部其他api已经处理好，可以不判断
            if (errorCode!=null){
                Intent intent=new Intent();
                intent.putExtra("errorcode",errorCode);
                setResult(101,intent);
                finish();
                return;
            }

            Facepp.FaceppConfig faceppConfig = facepp.getFaceppConfig();
            faceppConfig.interval = 25;
            faceppConfig.minFaceSize = 200;
            faceppConfig.roi_left = left;
            faceppConfig.roi_top = top;
            faceppConfig.roi_right = right;
            faceppConfig.roi_bottom = bottom;
            String[] array = getResources().getStringArray(R.array.trackig_mode_array);
            if (trackModel.equals(array[0]))
            {
                faceppConfig.detectionMode = Facepp.FaceppConfig.DETECTION_MODE_TRACKING_FAST;
            }
            else if (trackModel.equals(array[1]))
            {
                faceppConfig.detectionMode = Facepp.FaceppConfig.DETECTION_MODE_TRACKING_ROBUST;
            }
            else if (trackModel.equals(array[2]))
            {
                faceppConfig.detectionMode = Facepp.FaceppConfig.MG_FPP_DETECTIONMODE_TRACK_RECT;
            }


            facepp.setFaceppConfig(faceppConfig);
        }
        else
        {
            Log.e(TAG, getString(R.string.camera_error));
        }
    }

    private void setConfig(int rotation) {
        Facepp.FaceppConfig faceppConfig = facepp.getFaceppConfig();
        if (faceppConfig.rotation != rotation) {
            faceppConfig.rotation = rotation;
            facepp.setFaceppConfig(faceppConfig);
        }
    }

    float confidence;
    float pitch, yaw, roll;
    int rotation = Angle;


    @Override
    public void onPreviewFrame(final byte[] imgData, final Camera camera) {

        //检测操作放到主线程，防止贴点延迟
        int width = mICamera.cameraWidth;
        int height = mICamera.cameraHeight;

        final int orientation = sensorUtil.orientation;
        if (orientation == 0)
        {
            rotation = Angle;
        }
        else if (orientation == 1)
        {
            rotation = 0;
        }
        else if (orientation == 2)
        {
            rotation = 180;
        }
        else if (orientation == 3)
        {
            rotation = 360 - Angle;
        }


        setConfig(rotation);

        final Facepp.Face[] faces = facepp.detect(imgData, width, height, Facepp.IMAGEMODE_NV21);
        if (faces != null) {
            ArrayList<ArrayList> pointsOpengl = new ArrayList<ArrayList>();
            if (faces.length > 0) {
                for (int c = 0; c < faces.length; c++) {

                    facepp.getLandmarkRaw(faces[c], Facepp.FPP_GET_LANDMARK106);


                    facepp.get3DPose(faces[c]);

                    final Facepp.Face face = faces[c];
                    pitch = face.pitch;
                    yaw = face.yaw;
                    roll = face.roll;
                    confidence = face.confidence;

                    //0.4.7之前（包括）jni把所有角度的点算到竖直的坐标，所以外面画点需要再调整回来，才能与其他角度适配
                    //目前getLandmarkOrigin会获得原始的坐标，所以只需要横屏适配好其他的角度就不用适配了，因为texture和preview的角度关系是固定的
                    ArrayList<FloatBuffer> triangleVBList = new ArrayList<FloatBuffer>();
                    for (int i = 0; i < face.points.length; i++) {
                        float x = (face.points[i].x / width) * 2 - 1;
                        if (isBackCamera)
                        {
                            x = -x;
                        }
                        float y = (face.points[i].y / height) * 2-1;
                        float[] pointf = new float[]{y, x, 0.0f};
                        FloatBuffer fb = mCameraMatrix.floatBufferUtil(pointf);
                        triangleVBList.add(fb);
                    }


                    pointsOpengl.add(triangleVBList);

                }
            } else {
                pitch = 0.0f;
                yaw = 0.0f;
                roll = 0.0f;
            }

            synchronized (mPointsMatrix) {
                mPointsMatrix.points = pointsOpengl;
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        ConUtil.releaseWakeLock();
        mICamera.closeCamera();
        mCamera = null;

        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                facepp.release();
            }
        });

    }

    private int mTextureID = -1;
    private SurfaceTexture mSurface;
    private CameraMatrix mCameraMatrix;
    private PointsMatrix mPointsMatrix;

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        // TODO Auto-generated method stub
//		Log.d("ceshi", "onFrameAvailable");
        mGlSurfaceView.requestRender();
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        // 黑色背景
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        surfaceInit();
    }

    private void surfaceInit() {
        mTextureID = OpenGLUtil.createTextureID();

        mSurface = new SurfaceTexture(mTextureID);
        // 这个接口就干了这么一件事，当有数据上来后会进到onFrameAvailable方法
        mSurface.setOnFrameAvailableListener(this);// 设置照相机有数据时进入
        mCameraMatrix = new CameraMatrix(mTextureID);
        mPointsMatrix = new PointsMatrix(false);
        mICamera.startPreview(mSurface);// 设置预览容器
        mICamera.actionDetect(this);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        // 设置画面的大小
        GLES20.glViewport(0, 0, width, height);

        float ratio = (float) width / height;
        ratio = 1; // 这样OpenGL就可以按照屏幕框来画了，不是一个正方形了

        // this projection matrix is applied to object coordinates
        // in the onDrawFrame() method
        Matrix.frustumM(mProjMatrix, 0, -ratio, ratio, -1, 1, 3, 7);
        // Matrix.perspectiveM(mProjMatrix, 0, 0.382f, ratio, 3, 700);

    }

    private final float[] mMVPMatrix = new float[16];
    private final float[] mProjMatrix = new float[16];
    private final float[] mVMatrix = new float[16];

    @Override
    public void onDrawFrame(GL10 gl)
    {
//		Log.w("ceshi", "onDrawFrame===");
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);// 清除屏幕和深度缓存
        float[] mtx = new float[16];
        mSurface.getTransformMatrix(mtx);
        mCameraMatrix.draw(mtx);
        // Set the camera position (View matrix)
        Matrix.setLookAtM(mVMatrix, 0, 0, 0, -3, 0f, 0f, 0f, 0f, 1f, 0f);

        // Calculate the projection and view transformation
        Matrix.multiplyMM(mMVPMatrix, 0, mProjMatrix, 0, mVMatrix, 0);

        mPointsMatrix.draw(mMVPMatrix);

        mSurface.updateTexImage();// 更新image，会调用onFrameAvailable方法
    }

}
