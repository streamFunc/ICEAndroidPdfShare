/**
 * Copyright 2016 Bartosz Schiller
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.barteksc.sample;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.provider.DocumentFile;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;

import com.cmic.x.X264Encode;
import com.github.barteksc.pdfviewer.PDFView;
import com.github.barteksc.pdfviewer.listener.OnLoadCompleteListener;
import com.github.barteksc.pdfviewer.listener.OnPageChangeListener;
import com.github.barteksc.pdfviewer.listener.OnPageErrorListener;
import com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle;
import com.github.barteksc.pdfviewer.util.FitPolicy;
import com.shockwave.pdfium.PdfDocument;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.NonConfigurationInstance;
import org.androidannotations.annotations.OnActivityResult;
import org.androidannotations.annotations.OptionsItem;
import org.androidannotations.annotations.OptionsMenu;
import org.androidannotations.annotations.ViewById;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import hole.Hole;



@EActivity(R.layout.activity_main)
@OptionsMenu(R.menu.options)
public class PDFViewActivity extends AppCompatActivity implements OnPageChangeListener, OnLoadCompleteListener,
        OnPageErrorListener {

    private static final String TAG = PDFViewActivity.class.getSimpleName();
    private boolean isSharing = false;
    private boolean startOK = false;
    private boolean fileCopy = false;
    private final static int REQUEST_CODE = 42;
    public static final int PERMISSION_CODE = 42042;
    public static final int MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 1;

    public static final String SAMPLE_FILE = "sample.pdf";
    public static final String READ_EXTERNAL_STORAGE = "android.permission.READ_EXTERNAL_STORAGE";

    @ViewById
    PDFView pdfView;

    @NonConfigurationInstance
    Uri uri;

    @NonConfigurationInstance
    Integer pageNumber = 0;

    String pdfFileName;
    File pdfFile;
    ParcelFileDescriptor pfd;


    //private FileOutputStream fileOutputStream;
    //private BitmapToH264Encoder encoder = new BitmapToH264Encoder();

    //static {
        // Load the native library
       // Log.d("X264","load x264 lib");
       // System.loadLibrary("h264_encode");
    //}

    X264Encode encoder = new X264Encode() {
        @Override
        public void onReceiveNal(byte[] nal) {
            Log.d("myTest","receive nal");

            if (startOK){
                /*if (fileOutputStream != null) {
                    try {
                        Log.d("myTest","write nal");
                        fileOutputStream.write(nal);
                        fileOutputStream.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }else{
                    Log.d("myTest","open file fail");
                }*/
                Hole.startSendH264ByteQueue("whatever",nal);
            }
        }
    };

    @OptionsItem(R.id.pickFile)
    void pickFile() {
        int permissionCheck = ContextCompat.checkSelfPermission(this,
                READ_EXTERNAL_STORAGE);

        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{READ_EXTERNAL_STORAGE},
                    PERMISSION_CODE
            );

            return;
        }

        launchPicker();
    }

    @OptionsItem(R.id.share)
    void startSharing(MenuItem item) {
        if (isSharing) {
            isSharing = false;
            Log.d("myTest","stop shared file...");
            item.setTitle("开始分享");
            Hole.stopConnect("whatever");
            startOK = false;
            /*if (fileOutputStream != null) {
                try {
                    fileOutputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }*/
           // encoder.stopEncoder();
        }else{
            // 改变按钮颜色
            isSharing = true;
            item.setTitle("停止分享");
            Log.d("myTest","start shared file...");
            Hole.setFecEnable(true);
            Hole.startConnect("","47.92.86.188:9090","turn://admin:123456@47.92.86.188","ctrl","whatever");
            startOK = true;
            //encoder.startEncoder();
        }
    }



    void launchPicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/pdf");
        try {
            startActivityForResult(intent, REQUEST_CODE);
        } catch (ActivityNotFoundException e) {
            //alert user that file manager not working
            Toast.makeText(this, R.string.toast_pick_file_error, Toast.LENGTH_SHORT).show();
        }
    }

    @AfterViews
    void afterViews() {
        pdfView.setBackgroundColor(Color.LTGRAY);
        if (uri != null) {
            displayFromUri(uri);
        } else {
            displayFromAsset(SAMPLE_FILE);
        }
        setTitle(pdfFileName);
    }

    private void displayFromAsset(String assetFileName) {
        pdfFileName = assetFileName;

        pdfView.fromAsset(SAMPLE_FILE)
                .defaultPage(pageNumber)
                .onPageChange(this)
                .enableAnnotationRendering(true)
                .onLoad(this)
                .scrollHandle(new DefaultScrollHandle(this))
                .spacing(10) // in dp
                .onPageError(this)
                .pageFitPolicy(FitPolicy.BOTH)
                .load();
    }

    private void displayFromUri(Uri uri) {

        pdfFileName = getFileName(uri);
        Log.d("myTest","pdfFileName "+ pdfFileName);
      //  pdfFilePtah = getRealPathFromUri(uri);
     //   Log.d("myTest","pdfFilePtah "+ pdfFilePtah);
        pdfFile = getFileFromUri(uri);

        try{
            pfd = getContentResolver().openFileDescriptor(uri, "r");
        }catch (Exception ex) {
            ex.printStackTrace();
        }

        pdfView.fromUri(uri)
                .defaultPage(pageNumber)
                .onPageChange(this)
                .enableAnnotationRendering(true)
                .onLoad(this)
                .scrollHandle(new DefaultScrollHandle(this))
                .spacing(10) // in dp
                .onPageError(this)
                .load();
    }

    @OnActivityResult(REQUEST_CODE)
    public void onResult(int resultCode, Intent intent) {
        Log.d("myTest","onResult...........");
        if (resultCode == RESULT_OK) {
            uri = intent.getData();
            Log.d("myTest","uri is " + uri);
            displayFromUri(uri);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    @Override
    public void onPageChanged(int page, int pageCount) {
        pageNumber = page;
        setTitle(String.format("%s %s / %s", pdfFileName, page + 1, pageCount));
        String directoryPath = Environment.getExternalStorageDirectory().getAbsolutePath();
        String fullPath = directoryPath +"/"+pdfFileName;

        //String fullPath =  directoryPath +pdfFileName;

       // File pdfFile = new File(fullPath);
        if (!fileCopy)
        {
            pdfFile = copyPdfFromAssetsToCache(pdfFileName);
            fileCopy = true;
        }

        try {
            Log.d("myTest","pdfFile: " + pdfFile+" fullPath: " + fullPath);
            PdfRenderer renderer = null;
            if (uri != null){
               ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "r");
                renderer = new PdfRenderer(pfd);
            }else{
                renderer = new PdfRenderer(ParcelFileDescriptor.open(new File(String.valueOf(pdfFile)), ParcelFileDescriptor.MODE_READ_ONLY));
            }

            PdfRenderer.Page page1 = null;
            page1 = renderer.openPage(pageNumber);

            Bitmap bitmap = null;
            bitmap = Bitmap.createBitmap(720, 1280, Bitmap.Config.ARGB_8888);

            Canvas canvas = new Canvas(bitmap);canvas.drawColor(Color.WHITE);canvas.drawBitmap(bitmap, 0, 0, null);
            page1.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            Log.d("myTest", "start save image num: "+pageNumber);

            // 保存 Bitmap 到文件
          //  File outputFile = new File("/sdcard/", "image_" + pageNumber + ".png");
          //  FileOutputStream outputStream = new FileOutputStream(outputFile);
          //  bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);

            int result = encoder.encodeBitmapToH264(bitmap, 720, 1280,0);
           /* if (startOK){
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    encoder.encodeBitmap(bitmap,720,1280);
                }
            }*/


            Log.d("myTest", "end..");
                // 关闭资源
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                page1.close();
            }
           // outputStream.close();
            renderer.close();

        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private File copyPdfFromAssetsToCache(String fileName) {
        AssetManager assetManager = getAssets();
        InputStream in = null;
        OutputStream out = null;
        File outFile = new File(getCacheDir(), fileName);

        try {
            in = assetManager.open(fileName);
            out = new FileOutputStream(outFile);
            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        } catch (IOException e) {
            e.printStackTrace(); // 或者进行适当的异常处理
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
                if (out != null) {
                    out.close();
                }
            } catch (IOException e) {
                e.printStackTrace(); // 关闭流时可能会抛出异常，进行适当处理
            }
        }

        return outFile;
    }


    // 根据 Uri 获取文件的实际路径
    private String getRealPathFromUri(Uri uri) {
        String filePath = null;
        FileDescriptor fileDescriptor = null;

        try {
            // 尝试打开文件描述符
            fileDescriptor = getContentResolver().openFileDescriptor(uri, "r").getFileDescriptor();

            // 可以在这里进一步处理 fileDescriptor，例如通过 FileInputStream 读取文件内容
            if (fileDescriptor != null) {
                FileInputStream inputStream = new FileInputStream(fileDescriptor);
                // 在这里处理输入流，可以获取文件内容或者进一步操作
                // 这里演示如何获取文件路径
                File file = new File(uri.getPath());  // 通过 Uri 的路径创建文件对象
                filePath = file.getAbsolutePath();   // 获取文件的绝对路径
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        Log.d("myTest","getRealPathFromUri is filePath:"+filePath);
        return filePath;
    }


    private File getFileFromUri(Uri uri) {
        String filePath = null;
        FileDescriptor fileDescriptor = null;
        File file = null;

        try {
            // 尝试打开文件描述符
            fileDescriptor = getContentResolver().openFileDescriptor(uri, "r").getFileDescriptor();

            // 可以在这里进一步处理 fileDescriptor，例如通过 FileInputStream 读取文件内容
            if (fileDescriptor != null) {
                FileInputStream inputStream = new FileInputStream(fileDescriptor);
                // 在这里处理输入流，可以获取文件内容或者进一步操作
                // 这里演示如何获取文件路径
                 file = new File(uri.getPath());  // 通过 Uri 的路径创建文件对象
               // filePath = file.getAbsolutePath();   // 获取文件的绝对路径
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        Log.d("myTest","getRealPathFromUri is filePath:"+filePath);
        return file;
    }




    public String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        if (result == null) {
            result = uri.getLastPathSegment();
        }
        return result;
    }

    @Override
    public void loadComplete(int nbPages) {
        PdfDocument.Meta meta = pdfView.getDocumentMeta();
        Log.e(TAG, "title = " + meta.getTitle());
        Log.e(TAG, "author = " + meta.getAuthor());
        Log.e(TAG, "subject = " + meta.getSubject());
        Log.e(TAG, "keywords = " + meta.getKeywords());
        Log.e(TAG, "creator = " + meta.getCreator());
        Log.e(TAG, "producer = " + meta.getProducer());
        Log.e(TAG, "creationDate = " + meta.getCreationDate());
        Log.e(TAG, "modDate = " + meta.getModDate());

        printBookmarksTree(pdfView.getTableOfContents(), "-");

    }

    public void printBookmarksTree(List<PdfDocument.Bookmark> tree, String sep) {
        for (PdfDocument.Bookmark b : tree) {

            Log.e(TAG, String.format("%s %s, p %d", sep, b.getTitle(), b.getPageIdx()));

            if (b.hasChildren()) {
                printBookmarksTree(b.getChildren(), sep + "-");
            }
        }
    }

    /**
     * Listener for response to user permission request
     *
     * @param requestCode  Check that permission request code matches
     * @param permissions  Permissions that requested
     * @param grantResults Whether permissions granted
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_CODE) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launchPicker();
            }
        }
    }

    @Override
    public void onPageError(int page, Throwable t) {
        Log.e(TAG, "Cannot load page " + page);
    }


}


