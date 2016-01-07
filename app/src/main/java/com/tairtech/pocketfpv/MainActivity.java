package com.tairtech.pocketfpv;

import android.app.Activity;
import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.view.Surface;
import android.view.View;
import android.widget.Toast;

import com.serenegiant.usb.CameraDialog;
import com.serenegiant.usb.IButtonCallback;
import com.serenegiant.usb.IStatusCallback;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.UVCCamera;
import com.serenegiant.widget.UVCCameraTextureView;
import com.umeng.analytics.MobclickAgent;
import com.umeng.update.UmengUpdateAgent;

import java.nio.ByteBuffer;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity implements CameraDialog.CameraDialogParent {


    // for thread pool
    private static final int CORE_POOL_SIZE = 1;        // initial/minimum threads
    private static final int MAX_POOL_SIZE = 4;            // maximum threads
    private static final int KEEP_ALIVE_TIME = 10;        // time periods while keep the idle thread
    protected static final ThreadPoolExecutor EXECUTER
            = new ThreadPoolExecutor(CORE_POOL_SIZE, MAX_POOL_SIZE, KEEP_ALIVE_TIME,
            TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());

    // for accessing USB and USB camera
    private USBMonitor mUSBMonitor;
    private UVCCamera mUVCCamera;
    private UVCCameraTextureView mUVCCameraView;
    // for open&start / stop&close camera preview
    private FloatingActionButton mFloatingActionButton;
    private Surface mPreviewSurface;


    /**
     * to access from CameraDialog
     *
     * @return
     */
    @Override
    public USBMonitor getUSBMonitor() {
        return mUSBMonitor;
    }


    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mFloatingActionButton=(FloatingActionButton)findViewById(R.id.connect_button);
        mFloatingActionButton.setOnClickListener(mOnClickListener);

        mUVCCameraView = (UVCCameraTextureView) findViewById(R.id.UVCCameraTextureView1);
        mUVCCameraView.setAspectRatio(UVCCamera.DEFAULT_PREVIEW_WIDTH / (float) UVCCamera.DEFAULT_PREVIEW_HEIGHT);

        mUSBMonitor = new USBMonitor(this, mOnDeviceConnectListener);

        UmengUpdateAgent.update(this);  //一打开软件就检查更新

    }

    /**
     * 沉浸式UI
     */
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            View decorView = getWindow().getDecorView();
            decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mUSBMonitor.register();
        if (mUVCCamera != null)
            mUVCCamera.startPreview();
        MobclickAgent.onResume(this);    //umeng tngji
    }

    @Override
    public void onPause() {
        if (mUVCCamera != null)
            mUVCCamera.stopPreview();
        mUSBMonitor.unregister();
        super.onPause();
        MobclickAgent.onPause(this);    //umeng tngji
    }

    @Override
    public void onDestroy() {
        if (mUVCCamera != null) {
            mUVCCamera.destroy();
            mUVCCamera = null;
        }
        if (mUSBMonitor != null) {
            mUSBMonitor.destroy();
            mUSBMonitor = null;
        }
        mUVCCameraView = null;
        mFloatingActionButton = null;
        super.onDestroy();
    }

    private final View.OnClickListener mOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(final View view) {
            if (mUVCCamera == null)
                CameraDialog.showDialog(MainActivity.this);
            else {
                mUVCCamera.destroy();
                mUVCCamera = null;
            }
        }
    };

    private final USBMonitor.OnDeviceConnectListener mOnDeviceConnectListener = new USBMonitor.OnDeviceConnectListener() {
        @Override
        public void onAttach(final UsbDevice device) {
            Toast.makeText(MainActivity.this, "USB_DEVICE_ATTACHED", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onConnect(final UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock, final boolean createNew) {
            if (mUVCCamera != null)
                mUVCCamera.destroy();
            mUVCCamera = new UVCCamera();
            EXECUTER.execute(new Runnable() {
                @Override
                public void run() {
                    mUVCCamera.open(ctrlBlock);
                    mUVCCamera.setStatusCallback(new IStatusCallback() {
                        @Override
                        public void onStatus(final int statusClass, final int event, final int selector,
                                             final int statusAttribute, final ByteBuffer data) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(MainActivity.this, "onStatus(statusClass=" + statusClass
                                            + "; " +
                                            "event=" + event + "; " +
                                            "selector=" + selector + "; " +
                                            "statusAttribute=" + statusAttribute + "; " +
                                            "data=...)", Toast.LENGTH_SHORT).show();
                                }
                            });

                        }
                    });
                    mUVCCamera.setButtonCallback(new IButtonCallback() {
                        @Override
                        public void onButton(final int button, final int state) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(MainActivity.this, "onButton(button=" + button + "; " +
                                            "state=" + state + ")", Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    });
//					mUVCCamera.setPreviewTexture(mUVCCameraView.getSurfaceTexture());
                    if (mPreviewSurface != null) {
                        mPreviewSurface.release();
                        mPreviewSurface = null;
                    }
                    try {
                        mUVCCamera.setPreviewSize(UVCCamera.DEFAULT_PREVIEW_WIDTH, UVCCamera.DEFAULT_PREVIEW_HEIGHT, UVCCamera.FRAME_FORMAT_MJPEG);
                    } catch (final IllegalArgumentException e) {
                        // fallback to YUV mode
                        try {
                            mUVCCamera.setPreviewSize(UVCCamera.DEFAULT_PREVIEW_WIDTH, UVCCamera.DEFAULT_PREVIEW_HEIGHT, UVCCamera.DEFAULT_PREVIEW_MODE);
                        } catch (final IllegalArgumentException e1) {
                            mUVCCamera.destroy();
                            mUVCCamera = null;
                        }
                    }
                    if (mUVCCamera != null) {
                        final SurfaceTexture st = mUVCCameraView.getSurfaceTexture();
                        if (st != null)
                            mPreviewSurface = new Surface(st);
                        mUVCCamera.setPreviewDisplay(mPreviewSurface);
//						mUVCCamera.setFrameCallback(mIFrameCallback, UVCCamera.PIXEL_FORMAT_RGB565/*UVCCamera.PIXEL_FORMAT_NV21*/);
                        mUVCCamera.startPreview();
                    }
                }
            });
        }

        @Override
        public void onDisconnect(final UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock) {
            // XXX you should check whether the comming device equal to camera device that currently using
            if (mUVCCamera != null) {
                mUVCCamera.close();
                if (mPreviewSurface != null) {
                    mPreviewSurface.release();
                    mPreviewSurface = null;
                }
            }
        }

        @Override
        public void onDettach(final UsbDevice device) {
            Toast.makeText(MainActivity.this, "USB_DEVICE_DETACHED", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onCancel() {
        }
    };


    // if you need frame data as byte array on Java side, you can use this callback method with UVCCamera#setFrameCallback
    // if you need to create Bitmap in IFrameCallback, please refer following snippet.
/*	final Bitmap bitmap = Bitmap.createBitmap(UVCCamera.DEFAULT_PREVIEW_WIDTH, UVCCamera.DEFAULT_PREVIEW_HEIGHT, Bitmap.Config.RGB_565);
    private final IFrameCallback mIFrameCallback = new IFrameCallback() {
		@Override
		public void onFrame(final ByteBuffer frame) {
			frame.clear();
			synchronized (bitmap) {
				bitmap.copyPixelsFromBuffer(frame);
			}
			mImageView.post(mUpdateImageTask);
		}
	};

	private final Runnable mUpdateImageTask = new Runnable() {
		@Override
		public void run() {
			synchronized (bitmap) {
				mImageView.setImageBitmap(bitmap);
			}
		}
	}; */
}
