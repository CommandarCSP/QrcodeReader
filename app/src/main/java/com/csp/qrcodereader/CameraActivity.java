package com.csp.qrcodereader;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.text.Spanned;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.esafirm.imagepicker.features.ImagePicker;
import com.esafirm.imagepicker.features.IpCons;
import com.esafirm.imagepicker.features.ReturnMode;
import com.esafirm.imagepicker.model.Image;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CameraActivity extends AppCompatActivity implements CameraPreview.CameraCallback {

    private SurfaceView surfaceView;
    private CameraPreview mCameraPreview;
    private List<String> addedImages = new ArrayList<>();
    private String lastQrUrl;
    private Snackbar snackbar;
    private ImageView btnClose;
    private ConstraintLayout clClose;
    private TextView tvCount;
    private ImageView ivFlash;
    private ConstraintLayout clPhoto;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_camera);


        ViewGroup viewGroup = findViewById(R.id.clRoot);
        surfaceView = findViewById(R.id.camera_preview_main);

        Button button = findViewById(R.id.button_main_capture);
        button.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if(snackbar != null) {
                    snackbar.dismiss();
                }
                //현재 카메라 캡처 중이 아니면 캡처 진행합니다.
                if(!mCameraPreview.getCaptureFlag()) {
                    mCameraPreview.takePicture();
                } else {
                    Toast.makeText(CameraActivity.this,"촬영을 진행중입니다.",Toast.LENGTH_SHORT).show();
                }

            }
        });
        tvCount = findViewById(R.id.tvCount);
        btnClose = findViewById(R.id.btnClose);
        clClose = findViewById(R.id.clClose);
        ivFlash = findViewById(R.id.ivFlash);
        clPhoto = findViewById(R.id.clPhoto);
        ivFlash.setOnClickListener(view -> {

            //카메라 플래시를 토글 합니다.
            mCameraPreview.toggleflashOnButton();

        });

        clPhoto.setOnClickListener(view -> {

            //갤러리 라이브러리를 띄웁니다.
            ImagePicker.create(this)
                    .returnMode(ReturnMode.NONE) // set whether pick action or camera action should return immediate result or not. Only works in single mode for image picker
                    .folderMode(true) // set folder mode (false by default)
                    .multi()
                    .showCamera(false)
                    .start(); // image selection title

        });

        btnClose.setOnClickListener(view -> close());
        clClose.setOnClickListener(view -> close());

        snackbar = Snackbar.make(viewGroup,lastQrUrl,Snackbar.LENGTH_LONG);
        snackbar.setAction("열기", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    //qr 코드 스낵바에 열기를 누르면 브라우로 해당 url을 띄웁니다.
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    Uri uri = Uri.parse(lastQrUrl);
                    intent.setData(uri);
                    startActivity(intent);

                    lastQrUrl = null;
                    snackbar.dismiss();


                }catch (Exception e){
                    Log.e("start browser error=",e.getMessage());
                }

            }
        });
        //스낵바 애니메이션 형태를 지정하고 상단에서 나오게 설정합니다.
        snackbar.setAnimationMode(BaseTransientBottomBar.ANIMATION_MODE_FADE);
        View view = snackbar.getView();
        FrameLayout.LayoutParams params =(FrameLayout.LayoutParams)view.getLayoutParams();
        params.gravity = Gravity.TOP;
        view.setLayoutParams(params);
        startCamera();

    }

    void close() {
        finish();
    }

    void startCamera(){

        // Create the Preview view and set it as the content of this Activity.
        mCameraPreview = new CameraPreview(this, this, surfaceView, this);

    }



    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        Log.d("onActivityResult", " " + requestCode + " " + String.valueOf(resultCode));
        //갤러리에서 이미지를 업로드 한경우 바로 엑티비티 종료합니다. 선택한 이미지 리스트도 받을수 있습니다.
        if (ImagePicker.shouldHandle(requestCode, resultCode, data)) {
            ArrayList<Image> images = (ArrayList<Image>) ImagePicker.getImages(data);
            for (Image image:images) {
                Log.d("image=",image.toString());
            }

            finish();
            return;
        }

        //카메라 프리뷰에서 캡처한 이미지를 수정하고 결과를 돌려받는경우가 있기때문에 등록해줍니다.
        mCameraPreview.handleActivityResult(requestCode,resultCode,data);

    }

    @Override
    public void addedImage(String path) {
        //이미지 수정이 완료되면 이곳으로 완료된 이미지 패스가 들어옵니다.
        Log.d("addedImage",path);
        addedImages.add(path);

        clClose.setVisibility(View.VISIBLE);
        clPhoto.setVisibility(View.GONE);
        tvCount.setText(addedImages.size()+" "+">");

    }

    @Override
    public void findQrCode(String path) {
        //qr 코드가 찾아지면 이곳으로 url 이 들어옵니다.
        //사진이 1장이라도 이미 찍힌게 있다면 qr 코드는 들어오지 않습니다.
        Log.d("findQrCode",path);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if(lastQrUrl == null || !lastQrUrl.equals(path)){
                    lastQrUrl = path;
                }
                if(snackbar != null) {
                    snackbar.setText(lastQrUrl);
                    snackbar.show();
                }

            }
        });


    }
}
