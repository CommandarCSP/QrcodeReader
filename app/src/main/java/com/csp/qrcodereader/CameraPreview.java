package com.csp.qrcodereader;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;
import android.util.SparseArray;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;

import iamutkarshtiwari.github.io.ananas.editimage.EditImageActivity;
import iamutkarshtiwari.github.io.ananas.editimage.ImageEditorIntentBuilder;

public class CameraPreview implements SurfaceHolder.Callback {

    private final String TAG = "CameraPreview";
    private final int PHOTO_EDITOR_REQUEST_CODE = 231;

    private final static String DIRECTORY_PATH = Environment.getExternalStorageDirectory().getAbsolutePath() + "/tempImg";

    private SurfaceView mSurfaceView;
    private SurfaceHolder mHolder;
    private AppCompatActivity mActivity;
    private CameraCallback mCameraCallback;
    private CameraSource mCameraSource;
    private Context mContext;
    private Boolean isAddedImage = false;
    private Boolean captureFlag = false;
    private Camera camera = null;
    boolean flashmode=false;

    public interface CameraCallback {

        void addedImage(String path);

        void findQrCode(String path);

    }

    public CameraPreview(Context context, AppCompatActivity activity, SurfaceView surfaceView, CameraCallback cameraCallback) {

        mContext = context;
        mActivity = activity;
        mSurfaceView = surfaceView;
        mCameraCallback = cameraCallback;

        mSurfaceView.setVisibility(View.VISIBLE);

        initBarcodeDetector(context);


        // SurfaceHolder.Callback를 등록하여 surface의 생성 및 해제 시점을 감지
        mHolder = mSurfaceView.getHolder();
        mHolder.addCallback(this);

    }

    public Boolean getCaptureFlag() {
        return captureFlag;
    }

    public void initBarcodeDetector(Context context) {
        //바코드 스캐너를 초기화 하고 카메라소스를 초기화 합니다.
        BarcodeDetector barcodeDetector = new BarcodeDetector.Builder(context)
                .setBarcodeFormats(Barcode.QR_CODE)
                .build();

        mCameraSource = new CameraSource
                .Builder(context, barcodeDetector)
                .setFacing(CameraSource.CAMERA_FACING_BACK)
                .setAutoFocusEnabled(true)
                .build();



        barcodeDetector.setProcessor(new Detector.Processor<Barcode>() {
            @Override
            public void release() {
                Log.d("NowStatus", "BarcodeDetector SetProcessor Released");
            }

            @Override
            public void receiveDetections(Detector.Detections<Barcode> detections) {
                //qr 코드가 스캔되면 이곳으로 들어옵니다. 캡처된 이미지가 1장이라도 있는경우 무시합니다.
                if (!isAddedImage) {
                    final SparseArray<Barcode> barcodes = detections.getDetectedItems();
                    if (barcodes.size() != 0) {
                        String barcodeContents = barcodes.valueAt(0).displayValue;
//                        Log.d("Detection", barcodeContents);
                        if (mCameraCallback != null) {
                            mCameraCallback.findQrCode(barcodeContents);
                        }
                    }
                }


            }
        });

    }

