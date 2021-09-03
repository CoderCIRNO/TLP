package com.suzumiyakonata.tlp;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

public class MainActivity extends AppCompatActivity {
    private TextureView mTextureView = null;
    Button mRecord = null;
    SeekBar mSeekbar = null;
    TextView mText = null;

    private CameraManager mCamManager = null;
    private CameraDevice mCamDevice = null;
    private CameraCaptureSession mCamSession = null;
    private CameraCaptureSession mVideoSession = null;
    private CaptureRequest.Builder mRepeatRequestBuilder = null;
    private CaptureRequest.Builder mVideoRequestBuilder = null;
    private CaptureRequest mRepeatRequest = null;
    private SurfaceTexture mSurfaceTexture;
    private HandlerThread mVideoThread;
    private Handler mVideoThreadHandler;

    private Surface mPreviewSurface = null;
    private MediaRecorder mMediaRecorder = null;
    private String mCurrentFilePath = null;
    private boolean mIsRecording = false;

    private long mFrameCount;
    private volatile int mFrameDuration = 100;
    private Size mPreviewSize;
    private Size mVideoSize;
    private final String cameraId = "0";
    private final String TAG = "wangtianlin1";
    @SuppressLint("SimpleDateFormat")
    private final SimpleDateFormat mSimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_main);
            if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA}, 1);
            }
            if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            }
            if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.RECORD_AUDIO}, 1);
            }
            mTextureView = findViewById(R.id.previewer);
            mRecord = findViewById(R.id.recordStart);
            mText = findViewById(R.id.frameCount);
            //此滑动条作用是改变延时摄影的拍摄间隔
            mSeekbar = findViewById(R.id.seekBar);
            mSeekbar.setMax(60000);
            mSeekbar.setMin(1000);
        }

        mRecord.setOnClickListener(v -> {
            if(mIsRecording){
                mRecord.setClickable(false);
                mMediaRecorder.stop();
                mMediaRecorder.reset();
                mVideoThreadHandler.sendEmptyMessage(1);
            }else{
                try {
                    setUpMediaRecorder();
                    mFrameCount = 0;
                    mMediaRecorder.start();
                    mCamSession.stopRepeating();
                    mTextureView.setVisibility(View.INVISIBLE);
                    mSeekbar.setVisibility(View.INVISIBLE);
                    mText.setVisibility(View.VISIBLE);
                    mCamDevice.createCaptureSession(Arrays.asList(mMediaRecorder.getSurface()),
                            new CameraCaptureSession.StateCallback() {
                                @Override
                                public void onConfigured(@NonNull CameraCaptureSession session) {
                                    try {
                                        mVideoRequestBuilder = mCamDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                                        mVideoRequestBuilder.addTarget(mMediaRecorder.getSurface());
                                        setBuilder(mVideoRequestBuilder);
                                        mVideoSession = session;
                                        Log.d(TAG, "onConfigured: ChildThread start");
                                        startThread();
                                    } catch (CameraAccessException e) {
                                        e.printStackTrace();
                                    }
                                }
                                @Override
                                public void onConfigureFailed(@NonNull CameraCaptureSession session) {

                                }
                            }, null);
                } catch (CameraAccessException | IOException e) {
                    e.printStackTrace();
                }
            }
            mIsRecording ^= true;
        });

        mSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mFrameDuration = progress;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                Toast.makeText(getApplicationContext(), "拍摄间隔设置为" + mFrameDuration + "毫秒", Toast.LENGTH_SHORT).show();
            }
        });
        mCamManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        mTextureView.setSurfaceTextureListener(textureListener);
    }

    private void startThread(){
        mVideoThread = new HandlerThread("Video");
        mVideoThread.start();
        mVideoThreadHandler = new Handler(mVideoThread.getLooper()){
            @Override
            public void handleMessage(Message msg){
                while(!hasMessages(1)){
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            ++mFrameCount;
                            String _temp = "帧 " + mFrameCount;
                            mText.setText(_temp);
                            Log.d(TAG, "run: " + mFrameCount);
                        }
                    });
                    try {
                        mVideoSession.capture(mVideoRequestBuilder.build(), new CameraCaptureSession.CaptureCallback() {
                            @Override
                            public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
                                super.onCaptureStarted(session, request, timestamp, frameNumber);
                            }
                        }, null);
                        Thread.sleep(mFrameDuration);
                    } catch (CameraAccessException | InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                mVideoThreadHandler.removeMessages(1);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mText.setVisibility(View.INVISIBLE);
                        mTextureView.setVisibility(View.VISIBLE);
                        mSeekbar.setVisibility(View.VISIBLE);
                        Toast.makeText(getApplicationContext(), mCurrentFilePath, Toast.LENGTH_SHORT).show();
                        startPreview();
                        mRecord.setClickable(true);
                    }
                });

            }
        };
        mVideoThreadHandler.sendEmptyMessage(0);
    }

    private void setUpMediaRecorder() throws IOException {
        Log.d(TAG, "setUpMediaRecorder: setUp MediaRecorder");
        ContextWrapper tempContextWrapper = new ContextWrapper(getApplicationContext());
        File directory = tempContextWrapper.getExternalFilesDir(Environment.DIRECTORY_MOVIES);
        Date date = new Date(System.currentTimeMillis());
        String fileName = mSimpleDateFormat.format(date);
        File video = new File(directory, fileName + ".mp4");
        double fps = 1000;
        fps /= mFrameDuration;
        Log.d(TAG, "setUpMediaRecorder: " + fps);
        mCurrentFilePath = video.getAbsolutePath();
        mMediaRecorder = new MediaRecorder();
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mMediaRecorder.setOutputFile(mCurrentFilePath);
        mMediaRecorder.setVideoEncodingBitRate(10000000);
        mMediaRecorder.setVideoFrameRate(30);
        mMediaRecorder.setCaptureRate(fps);
        mMediaRecorder.setVideoSize(mVideoSize.getWidth(), mVideoSize.getHeight());
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mMediaRecorder.prepare();
        Log.d(TAG, "setUpMediaRecorder: prepared");
    }

    //缩放预览画面以解决因比例不调导致的预览画面扭曲问题
    private void configureTransform(int viewHeight) {
        float scale = (float) viewHeight / 3200;
        Matrix matrix = new Matrix();
        matrix.postScale(1, scale, 0, 0);
        mTextureView.setTransform(matrix);
    }

    public TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
            try{
                CameraCharacteristics characteristics = mCamManager.getCameraCharacteristics(cameraId);
                mVideoSize = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG)[3];
                Log.d(TAG, "onSurfaceTextureAvailable: " + mVideoSize.getHeight() + " " + mVideoSize.getWidth());
                mPreviewSize = mVideoSize;
                Toast.makeText(getApplicationContext(), "尺寸:" + mVideoSize.getWidth() + " x " + mVideoSize.getHeight(), Toast.LENGTH_SHORT).show();

                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                mCamManager.openCamera(cameraId, mStateCallback, null);
            }catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
        public final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
            @Override
            public void onOpened(@NonNull CameraDevice camera) {
                mCamDevice = camera;
                startPreview();
            }
            @Override
            public void onDisconnected(@NonNull CameraDevice camera) {

            }
            @Override
            public void onError(@NonNull CameraDevice camera, int error) {

            }
        };
        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {

        }
        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
            return false;
        }
        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {

        }
    };
    public void startPreview(){
        mSurfaceTexture = mTextureView.getSurfaceTexture();
        mSurfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        mPreviewSurface = new Surface(mSurfaceTexture);
        try{
            mCamDevice.createCaptureSession(Arrays.asList(mPreviewSurface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            try {
                                mRepeatRequestBuilder = mCamDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                                mRepeatRequestBuilder.addTarget(mPreviewSurface);
                                //自动白平衡，自动曝光，焦距锁定无限远
                                setBuilder(mRepeatRequestBuilder);
                                mCamSession = session;
                                mRepeatRequest = mRepeatRequestBuilder.build();
                                try{
                                    mCamSession.setRepeatingRequest(mRepeatRequest, new CameraCaptureSession.CaptureCallback() {
                                        @Override
                                        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                                        }
                                    }, null);
                                }catch(CameraAccessException e){
                                    e.printStackTrace();
                                }
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }
                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {

                        }
                    }, null);
        }catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    private void setBuilder(CaptureRequest.Builder _builder){
        _builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO);
        _builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
        _builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
        _builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, 0.0f);
    }
}
