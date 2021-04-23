# Compress Instruction

- 仅支持MP4文件格式压缩， 视频格式为h.264， 音频格式为aac
- 压缩方式：调整视频分辨率为 640* 480, 比特率到500k, 帧率为30， I帧间隔为3； 音频不调整
- 支持Android 4.3以上（api level 18 above）


1. Using "video/avc" video codec.
2. Reduce video resolution to 640x480, bitrate to 500k, frame rate to 30, I frame interval is 3.
3. Audio frame is keep the same from source to target.
4. Only support apiLevel 18 above(Android 4.3).

