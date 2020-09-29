package com.esafirm.imagepicker.features;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentTransaction;

import com.esafirm.imagepicker.R;
import com.esafirm.imagepicker.features.cameraonly.CameraOnlyConfig;
import com.esafirm.imagepicker.helper.ConfigUtils;
import com.esafirm.imagepicker.helper.IpLogger;
import com.esafirm.imagepicker.helper.LocaleManager;
import com.esafirm.imagepicker.model.Folder;
import com.esafirm.imagepicker.model.Image;

import java.util.List;

public class ImagePickerActivity extends AppCompatActivity implements ImagePickerInteractionListener, ImagePickerView, ImagePickerFragment.ChangeTitleListener {

    //    private ActionBar actionBar;
    private ImagePickerFragment imagePickerFragment;

    private ImagePickerConfig config;
    private TextView tvMessage;
    private TextView tvTitle;
    private ProgressBar progressBar;
    private RelativeLayout rlBottom;
    private ImageView ivArrow;
    private RelativeLayout rlTitle;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleManager.updateResources(newBase));
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setResult(RESULT_CANCELED);

        /* This should not happen */
        Intent intent = getIntent();
        if (intent == null || intent.getExtras() == null) {
            IpLogger.getInstance().e("This should not happen. Please open an issue!");
            finish();
            return;
        }
        config = getIntent().getExtras().getParcelable(ImagePickerConfig.class.getSimpleName());
        CameraOnlyConfig cameraOnlyConfig = getIntent().getExtras().getParcelable(CameraOnlyConfig.class.getSimpleName());

        boolean isCameraOnly = cameraOnlyConfig != null;


        // TODO extract camera only function so we don't have to rely to Fragment
        if (!isCameraOnly) {
            setTheme(config.getTheme());
            setContentView(R.layout.ef_activity_image_picker);

        } else {
            setContentView(createCameraLayout());
        }

        progressBar = findViewById(R.id.progressBar);
        tvMessage = findViewById(R.id.tvMessage);
        rlBottom = findViewById(R.id.rlBottom);
        ivArrow = findViewById(R.id.ivArrow);
        rlTitle = findViewById(R.id.rlTitle);


        rlBottom.setOnClickListener(view -> {

            int size = imagePickerFragment.getSelectedImages().size();

            if (size > 0) {
                //여기서 이미지 업로드 하면 됩니다.
                //업로드후 imagePickerFragment.onDone() 호출
                tvMessage.setText(size + "장의 사진을 업로드 중");

                Handler mHandler = new Handler();
                new DelayTask().execute();
                mHandler.postDelayed(new Runnable() {
                    public void run() {

                        imagePickerFragment.onDone();
                    }
                }, 2000);


            } else {
                finish();
            }


        });

        findViewById(R.id.ivClose).setOnClickListener(view -> {
            finish();
        });


        tvTitle = findViewById(R.id.tvTitle);
        rlTitle.setOnClickListener(view -> {

            if (imagePickerFragment != null) {
                imagePickerFragment.changeType();
            }

        });

        if (savedInstanceState != null) {
            // The fragment has been restored.
            imagePickerFragment = (ImagePickerFragment) getSupportFragmentManager().findFragmentById(R.id.ef_imagepicker_fragment_placeholder);
            if(imagePickerFragment != null) {
                imagePickerFragment.setChangeTitleListener(this);
            }
        } else {
            imagePickerFragment = ImagePickerFragment.newInstance(config, cameraOnlyConfig);
            imagePickerFragment.setChangeTitleListener(this);
            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            ft.replace(R.id.ef_imagepicker_fragment_placeholder, imagePickerFragment);
            ft.commit();
        }
    }

    @Override
    public void onTitleChange(String title, int rotate) {
        if(title != null) {
            tvTitle.setText(title);
        }
        ivArrow.setRotation(rotate);

    }

    private FrameLayout createCameraLayout() {
        FrameLayout frameLayout = new FrameLayout(this);
        frameLayout.setId(R.id.ef_imagepicker_fragment_placeholder);
        return frameLayout;
    }

    /**
     * Create option menus.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.ef_image_picker_menu_main, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem menuCamera = menu.findItem(R.id.menu_camera);
        if (menuCamera != null) {
            if (config != null) {
                menuCamera.setVisible(config.isShowCamera());
            }
        }

        MenuItem menuDone = menu.findItem(R.id.menu_done);
        if (menuDone != null) {
            menuDone.setTitle(ConfigUtils.getDoneButtonText(this, config));
            menuDone.setVisible(imagePickerFragment.isShowDoneButton());
        }
        return super.onPrepareOptionsMenu(menu);
    }

    /**
     * Handle option menu's click event
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == android.R.id.home) {
            onBackPressed();
            return true;
        }
        if (id == R.id.menu_done) {
            imagePickerFragment.onDone();
            return true;
        }
        if (id == R.id.menu_camera) {
            imagePickerFragment.captureImageWithPermission();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if (!imagePickerFragment.handleBack()) {
            super.onBackPressed();
        }
    }


    /* --------------------------------------------------- */
    /* > ImagePickerInteractionListener Methods */
    /* --------------------------------------------------- */

    @Override
    public void setTitle(String title) {
//        actionBar.setTitle(title);
        tvMessage.setText(title);
        supportInvalidateOptionsMenu();
    }

    @Override
    public void cancel() {
        finish();
    }

    @Override
    public void selectionChanged(List<Image> imageList) {
        // Do nothing when the selection changes.
    }

    @Override
    public void finishPickImages(Intent result) {
        setResult(RESULT_OK, result);
        finish();
    }

    /* --------------------------------------------------- */
    /* > View Methods  */
    /* --------------------------------------------------- */

    @Override
    public void showLoading(boolean isLoading) {
        imagePickerFragment.showLoading(isLoading);
    }

    @Override
    public void showFetchCompleted(List<Image> images, List<Folder> folders) {
        imagePickerFragment.showFetchCompleted(images, folders);
    }

    @Override
    public void showError(Throwable throwable) {
        imagePickerFragment.showError(throwable);
    }

    @Override
    public void showEmpty() {
        imagePickerFragment.showEmpty();
    }

    @Override
    public void showCapturedImage() {
        imagePickerFragment.showCapturedImage();
    }

    @Override
    public void finishPickImages(List<Image> images) {
        imagePickerFragment.finishPickImages(images);
    }

    public class DelayTask extends AsyncTask<Void, Integer, String> {
        int count = 0;

        @Override
        protected void onPreExecute() {
            progressBar.setVisibility(ProgressBar.VISIBLE);
        }

        @Override
        protected String doInBackground(Void... params) {
            while (count < 5) {
                SystemClock.sleep(400);
                count++;
                publishProgress(count * 20);
            }
            return "Complete";
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            progressBar.setProgress(values[0]);
        }
    }

}
