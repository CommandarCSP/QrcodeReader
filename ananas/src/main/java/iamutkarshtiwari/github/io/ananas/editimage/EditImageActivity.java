package iamutkarshtiwari.github.io.ananas.editimage;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Surface;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;

import com.theartofdev.edmodo.cropper.CropImageView;

import org.jetbrains.annotations.NotNull;

import iamutkarshtiwari.github.io.ananas.BaseActivity;
import iamutkarshtiwari.github.io.ananas.R;
import iamutkarshtiwari.github.io.ananas.editimage.interfaces.OnLoadingDialogListener;
import iamutkarshtiwari.github.io.ananas.editimage.utils.BitmapUtils;
import iamutkarshtiwari.github.io.ananas.editimage.utils.PermissionUtils;
import iamutkarshtiwari.github.io.ananas.editimage.view.RotateImageView;
import iamutkarshtiwari.github.io.ananas.editimage.view.imagezoom.ImageViewTouch;
import iamutkarshtiwari.github.io.ananas.editimage.view.imagezoom.ImageViewTouchBase;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class EditImageActivity extends BaseActivity implements OnLoadingDialogListener {
    public static final String IS_IMAGE_EDITED = "is_image_edited";
    public static final int MODE_NONE = 0;
    public static final int MODE_CROP = 3;
    public static final int MODE_ROTATE = 4;
    private static final int PERMISSIONS_REQUEST_CODE = 110;
    private final String[] requiredPermissions = new String[]{
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    public String sourceFilePath;
    public String outputFilePath;
    public String editorTitle;
    public CropImageView cropPanel;
    public ImageViewTouch mainImage;
    public int mode = MODE_NONE;
    protected boolean isBeenSaved = false;
    protected boolean isPortraitForced = false;
    protected boolean isSupportActionBarEnabled = false;
    public RotateImageView rotatePanel;

    protected int numberOfOperations = 0;
    private int imageWidth, imageHeight;
    private Bitmap mainBitmap;
    private Dialog loadingDialog;
    private CompositeDisposable compositeDisposable = new CompositeDisposable();

    private Button btnBack;
    private Button btnOk;
    private Button btnApply;

    private ImageView ivRotate;
    private ImageView ivCrop;

    public static void start(Activity activity, Intent intent, int requestCode) {
        if (TextUtils.isEmpty(intent.getStringExtra(ImageEditorIntentBuilder.SOURCE_PATH))) {
            Toast.makeText(activity, R.string.iamutkarshtiwari_github_io_ananas_not_selected, Toast.LENGTH_SHORT).show();
            return;
        }
        activity.startActivityForResult(intent, requestCode);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_edit);
        getData();
        initView();
    }

    @Override
    protected void onPause() {
        compositeDisposable.clear();
        super.onPause();
    }

    @Override
    public void showLoadingDialog() {
        loadingDialog.show();
    }

    @Override
    public void dismissLoadingDialog() {
        loadingDialog.dismiss();
    }

    private void getData() {
        isPortraitForced = getIntent().getBooleanExtra(ImageEditorIntentBuilder.FORCE_PORTRAIT, false);
        isSupportActionBarEnabled  = getIntent().getBooleanExtra(ImageEditorIntentBuilder.SUPPORT_ACTION_BAR_VISIBILITY, false);

        sourceFilePath = getIntent().getStringExtra(ImageEditorIntentBuilder.SOURCE_PATH);
        outputFilePath = getIntent().getStringExtra(ImageEditorIntentBuilder.OUTPUT_PATH);
        editorTitle = getIntent().getStringExtra(ImageEditorIntentBuilder.EDITOR_TITLE);
    }

    private void initView() {
        loadingDialog = BaseActivity.getLoadingDialog(this, R.string.iamutkarshtiwari_github_io_ananas_loading,
                false);

        if (getSupportActionBar() != null) {
            if (isSupportActionBarEnabled) {
                getSupportActionBar().show();
            } else {
                getSupportActionBar().hide();
            }
        }

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        imageWidth = metrics.widthPixels / 2;
        imageHeight = metrics.heightPixels / 2;

        btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> onBackPressed());
        btnOk = findViewById(R.id.btnOk);
        btnOk.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (numberOfOperations == 0) {
                    onSaveTaskDone();
                } else {
                    doSaveImage();
                }
            }
        });

        btnApply = findViewById(R.id.btnApply);
        btnApply.setOnClickListener(v -> {
            switch (mode) {
                case MODE_CROP:
                    applyCropImage();
                    break;
                case MODE_ROTATE:
                    applyRotateImage();
                    break;
            }
        });

        ivRotate = findViewById(R.id.ivRotate);

        ivRotate.setOnClickListener(v -> {
            if(mode != EditImageActivity.MODE_ROTATE) {
                mode = EditImageActivity.MODE_ROTATE;
                mainImage.setImageBitmap(getMainBit());
                mainImage.setDisplayType(ImageViewTouchBase.DisplayType.FIT_TO_SCREEN);
                mainImage.setVisibility(View.GONE);

                cropPanel.setVisibility(View.GONE);
                cropPanel.setImageBitmap(getMainBit());
                cropPanel.setFixedAspectRatio(false);

                rotatePanel.addBit(getMainBit(),
                        mainImage.getBitmapRect());

                rotatePanel.reset();
                rotatePanel.setVisibility(View.VISIBLE);
                btnApply.setVisibility(View.VISIBLE);
            }

            int updatedAngle = rotatePanel.getRotateAngle() + 90;
            rotatePanel.rotateImage(updatedAngle);
        });

        ivCrop = findViewById(R.id.ivCrop);

        ivCrop.setOnClickListener(v -> {
            mode = EditImageActivity.MODE_CROP;

            mainImage.setVisibility(View.GONE);
            cropPanel.setVisibility(View.VISIBLE);
            mainImage.setImageBitmap(getMainBit());
            mainImage.setDisplayType(ImageViewTouchBase.DisplayType.FIT_TO_SCREEN);
            mainImage.setScaleEnabled(false);

            rotatePanel.reset();
            rotatePanel.setVisibility(View.GONE);
            cropPanel.setImageBitmap(getMainBit());
            cropPanel.setFixedAspectRatio(false);
            btnApply.setVisibility(View.VISIBLE);
        });

        mainImage = findViewById(R.id.main_image);

        View backBtn = findViewById(R.id.back_btn);
        backBtn.setOnClickListener(v -> onBackPressed());


        cropPanel = findViewById(R.id.crop_panel);
        rotatePanel = findViewById(R.id.rotate_panel);


        if (!PermissionUtils.hasPermissions(this, requiredPermissions)) {
            ActivityCompat.requestPermissions(this, requiredPermissions, PERMISSIONS_REQUEST_CODE);
        }

        loadImageFromFile(sourceFilePath);
    }


    private void releaseState() {
        mode = EditImageActivity.MODE_NONE;
        mainImage.setVisibility(View.VISIBLE);
        cropPanel.setVisibility(View.GONE);
        rotatePanel.setVisibility(View.GONE);
        btnApply.setVisibility(View.GONE);
    }

    private void releaseCrop() {
        mode = EditImageActivity.MODE_NONE;
        cropPanel.setVisibility(View.GONE);
        mainImage.setVisibility(View.VISIBLE);
        mainImage.setScaleEnabled(true);
        btnApply.setVisibility(View.GONE);
    }



    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NotNull String permissions[], @NotNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_CODE: {
                // If request is cancelled, the result arrays are empty.
                if (!(grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    finish();
                }
                break;
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Lock orientation for this activity
        if (isPortraitForced) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        } else {
            setLockScreenOrientation(true);
        }
    }


    @Override
    public void onPointerCaptureChanged(boolean hasCapture) {
    }

    @Override
    public void onBackPressed() {
        switch (mode) {
            case MODE_CROP:
                releaseCrop();
                break;
            case MODE_ROTATE:
                releaseState();
                break;
            default:
                if (canAutoExit()) {
                    finish();
                } else {
                    AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
                    alertDialogBuilder.setMessage(R.string.iamutkarshtiwari_github_io_ananas_exit_without_save)
                            .setCancelable(false).setPositiveButton(R.string.iamutkarshtiwari_github_io_ananas_confirm, (dialog, id) -> finish()).setNegativeButton(R.string.iamutkarshtiwari_github_io_ananas_cancel, (dialog, id) -> dialog.cancel());

                    AlertDialog alertDialog = alertDialogBuilder.create();
                    alertDialog.show();
                }
                break;
        }
    }

    public void changeMainBitmap(Bitmap newBit, boolean needPushUndoStack) {
        if (newBit == null)
            return;

        if (mainBitmap == null || mainBitmap != newBit) {
            if (needPushUndoStack) {
                increaseOpTimes();
            }
            mainBitmap = newBit;
            mainImage.setImageBitmap(mainBitmap);
            mainImage.setDisplayType(ImageViewTouchBase.DisplayType.FIT_TO_SCREEN);

        }
    }

    protected void onSaveTaskDone() {
        Intent returnIntent = new Intent();
        returnIntent.putExtra(ImageEditorIntentBuilder.SOURCE_PATH, sourceFilePath);
        returnIntent.putExtra(ImageEditorIntentBuilder.OUTPUT_PATH, outputFilePath);
        returnIntent.putExtra(IS_IMAGE_EDITED, numberOfOperations > 0);

        setResult(RESULT_OK, returnIntent);
        finish();
    }

    protected void doSaveImage() {
        if (numberOfOperations <= 0)
            return;

        compositeDisposable.clear();

        Disposable saveImageDisposable = saveImage(mainBitmap)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe(subscriber -> loadingDialog.show())
                .doFinally(() -> loadingDialog.dismiss())
                .subscribe(result -> {
                    if (result) {
                        resetOpTimes();
                        onSaveTaskDone();
                    } else {
                        showToast(R.string.iamutkarshtiwari_github_io_ananas_save_error);
                    }
                }, e -> showToast(R.string.iamutkarshtiwari_github_io_ananas_save_error));

        compositeDisposable.add(saveImageDisposable);
    }

    private Single<Boolean> saveImage(Bitmap finalBitmap) {
        return Single.fromCallable(() -> {
            if (TextUtils.isEmpty(outputFilePath))
                return false;

            return BitmapUtils.saveBitmap(finalBitmap, outputFilePath);
        });
    }

    private void loadImageFromFile(String filePath) {
        compositeDisposable.clear();

        Disposable loadImageDisposable = loadImage(filePath)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe(subscriber -> loadingDialog.show())
                .doFinally(() -> loadingDialog.dismiss())
                .subscribe(processedBitmap -> changeMainBitmap(processedBitmap, false), e -> showToast(R.string.iamutkarshtiwari_github_io_ananas_load_error));

        compositeDisposable.add(loadImageDisposable);
    }

    private Single<Bitmap> loadImage(String filePath) {
        return Single.fromCallable(() -> BitmapUtils.getSampledBitmap(filePath, imageWidth,
                imageHeight));
    }

    private void showToast(@StringRes int resId) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        compositeDisposable.dispose();

        if (!isPortraitForced) {
            setLockScreenOrientation(false);
        }
    }

    protected void setLockScreenOrientation(boolean lock) {
        if (Build.VERSION.SDK_INT >= 18) {
            setRequestedOrientation(lock ? ActivityInfo.SCREEN_ORIENTATION_LOCKED : ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
            return;
        }

        if (lock) {
            switch (getWindowManager().getDefaultDisplay().getRotation()) {
                case Surface
                        .ROTATION_0:
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                    break;
                case Surface
                        .ROTATION_90:
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                    break;
                case Surface
                        .ROTATION_180:
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT);
                    break;
                case Surface
                        .ROTATION_270:
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
                    break;
            }
        } else
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
    }

    public void increaseOpTimes() {
        numberOfOperations++;
        isBeenSaved = false;
    }

    public boolean canAutoExit() {
        return isBeenSaved || numberOfOperations == 0;
    }

    public void resetOpTimes() {
        isBeenSaved = true;
    }

    public Bitmap getMainBit() {
        return mainBitmap;
    }



    public void applyCropImage() {
        compositeDisposable.add(getCroppedBitmap()
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe(subscriber -> loadingDialog.show())
                .doFinally(() -> loadingDialog.dismiss())
                .subscribe(bitmap -> {
                    changeMainBitmap(bitmap, true);
                    releaseCrop();
                }, e -> {
                    e.printStackTrace();
                    releaseCrop();
                    Toast.makeText(this, "Error while saving image", Toast.LENGTH_SHORT).show();
                }));
    }

    private Single<Bitmap> getCroppedBitmap() {
        return Single.fromCallable(() -> cropPanel.getCroppedImage());
    }




    public void applyRotateImage() {
        if (rotatePanel.getRotateAngle() == 0 || (rotatePanel.getRotateAngle() % 360) == 0) {
            releaseState();
        } else {
            compositeDisposable.clear();
            Disposable applyRotationDisposable = applyRotation(getMainBit())
                    .subscribeOn(Schedulers.computation())
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnSubscribe(subscriber -> loadingDialog.show())
                    .doFinally(() -> loadingDialog.dismiss())
                    .subscribe(processedBitmap -> {
                        if (processedBitmap == null)
                            return;

                        applyAndExit(processedBitmap);
                    }, e -> {
                        // Do nothing on error
                    });

            compositeDisposable.add(applyRotationDisposable);
        }
    }

    private Single<Bitmap> applyRotation(Bitmap sourceBitmap) {
        return Single.fromCallable(() -> {
            RectF imageRect = rotatePanel.getImageNewRect();
            Bitmap resultBitmap = Bitmap.createBitmap((int) imageRect.width(),
                    (int) imageRect.height(), Bitmap.Config.ARGB_4444);

            Canvas canvas = new Canvas(resultBitmap);
            int w = sourceBitmap.getWidth() >> 1;
            int h = sourceBitmap.getHeight() >> 1;

            float centerX = imageRect.width() / 2;
            float centerY = imageRect.height() / 2;

            float left = centerX - w;
            float top = centerY - h;

            RectF destinationRect = new RectF(left, top, left + sourceBitmap.getWidth(), top
                    + sourceBitmap.getHeight());
            canvas.save();
            canvas.rotate(
                    rotatePanel.getRotateAngle(),
                    imageRect.width() / 2,
                    imageRect.height() / 2
            );

            canvas.drawBitmap(
                    sourceBitmap,
                    new Rect(
                            0,
                            0,
                            sourceBitmap.getWidth(),
                            sourceBitmap.getHeight()),
                    destinationRect,
                    null);
            canvas.restore();
            return resultBitmap;
        });
    }

    private void applyAndExit(Bitmap resultBitmap) {
        changeMainBitmap(resultBitmap, true);
        releaseState();
    }

}
