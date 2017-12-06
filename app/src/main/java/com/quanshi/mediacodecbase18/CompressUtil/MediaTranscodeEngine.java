package com.quanshi.mediacodecbase18.CompressUtil;


import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.util.Log;

import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;


/**
 * 视频压缩工具类
 * <p>
 * Created by zhen.liu on 18,September,2017.
 */
public class MediaTranscodeEngine {
    private static final String TAG = "MediaTranscodeEngine";

    private static final double PROGRESS_UNKNOWN = -1.0;
    private static final long PROGRESS_INTERVAL_STEPS = 100;
    private static final long SLEEP_TO_WAIT_TRACK_TRANSCODERS = 10;

    // Refer: http://en.wikipedia.org/wiki/H.264/MPEG-4_AVC#Profiles
    private static final byte PROFILE_IDC_BASELINE = 66;

    private FileDescriptor mInputFileDescriptor;
    private TrackTranscoder mVideoTrackTranscoder;
    private TrackTranscoder mAudioTrackTranscoder;
    private MediaExtractor mExtractor;
    private MediaMuxer mMuxer;
    private volatile double mProgress;
    private long mDurationUs;

    private static final String MIME_TYPE = "video/avc";
    private static final int TARGET_WIDTH = 640;
    private static final int TARGET_HEIGHT = 480;

    private static final int TARGET_BITRATE = 500 * 1000;

    public MediaTranscodeEngine() {
    }

    public void setDataSource(FileDescriptor fileDescriptor) {
        mInputFileDescriptor = fileDescriptor;
    }

