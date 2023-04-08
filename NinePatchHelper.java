package chat.meme.inke.utils;

import android.app.Activity;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.NinePatch;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.NinePatchDrawable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import android.util.DisplayMetrics;
import android.util.Log;

import com.nett.meme.common.download.DownloadManager;
import com.nett.meme.common.utils.downloader.exception.HttpException;
import com.nett.meme.common.utils.downloader.http.ResponseInfo;
import com.nett.meme.common.utils.downloader.http.callback.RequestCallBack;

import java.io.File;

import com.nett.meme.common.image.FrescoHelper;

import static chat.meme.inke.BuildConfig.DEBUG;

/**
 * 用aapt进行编译：
 * 例如编译整个目录的
 * aapt c -v -S ../app/src/debug/res/drawable-xxhdpi/ -C out
 */
public class NinePatchHelper implements LifecycleObserver {
    private static final String TAG = NinePatchHelper.class.getSimpleName();
    private final LoadCallback mLoadCallback;
    private Lifecycle mLifecycle;
    private final float mDensityDpi;
    private final String dir;

    public NinePatchHelper(@NonNull Context context, LoadCallback loadCallback) {
        mLoadCallback = loadCallback;
        //create path
        dir = context.getCacheDir().getAbsolutePath() + "/nine-patch/";
        File dirFile = new File(dir);
        if (!dirFile.exists()) {
            dirFile.mkdirs();
        }

        //read system densityDpi
        DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
        mDensityDpi = displayMetrics.densityDpi;
        try {
            Activity activity = ResourceUtils.getActivity(context);
            if (activity instanceof FragmentActivity) {
                mLifecycle = ((FragmentActivity) activity).getLifecycle();
                mLifecycle.addObserver(this);
            }
        } catch (Exception e) {
            Log.w(TAG, "NinePatchHelper: ", e);
        }
    }

    public void loadNinePatchPng(String ninePatchPngUrl) {
        if (ninePatchPngUrl == null)
            return;
        ninePatchPngUrl = FrescoHelper.replaceSrc(ninePatchPngUrl);
        String[] split = ninePatchPngUrl.split("/");
        String fileName = split[split.length - 1];
        File file = new File(dir + fileName);
        if (file.exists()) {
            callbackBitmap(BitmapFactory.decodeFile(file.getPath(), getOptions()));
        } else {
            if (!DownloadManager.getInstance().isFileDownloading(ninePatchPngUrl)) {
                String tmpFilePath = dir + fileName + ".tmp";
                DownloadManager.getInstance().download(DownloadManager.getInstance().generateTaskId(), ninePatchPngUrl, tmpFilePath, new RequestCallBack<File>() {
                    @Override
                    public void onSuccess(ResponseInfo<File> responseInfo) {
                        File tmpFile = responseInfo.result;
                        processSuccess(tmpFile);
                    }

                    @Override
                    public void onFailure(HttpException error, String msg) {
                        File tmp = new File(tmpFilePath);
                        if (error.getExceptionCode() == 416) {
                            processSuccess(tmp);
                        } else {
                            if (tmp.exists()) {
                                tmp.delete();
                                if (DEBUG)
                                    Log.i(TAG, "onFailure: tmp file deleted!");
                            }

                            if (isDestroy())
                                return;

                            if (mLoadCallback != null) {
                                mLoadCallback.onLoadFailed();
                            }
                        }
                    }

                    private void processSuccess(File tmpFile) {
                        try {
                            boolean rename;
                            if (tmpFile != null) {
                                rename = tmpFile.renameTo(file);
                            } else {
                                rename = new File(tmpFilePath).renameTo(file);
                            }
                            if (DEBUG) {
                                Log.d(TAG, "onSuccess: renamed = " + rename);
                            }

                            if (isDestroy())
                                return;

                            callbackBitmap(BitmapFactory.decodeFile(file.getPath(), getOptions()));
                        } catch (Exception e) {
                            Log.w(TAG, "onSuccess: ", e);
                            if (isDestroy())
                                return;
                            if (mLoadCallback != null) {
                                mLoadCallback.onLoadFailed();
                            }
                        }
                    }
                });
            }
        }
    }

    private BitmapFactory.Options getOptions() {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inDensity = DisplayMetrics.DENSITY_XXHIGH;
        opts.inTargetDensity = (int) mDensityDpi;
        return opts;
    }

    private void callbackBitmap(Bitmap resource) {
        try {
            byte[] ninePatchChunk = resource.getNinePatchChunk();
            if (DEBUG) {
                boolean isNinePatchChunk = false;
                if (ninePatchChunk != null) {
                    isNinePatchChunk = NinePatch.isNinePatchChunk(ninePatchChunk);
                }
                Log.i(TAG, "fetch.onSuccess: ninePatchChunk = " + ninePatchChunk + ", isNinePatchChunk = " + isNinePatchChunk);
            }
            if (ninePatchChunk != null && NinePatch.isNinePatchChunk(ninePatchChunk)) {
                NinePatchDrawable ninePatchDrawable = new NinePatchDrawable(resource, ninePatchChunk, new Rect(), null);
                if (mLoadCallback != null) {
                    mLoadCallback.onDrawableReady(ninePatchDrawable);
                }
            } else {
                showAsDefaultBitmap(resource);
            }
        } catch (Throwable e) {
            showAsDefaultBitmap(resource);
        }
    }

    void showAsDefaultBitmap(Bitmap resource) {
        if (mLoadCallback != null) {
            mLoadCallback.onDrawableReady(new BitmapDrawable(resource));
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    public void onDestroyInternal() {
        if (mLifecycle != null) {
            mLifecycle.removeObserver(this);
        }
    }

    @Nullable
    Lifecycle.State getCurrentState() {
        if (mLifecycle != null) {
            return mLifecycle.getCurrentState();
        }
        return null;
    }

    boolean isDestroy() {
        Lifecycle.State currentState = getCurrentState();
        if (currentState == null)
            return false;
        return currentState == Lifecycle.State.INITIALIZED || currentState == Lifecycle.State.DESTROYED;
    }

    public interface LoadCallback {
        void onDrawableReady(Drawable drawable);

        void onLoadFailed();
    }
}
