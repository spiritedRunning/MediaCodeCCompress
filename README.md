# Compress Instruction

This demo can only transcode video from mp4 to mp4(h.264). Source file is captured by smartphone itself. 

Princple:
1. Using "video/avc" video codec.
2. Reduce video resolution to 640x480, bitrate to 500k, frame rate to 30, I frame interval is 3.
3. Audio frame is keep the same from source to target.

Reference:

https://developer.android.com/reference/android/media/MediaCodec.html
http://bigflake.com/mediacodec/
https://qiita.com/yuya_presto/items/d48e29c89109b746d000#mediaformat
