package com.github.barteksc.sample;

import android.graphics.Bitmap;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
//import android.media.MediaMuxer;
import android.os.Environment;
import android.util.Log;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import hole.Hole;

public class BitmapToH264Encoder {

    private static final String TAG = "BitmapToH264Encoder";
    private static final String MIME_TYPE = "video/avc";
    private int VIDEO_WIDTH = 720;
    private int VIDEO_HEIGHT = 1280;
    private static final int FRAME_RATE = 30;
    private static final int IFRAME_INTERVAL = 1;
    private static final long TIMEOUT_USEC = 10000;

    private MediaCodec mediaCodec;
    //private MediaMuxer mediaMuxer;
    private FileOutputStream outputStream;
    private boolean isStarted;

    public BitmapToH264Encoder() {
        try {
            String outputPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/outputNew.264";
            outputStream = new FileOutputStream(outputPath);
           // mediaMuxer = new MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void startEncoder() {
        try {
            Hole.setFecEnable(true);
            Hole.startConnect("","47.92.86.188:9090","turn://admin:123456@47.92.86.188","ctrl","whatever");
            MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, VIDEO_WIDTH, VIDEO_HEIGHT);
            format.setInteger(MediaFormat.KEY_BIT_RATE, 500000);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible );
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);

            mediaCodec = MediaCodec.createEncoderByType(MIME_TYPE);
            mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mediaCodec.start();
            isStarted = true;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void stopEncoder() {
        Hole.stopConnect("whatever");
        if (mediaCodec != null) {
            mediaCodec.stop();
            mediaCodec.release();
            mediaCodec = null;
        }
        /*if (mediaMuxer != null) {
            mediaMuxer.stop();
            mediaMuxer.release();
            mediaMuxer = null;
        }*/
        if (outputStream != null) {
            try {
                outputStream.flush();
                outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            outputStream = null;
        }
        isStarted = false;
    }

    public void encodeBitmap(Bitmap bitmap,int w,int h) {
        if (!isStarted || mediaCodec == null) {
            Log.e(TAG, "Encoder not properly initialized.");
            return;
        }

        Log.d("myTest","encodeBitmap...");
        ByteBuffer[] inputBuffers = mediaCodec.getInputBuffers();
        ByteBuffer[] outputBuffers = mediaCodec.getOutputBuffers();

        byte[] imageData = bitmapToYUV(bitmap,w,h);
        long presentationTimeUs = System.nanoTime() / 1000;
        Log.d("myTest","encodeBitmap 111...");

        try {
            int inputBufferIndex = mediaCodec.dequeueInputBuffer(TIMEOUT_USEC);
            if (inputBufferIndex >= 0) {
                ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                inputBuffer.clear();
                inputBuffer.put(imageData);

                mediaCodec.queueInputBuffer(inputBufferIndex, 0, imageData.length, presentationTimeUs, 0);
            }

            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC);
            while (outputBufferIndex >= 0) {
                ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];

                byte[] outData = new byte[bufferInfo.size];
                outputBuffer.get(outData);

               // outputStream.write(outData, 0, outData.length);
              //  mediaMuxer.writeSampleData(0, outputBuffer, bufferInfo);
                Log.d("myTest","write data");
                Hole.startSendH264ByteQueue("whatever",outData);

                mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
                outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private byte[] bitmapToYUV(Bitmap bitmap,int w,int h) {
       // VIDEO_WIDTH = w;
     //   VIDEO_HEIGHT = h;
        Log.d("myTest", "bitmapToYUV, width: " + w + ", height: " + h);
        int[] argb = new int[w * h];
        bitmap.getPixels(argb, 0, w, 0, 0, w, h);

        byte[] yuv = new byte[w * h * 3 / 2];
        encodeYUV420SP(yuv, argb, w, h);

        return yuv;
    }

    private void convertArgbToI420(byte[] i420, int[] argb, int width, int height) {
        final int frameSize = width * height;
        int yIndex = 0;                   // Y start index
        int uIndex = frameSize;           // U statt index
        int vIndex = frameSize * 5 / 4; // V start index: w*h*5/4
        int a, R, G, B, Y, U, V;
        int index = 0;
        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {
                a = (argb[index] & 0xff000000) >> 24; //  is not used obviously
                R = (argb[index] & 0xff0000) >> 16;
                G = (argb[index] & 0xff00) >> 8;
                B = (argb[index] & 0xff) >> 0;
                // well known RGB to YUV algorithm
                Y = ((66 * R + 129 * G + 25 * B + 128) >> 8) + 16;
                U = ((-38 * R - 74 * G + 112 * B + 128) >> 8) + 128;
                V = ((112 * R - 94 * G - 18 * B + 128) >> 8) + 128;
                // I420(YUV420p) -> YYYYYYYY UU VV
                i420[yIndex++] = (byte) ((Y < 0) ? 0 : ((Y > 255) ? 255 : Y));
                if (j % 2 == 0 && i % 2 == 0) {
                    i420[uIndex++] = (byte) ((U < 0) ? 0 : ((U > 255) ? 255 : U));
                    i420[vIndex++] = (byte) ((V < 0) ? 0 : ((V > 255) ? 255 : V));
                }
                index++;
            }
        }
    }

    private void encodeYUV420SP(byte[] yuv420sp, int[] argb, int width, int height) {
        final int frameSize = width * height;

        int yIndex = 0;
        int uvIndex = frameSize;

        int a, R, G, B, Y, U, V;
        int index = 0;
        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {
                a = (argb[index] & 0xff000000) >> 24; // a is not used obviously
                R = (argb[index] & 0xff0000) >> 16;
                G = (argb[index] & 0xff00) >> 8;
                B = (argb[index] & 0xff) >> 0;

                // well known RGB to YUV algorithm
                Y = ((66 * R + 129 * G + 25 * B + 128) >> 8) + 16;
                U = ((-38 * R - 74 * G + 112 * B + 128) >> 8) + 128;
                V = ((112 * R - 94 * G - 18 * B + 128) >> 8) + 128;

                // NV21 has a plane of Y and interleaved planes of VU each sampled by a factor of 2
                //    meaning for every 4 Y pixels there are 1 V and 1 U.  Note the sampling is every other
                //    pixel AND every other scanline.
                yuv420sp[yIndex++] = (byte) ((Y < 0) ? 0 : ((Y > 255) ? 255 : Y));
                if (j % 2 == 0 && index % 2 == 0) {
                    yuv420sp[uvIndex++] = (byte) ((V < 0) ? 0 : ((V > 255) ? 255 : V));
                    yuv420sp[uvIndex++] = (byte) ((U < 0) ? 0 : ((U > 255) ? 255 : U));
                }

                index++;
            }
        }
    }
}

