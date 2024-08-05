package com.cmic.x;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class X264Encode {
    protected X264Encode(){
        m_nDuration=0;
        m_caches=new ArrayList<>();
        m_nByteNumber=0;
    }

    @Override
    protected void finalize() throws Throwable{
       if(!m_caches.isEmpty())m_caches.clear();
    }

    static {
        System.loadLibrary("h264_encode");
    }

    private List<byte[]> m_caches;

    private int m_nByteNumber;

    private int m_nDuration;

    /**
     * notes:if the width or height of image does not match the (expected) width or height
     * then will scare the image including (expected) width or height
     * only supportive size:540*960/720*1280
     * @param bitmap:Bitmap object;
     * @param width:expected width;
     * @param height:expected height;
     * @duration: unit second
     * @return negative if failure;others if success
     */
    public int encodeBitmapToH264(Bitmap bitmap, int width, int height,int duration) {
        if(width==480 || height==640){
            Log.e("X264",String.format("It does not support the width=%d,height=%d",
                    width,height));
            return -1;
        }
        m_nDuration=duration;

        //long st=System.currentTimeMillis();
        Bitmap compressBmp = compressBitmapToResolution(bitmap, width, height);
        byte[] yuv = bmp2Yuv(compressBmp);
//        long et=System.currentTimeMillis();
//        Log.d("X264", String.format("Scaling picture takes %d ms",et-st));

//        long st2=System.currentTimeMillis();
        int ret=EncodeJNI(width, height, yuv, "_onReceiveNal",duration);
//        long et2=System.currentTimeMillis();
//        Log.d("X264", String.format("Encoding takes %d ms",et2-st2));

        if(m_nDuration==0){
            return ret;
        }

        if(m_nByteNumber<=0){
            Log.e("X264",String.format("There is not encode_data to use"));
            return -1;
        }

        byte[] td=new byte[m_nByteNumber];
        if(td!=null && td.length==m_nByteNumber){//can find a block memory
            int count=0;
            for(byte[] d:m_caches){
                System.arraycopy(d,0,td,count,d.length);
                count+=d.length;
            }
            for(int i=0;i<m_nDuration;i++){
                onReceiveNal(td);
            }
        }else{//cant find a block memory
            for(int i=0;i<m_nDuration;i++){
                for(byte[] d:m_caches){
                    onReceiveNal(d);
                }
            }
        }
        //reset for next time
        if(!m_caches.isEmpty())m_caches.clear();
        m_nByteNumber=0;

        return ret;
    }

    private void _onReceiveNal(byte[] nal,int flag){
        if(nal.length<=0){
            Log.w("x264","nal is null and ignore this time");
            return;
        }

        if(m_nDuration==0){
            onReceiveNal(nal);
            return;
        }

        byte[] temp=new byte[nal.length];
        System.arraycopy(nal,0,temp,0,nal.length);
        m_caches.add(temp);
        m_nByteNumber+=nal.length;

    }


    /**
     * notes:It may be called a few times but
     * all the data must be written before the return of "encodeBitmapToH264" function.
     * key point:you can write feature here such as saving the data into file.
     * but overriding is a better way as follow:
     *  X264Encode encoder = new X264Encode(){
     *                     @Override
     *                     public void onReceiveNal(byte[] nal){
     *                         //your code
     *                     }
     *                 };
     * suggestion for high performance: save the nal in memory and write IO at the end.
     * write IO once instead of a few times
     * @param nal the unit data of H264
     */
    public void onReceiveNal(byte[] nal){}



    /**
     * native c++ interface
     * @param width
     * @param height
     * @param yuv
     * @param fn
     * @return
     */
    private native int EncodeJNI(int width, int height, byte[] yuv, String fn,int duration);

    private Bitmap compressBitmapToResolution(Bitmap originalBitmap, int targetWidth, int targetHeight) {
        // 获取原始图片的尺寸
        int originalWidth = originalBitmap.getWidth();
        int originalHeight = originalBitmap.getHeight();
        // 计算缩放比例
        float scaleX = (float) targetWidth / originalWidth;
        float scaleY = (float) targetHeight / originalHeight;
        // 创建缩放矩阵
        Matrix matrix = new Matrix();
        matrix.postScale(scaleX, scaleY);
        // 创建目标分辨率的Bitmap
        return Bitmap.createBitmap(originalBitmap, 0, 0, originalWidth, originalHeight, matrix, true);
    }

    private byte[] bmp2Yuv(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int size = width * height;
        int[] pixels = new int[size];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        byte[] data = new byte[width * height * 3 / 2];
        convertArgbToI420(data, pixels, width, height);
        return data;
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
}
