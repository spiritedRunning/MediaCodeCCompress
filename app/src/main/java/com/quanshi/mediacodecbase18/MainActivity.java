package com.quanshi.mediacodecbase18;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.quanshi.mediacodecbase18.CompressUtil.MediaCompressManager;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private Context mContext;
    private final int REQUEST_CODE_PICK = 1;

    private AsyncTask compressTask;
    private File outputFile;
    private ProgressBar progressBar;
    private long startTime, endTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        progressBar = (ProgressBar) findViewById(R.id.progressBar);

        listAllCodecs();
    }


    public void openFile(View view) {
        Intent intent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
        intent.setDataAndType(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "video/*");
        startActivityForResult(intent, REQUEST_CODE_PICK);
    }

    public void cancelCompress(View view) {
        compressTask.cancel(true);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CODE_PICK: {
                if (resultCode == RESULT_OK) {
                    File sampleDir = new File(Environment.getExternalStorageDirectory() + File.separator + "compressed");
                    if (!sampleDir.exists()) {
                        sampleDir.mkdirs();
                    }
                    try {
                        outputFile = File.createTempFile("compressed", ".mp4", sampleDir);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    ContentResolver resolver = getContentResolver();
                    final ParcelFileDescriptor parcelFileDescriptor;
                    try {
                        parcelFileDescriptor = resolver.openFileDescriptor(data.getData(), "r");
                    } catch (FileNotFoundException e) {
                        Log.w("Could not open '" + data.getDataString() + "'", e);
                        Toast.makeText(MainActivity.this, "File not found.", Toast.LENGTH_LONG).show();
                        return;
                    }

                    startTime = SystemClock.uptimeMillis();
                    if (parcelFileDescriptor == null || outputFile == null) {
                        Log.e(TAG, "target file is null");
                        return;
                    }
                    progressBar.setVisibility(View.VISIBLE);
                    Log.i(TAG, "target output file: " + outputFile);
                    final FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
                    compressTask = MediaCompressManager.getInstance().compressVideoStream(fileDescriptor,
                            outputFile.getAbsolutePath(),
                            new MediaCompressManager.CompressListener() {
                                @Override
                                public void onTranscodeCompleted() {
                                    endTime = SystemClock.uptimeMillis();
                                    Log.d(TAG, "compress took " + (endTime - startTime) + "ms");
                                    onCompressFinished(true, "compressed file placed on " + outputFile, parcelFileDescriptor);
                                    startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.fromFile(outputFile), "video/mp4"));
                                }

                                @Override
                                public void onTranscodeCanceled() {
                                    deleteFileCustom(outputFile.getAbsolutePath());
                                    onCompressFinished(false, "compress canceled.", parcelFileDescriptor);
                                }

                                @Override
                                public void onTranscodeFailed(int failReason) {
                                    Log.e(TAG, "compress failed reason: " + failReason);
                                    deleteFileCustom(outputFile.getAbsolutePath());
                                    onCompressFinished(false, "compress error occurred.", parcelFileDescriptor);
                                }
                            });

                }
                break;
            }
            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }


    private void onCompressFinished(boolean isSuccess, String toastMessage, ParcelFileDescriptor parcelFileDescriptor) {
//        progressBar.setIndeterminate(false);
//        progressBar.setProgress(isSuccess ? PROGRESS_BAR_MAX : 0);

        progressBar.setVisibility(View.GONE);
        Toast.makeText(MainActivity.this, toastMessage, Toast.LENGTH_LONG).show();
        try {
            parcelFileDescriptor.close();
        } catch (IOException e) {
            Log.w("Error while closing", e);
        }
    }


    public class mHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
        }
    }

    public void deleteFileCustom(String filePath) {
        if (filePath == null) return;

        File file = new File(filePath);
        if (file.exists()) {
            if (!file.delete()) {
                // 若FileAPI删除失败，用命令删除
                try {
                    Runtime.getRuntime().exec("rm -r -f " + filePath);
                } catch (IOException e) {
                    Log.w(TAG, "deleteFile->delete file by rm: " + e);
                }
            }
        }
    }


    private void listAllCodecs() {
        int numCodecs = MediaCodecList.getCodecCount();
        List<MediaCodecInfo> encoderList = new ArrayList<>();
        List<MediaCodecInfo> decoderList = new ArrayList<>();

        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            if (codecInfo.isEncoder()) {
                encoderList.add(codecInfo);
            } else {
                decoderList.add(codecInfo);
            }
        }

        for (MediaCodecInfo info : encoderList) {
            Log.i(TAG, "encoder: " + info.getName());
        }
        for (MediaCodecInfo info : decoderList) {
            Log.i(TAG, "decoder: " + info.getName());
        }
    }
}