    /**
     * Returns the first codec capable of encoding the specified MIME type, or null if no
     * match was found.
     */
    private static MediaCodecInfo selectCodec(String mimeType) {
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            if (!codecInfo.isEncoder()) {
                Log.d(TAG, "support decoder: " + codecInfo.getName());
                continue;
            }
            String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(mimeType)) {
                    return codecInfo;
                }
            }
        }
        return null;
    }

    private void setupMetadata() throws IOException {
        MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
        mediaMetadataRetriever.setDataSource(mInputFileDescriptor);

        String rotationString = mediaMetadataRetriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
        try {
            mMuxer.setOrientationHint(Integer.parseInt(rotationString));
        } catch (NumberFormatException e) {
            // skip
        }

        // TODO: parse ISO 6709
        // String locationString = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION);
        // mMuxer.setLocation(Integer.getInteger(rotationString, 0));

        try {
            mDurationUs = Long.parseLong(mediaMetadataRetriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION)) * 1000;
        } catch (NumberFormatException e) {
            mDurationUs = -1;
        }
        Log.d(TAG, "Duration (us): " + mDurationUs);
    }

    public MediaFormat createVideoOutputFormat(MediaFormat inputFormat) {
        MediaCodecInfo codecInfo = selectCodec(MIME_TYPE);
        if (codecInfo == null) {
            // Don't fail CTS if they don't have an AVC codec (not here, anyway).
            Log.e(TAG, "Unable to find an appropriate codec for " + MIME_TYPE);
            return null;
        }
        Log.d(TAG, "found codec: " + codecInfo.getName());

        int width = inputFormat.getInteger(MediaFormat.KEY_WIDTH);
        int height = inputFormat.getInteger(MediaFormat.KEY_HEIGHT);
        int longer, shorter, outWidth, outHeight;
        if (width >= height) {
            longer = width;
            shorter = height;
            outWidth = TARGET_WIDTH;
            outHeight = TARGET_HEIGHT;
        } else {
            shorter = width;
            longer = height;
            outWidth = TARGET_HEIGHT;
            outHeight = TARGET_WIDTH;
        }

        /*
        if (longer * 9 != shorter * 16) {
            throw new OutputFormatUnavailableException("This video is not 16:9, and is not able to transcode. " +
                    "(" + width + "x" + height + ")");
        }
        */

        if (shorter <= TARGET_HEIGHT || longer <= TARGET_WIDTH) {
//            Log.d(TAG, "This video is less or equal to 720p, pass-through. (" + width + "x" + height + ")");
            Log.i(TAG, "video is no need to compress, video height: " + shorter + ", width: " + longer);
            return null;
        }

        // H.264 Advanced Video Coding
        MediaFormat format = MediaFormat.createVideoFormat("video/avc", outWidth, outHeight);
        format.setInteger(MediaFormat.KEY_BIT_RATE, TARGET_BITRATE);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 3);  // I帧间隔3s
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        return format;
    }

    private void setupTrackTranscoders() {
        TrackResult trackResult = getFirstVideoAndAudioTrack(mExtractor);

        MediaFormat videoOutputFormat = createVideoOutputFormat(trackResult.mVideoTrackFormat);
        if (videoOutputFormat == null) {
            throw new RuntimeException("pass-through for both video and audio. No transcoding is necessary.");
        }
        QueuedMuxer queuedMuxer = new QueuedMuxer(mMuxer, new QueuedMuxer.Listener() {
            @Override
            public void onDetermineOutputFormat() {
//                validateVideoOutputFormat(mVideoTrackTranscoder.getDeterminedFormat());
//                validateAudioOutputFormat(mAudioTrackTranscoder.getDeterminedFormat());
            }
        });

        mVideoTrackTranscoder = new VideoTrackTranscoder(mExtractor, trackResult.mVideoTrackIndex,
                videoOutputFormat, queuedMuxer);
        mVideoTrackTranscoder.setup();

        mAudioTrackTranscoder = new PassThroughTrackTranscoder(mExtractor, trackResult.mAudioTrackIndex,
                queuedMuxer, QueuedMuxer.SampleType.AUDIO);
        mAudioTrackTranscoder.setup();

        mExtractor.selectTrack(trackResult.mVideoTrackIndex);
        mExtractor.selectTrack(trackResult.mAudioTrackIndex);
    }

    private void runPipelines() {
        long loopCount = 0;
        if (mDurationUs <= 0) {
//            double progress = PROGRESS_UNKNOWN;
//            mProgress = progress;
//            if (mProgressCallback != null) mProgressCallback.onProgress(progress); // unknown
        }
        while (!(mVideoTrackTranscoder.isFinished() && mAudioTrackTranscoder.isFinished())) {
            boolean stepped = mVideoTrackTranscoder.stepPipeline() || mAudioTrackTranscoder.stepPipeline();
            loopCount++;
            if (mDurationUs > 0 && loopCount % PROGRESS_INTERVAL_STEPS == 0) {
                double videoProgress = mVideoTrackTranscoder.isFinished() ? 1.0 : Math.min(1.0,
                        (double) mVideoTrackTranscoder.getWrittenPresentationTimeUs() / mDurationUs);
                double audioProgress = mAudioTrackTranscoder.isFinished() ? 1.0 : Math.min(1.0,
                        (double) mAudioTrackTranscoder.getWrittenPresentationTimeUs() / mDurationUs);
                double progress = (videoProgress + audioProgress) / 2.0;
                mProgress = progress;

//                String proStr = String.format(Locale.CHINA, "progress: %.2f", progress * 100);
                Log.d(TAG, "current progress: " + progress * 100);
//                if (mProgressCallback != null) mProgressCallback.onProgress(progress);
            }
            if (!stepped) {
                try {
                    Thread.sleep(SLEEP_TO_WAIT_TRACK_TRANSCODERS);
                } catch (InterruptedException e) {
                    // nothing to do
                }
            }
        }
    }

    public static void validateVideoOutputFormat(MediaFormat format) {
        String mime = format.getString(MediaFormat.KEY_MIME);
        // Refer: http://developer.android.com/guide/appendix/media-formats.html#core
        // Refer: http://en.wikipedia.org/wiki/MPEG-4_Part_14#Data_streams
        if (!MediaConstants.MIMETYPE_VIDEO_AVC.equals(mime)) {
            throw new RuntimeException("Video codecs other than AVC is not supported, actual mime type: " + mime);
        }
        ByteBuffer spsBuffer = AvcCsdUtils.getSpsBuffer(format);
        byte profileIdc = AvcSpsUtils.getProfileIdc(spsBuffer);
        if (profileIdc != PROFILE_IDC_BASELINE) {
            throw new RuntimeException("Non-baseline AVC video profile is not supported by Android OS, " +
                    "actual profile_idc: " + profileIdc);
        }
    }

    public static void validateAudioOutputFormat(MediaFormat format) {
        String mime = format.getString(MediaFormat.KEY_MIME);
        if (!MediaConstants.MIMETYPE_AUDIO_AAC.equals(mime)) {
            throw new RuntimeException("Audio codecs other than AAC is not supported, actual mime type: " + mime);
        }
    }

    public static class TrackResult {
        private TrackResult() {
        }

        public int mVideoTrackIndex;
        public String mVideoTrackMime;
        public MediaFormat mVideoTrackFormat;
        public int mAudioTrackIndex;
        public String mAudioTrackMime;
        public MediaFormat mAudioTrackFormat;
    }

    public static TrackResult getFirstVideoAndAudioTrack(MediaExtractor extractor) {
        TrackResult trackResult = new TrackResult();
        trackResult.mVideoTrackIndex = -1;
        trackResult.mAudioTrackIndex = -1;
        int trackCount = extractor.getTrackCount();
        for (int i = 0; i < trackCount; i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (trackResult.mVideoTrackIndex < 0 && mime.startsWith("video/")) {
                trackResult.mVideoTrackIndex = i;
                trackResult.mVideoTrackMime = mime;
                trackResult.mVideoTrackFormat = format;
            } else if (trackResult.mAudioTrackIndex < 0 && mime.startsWith("audio/")) {
                trackResult.mAudioTrackIndex = i;
                trackResult.mAudioTrackMime = mime;
                trackResult.mAudioTrackFormat = format;
            }
            if (trackResult.mVideoTrackIndex >= 0 && trackResult.mAudioTrackIndex >= 0) break;
        }
        if (trackResult.mVideoTrackIndex < 0 || trackResult.mAudioTrackIndex < 0) {
            throw new IllegalArgumentException("extractor does not contain video and/or audio tracks.");
        }
        return trackResult;
    }

    /**
     * 主要通过降低视频流的比特率，分辨率，帧率达到压缩的目的，音频流不做处理，直接输出到目标文件。
     *
     * @param outputPath 目标输出路径
     * @throws IOException
     * @throws InterruptedException
     */
    public int transcodeVideo(String outputPath) throws IOException, InterruptedException {
        if (outputPath == null) {
            Log.e(TAG, "Output path cannot be null.");
            throw new NullPointerException("Output path cannot be null.");
        }
        if (mInputFileDescriptor == null) {
            Log.e(TAG, "Data source is not set");
            throw new IllegalStateException("Data source is not set.");
        }
        try {
            // NOTE: use single extractor to keep from running out audio track fast.
            mExtractor = new MediaExtractor();
            mExtractor.setDataSource(mInputFileDescriptor);
            mMuxer = new MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            setupMetadata();
            setupTrackTranscoders();
            runPipelines();
            mMuxer.stop();
        } finally {
            try {
                if (mVideoTrackTranscoder != null) {
                    mVideoTrackTranscoder.release();
                    mVideoTrackTranscoder = null;
                }
                if (mAudioTrackTranscoder != null) {
                    mAudioTrackTranscoder.release();
                    mAudioTrackTranscoder = null;
                }
                if (mExtractor != null) {
                    mExtractor.release();
                    mExtractor = null;
                }
            } catch (RuntimeException e) {
                // Too fatal to make alive the app, because it may leak native resources.
                //noinspection ThrowFromFinallyBlock
                Log.e(TAG, "Could not shutdown extractor, codecs and muxer pipeline.");
                throw new Error("Could not shutdown extractor, codecs and muxer pipeline.", e);
            }
            try {
                if (mMuxer != null) {
                    mMuxer.release();
                    mMuxer = null;
                }
            } catch (RuntimeException e) {
                Log.e(TAG, "Failed to release muxer.", e);
                return MediaConstants.MEDIA_TRANSCODE_FAIL;
            }
        }

        return MediaConstants.MEDIA_TRANSCODE_SUCC;
    }


}