    public void toggleflashOnButton() {
        //카메라 객체를 이용하여 플래시를 키고 켭니다.
        camera=getCamera(mCameraSource);
        if (camera != null) {
            try {
                Camera.Parameters param = camera.getParameters();
                param.setFlashMode(!flashmode?Camera.Parameters.FLASH_MODE_TORCH :Camera.Parameters.FLASH_MODE_OFF);
                camera.setParameters(param);
                flashmode = !flashmode;
                if(flashmode){
                    Log.d("Flash Switched ON","!!");
                }
                else {
                    Log.d("Flash Switched Off","!!");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }

    public void setflashOnButton(boolean flash) {
        //서페이스가 다시 생성되는경우 기존 플래시 상태에 따라  다시 켜줄필요가 있기때문에 사용합니다.
        camera=getCamera(mCameraSource);
        if (camera != null) {
            try {
                Camera.Parameters param = camera.getParameters();
                param.setFlashMode(flash?Camera.Parameters.FLASH_MODE_TORCH :Camera.Parameters.FLASH_MODE_OFF);
                camera.setParameters(param);
                flashmode = flash;
                if(flashmode){
                    Log.d("Flash Switched ON","!!");
                }
                else {
                    Log.d("Flash Switched Off","!!");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }

    private static Camera getCamera(@NonNull CameraSource cameraSource) {
        Field[] declaredFields = CameraSource.class.getDeclaredFields();

        for (Field field : declaredFields) {
            if (field.getType() == Camera.class) {
                field.setAccessible(true);
                try {
                    Camera camera = (Camera) field.get(cameraSource);
                    if (camera != null) {
                        return camera;
                    }
                    return null;
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
                break;
            }
        }
        return null;
    }

    public void handleActivityResult(int requestCode, int resultCode, Intent data) {
        //사진 수정이 끝나고 완료되면 이곳으로 이미지 정보가 들어옵니다.
        // 수정을 완료 했을때만 받습니다 그냥 백버튼으로 나올경우 패스를 저장하지 않습니다.
        if (requestCode == PHOTO_EDITOR_REQUEST_CODE && resultCode == Activity.RESULT_OK) { // same code you used while starting
            String filePath = data.getStringExtra(ImageEditorIntentBuilder.OUTPUT_PATH);
            boolean isImageEdit = data.getBooleanExtra(EditImageActivity.IS_IMAGE_EDITED, false);

//            Log.d("requestCode", String.valueOf(requestCode));
//            Log.d("resultCode", String.valueOf(resultCode));
//            Log.d("Intent", data.getExtras().toString());
//            Log.d("filePath", filePath);
//            Log.d("isImageEdit", String.valueOf(isImageEdit));

            if(!filePath.equals("")) {
                isAddedImage = true;
                File outputFile = new File(filePath);
                Intent mediaScanIntent = new Intent( Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                mediaScanIntent.setData(Uri.fromFile(outputFile));
                mContext.sendBroadcast(mediaScanIntent);
                if(mCameraCallback != null) {
                    mCameraCallback.addedImage(filePath);
                }

            }




        }

    }


    public void surfaceCreated(SurfaceHolder holder) {
    //서페이스 관련 라이프 사이클 함수
        try {

            if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED) {
                if(mCameraSource != null) {
                    mCameraSource.start(mHolder);
                }

                setflashOnButton(flashmode);

            }

            Log.d(TAG, "qrcode scan start");
        } catch (IOException e) {
            Log.d(TAG, "Error qrcode scan: " + e.getMessage());
        }


    }



    public void surfaceDestroyed(SurfaceHolder holder) {
        //서페이스 관련 라이프 사이클 함수
        Log.d(TAG, "surfaceDestroyed");
        if(mCameraSource != null) {
            mCameraSource.stop();
        }

    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {

    }


    public void takePicture(){
        //캡처를 찍습니다.

        mCameraSource.takePicture(shutterCallback,jpegCallback);

    }


    CameraSource.ShutterCallback shutterCallback = new CameraSource.ShutterCallback() {
        @Override
        public void onShutter() {
            Log.d("onShutter","!!");
            captureFlag = true;
        }
    };

    CameraSource.PictureCallback jpegCallback = new CameraSource.PictureCallback() {
        @Override
        public void onPictureTaken(byte[] data) {
            //캡처된 이미지가 byte 배열로 들어오고
            //해당 이미지를 분석해 로테이션을 조정하고
            //비동기로 이미지파일을 디스크에 쓴후 패스를 전송합니다.
            Bitmap bitmapPicture = null;

            int orientation = Exif.getOrientation(data);
            Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
            switch(orientation) {
                case 90:
                    bitmapPicture= rotateImage(bitmap, 90);

                    break;
                case 180:
                    bitmapPicture= rotateImage(bitmap, 180);

                    break;
                case 270:
                    bitmapPicture= rotateImage(bitmap, 270);

                    break;
                case 0:


                default:
                    break;
            }



            //bitmap을 byte array로 변환
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            bitmapPicture.compress(Bitmap.CompressFormat.JPEG, 100, stream);
            byte[] currentData = stream.toByteArray();

            //파일로 저장
            new SaveImageTask().execute(currentData);

        }

        };

    private Bitmap rotateImage(Bitmap source, float angle) {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(),   source.getHeight(), matrix,
                true);
    }






    private class SaveImageTask extends AsyncTask<byte[], String, String> {

        @Override
        protected String doInBackground(byte[]... data) {
            FileOutputStream outStream = null;
            String filePath = null;

            try {

                File path = new File (DIRECTORY_PATH);
                if (!path.exists()) {
                    path.mkdirs();
                }

                String fileName = String.format("%d.jpg", System.currentTimeMillis());
                File outputFile = new File(path, fileName);

                outStream = new FileOutputStream(outputFile);
                outStream.write(data[0]);
                outStream.flush();
                outStream.close();


                filePath = outputFile.getAbsolutePath();
                Log.d(TAG, "onPictureTaken - wrote bytes: " + data.length + " to "
                        + outputFile.getAbsolutePath());


                // 갤러리에 반영
                Intent mediaScanIntent = new Intent( Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                mediaScanIntent.setData(Uri.fromFile(outputFile));
                mContext.sendBroadcast(mediaScanIntent);


            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            return filePath;
        }

        @Override
        protected void onPostExecute(String filePath) {
            super.onPostExecute(filePath);
            Log.d("filePath=", filePath);

            Intent intent = null;
            try {
                intent = new ImageEditorIntentBuilder(mContext, filePath, filePath)
                        .forcePortrait(true)  // Add this to force portrait mode (It's set to false by default)
                        .setSupportActionBarVisibility(true) // To hide app's default action bar
                        .build();

                EditImageActivity.start(mActivity, intent, PHOTO_EDITOR_REQUEST_CODE);
                captureFlag = false;
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }

}