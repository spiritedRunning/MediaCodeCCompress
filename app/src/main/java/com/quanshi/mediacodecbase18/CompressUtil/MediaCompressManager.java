package com.quanshi.mediacodecbase18.CompressUtil;


import android.os.AsyncTask;
import android.util.Log;

import java.io.FileDescriptor;
import java.io.IOException;

/**
 * 视频压缩任务管理
 * <p>
 * Created by zhen.liu on 29,September,2017.
 */

public class MediaCompressManager {
    private static final String TAG = "MediaCompressManager";

    private static volatile MediaCompressManager mMediaCompressManager;


    public static MediaCompressManager getInstance() {
        if (mMediaCompressManager == null) {
            synchronized (MediaCompressManager.class) {
                if (mMediaCompressManager == null) {
                    mMediaCompressManager = new MediaCompressManager();
                }
            }
        }
        return mMediaCompressManager;
    }

    public AsyncTask compressVideoStream(FileDescriptor inFileDescriptor, String outPath,
                                         CompressListener listener) {

        MediaCompressTask mediaCompressTask = new MediaCompressTask();
        mediaCompressTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, inFileDescriptor, outPath, listener);
        return mediaCompressTask;
    }

    private class MediaCompressTask extends AsyncTask<Object, Void, Integer> {
        private FileDescriptor fd;
        private String outputPath;
        private CompressListener listener;

        @Override
        protected Integer doInBackground(Object... params) {
            fd = (FileDescriptor) params[0];
            outputPath = (String) params[1];
            listener = (CompressListener) params[2];

            MediaTranscodeEngine transcodeEngine = new MediaTranscodeEngine();
            transcodeEngine.setDataSource(fd);
            try {
                return transcodeEngine.transcodeVideo(outputPath);
            } catch (IOException e) {
                Log.e(TAG, "IOException exception: " + e);
                return MediaConstants.MEDIA_TRANSCODE_FAIL;
            } catch (InterruptedException e) {
                Log.e(TAG, "task is interrupted");
                return MediaConstants.MEDIA_TRANSCODE_CANCELED;
            } catch (RuntimeException e) {
                Log.e(TAG, "task failed");
                return MediaConstants.MEDIA_TRANSCODE_FAIL;
            }
        }

        @Override
        protected void onPostExecute(Integer result) {
            if (listener == null) {
                return;
            }
            if (result == MediaConstants.MEDIA_TRANSCODE_FAIL) {
                Log.e(TAG, "compress failed.");
                listener.onTranscodeFailed(result);
            } else {
                Log.i(TAG, "compress success!");
                listener.onTranscodeCompleted();
            }
        }

        @Override
        protected void onCancelled() {
            listener.onTranscodeCanceled();
        }
    }

    public interface CompressListener {
        void onTranscodeCompleted();

        void onTranscodeCanceled();

        void onTranscodeFailed(int reason);
    }
}
