package com.love520ovo.aivirtualphone;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {

    private static final String HOME_URL = "https://ai-virtual-phone-zeta-ruddy.vercel.app";
    private static final int REQUEST_FILE = 1001;

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private long lastBackPress = 0L;

    private class Bridge {
        @JavascriptInterface
        public void saveBase64File(String dataUrl, String filename) {
            try {
                String b64 = dataUrl;
                int comma = dataUrl.indexOf(',');
                if (comma >= 0) b64 = dataUrl.substring(comma + 1);
                byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues cv = new ContentValues();
                    cv.put(MediaStore.Downloads.DISPLAY_NAME, filename);
                    cv.put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream");
                    Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                    if (uri == null) throw new RuntimeException("insert failed");
                    OutputStream os = getContentResolver().openOutputStream(uri);
                    if (os == null) throw new RuntimeException("open stream failed");
                    os.write(bytes);
                    os.close();
                } else {
                    File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                    if (dir != null && !dir.exists()) dir.mkdirs();
                    File out = new File(dir, filename);
                    FileOutputStream fos = new FileOutputStream(out);
                    fos.write(bytes);
                    fos.close();
                }
                final String okMsg = "已保存到「下载」：" + filename;
                runOnUiThread(() -> Toast.makeText(MainActivity.this, okMsg, Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                final String errMsg = "保存失败：" + e.getMessage();
                runOnUiThread(() -> Toast.makeText(MainActivity.this, errMsg, Toast.LENGTH_LONG).show());
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setSupportZoom(false);
        s.setAllowFileAccess(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        webView.addJavascriptInterface(new Bridge(), "AIVPAndroid");

        webView.setBackgroundColor(0xFF000000);
        webView.setWebViewClient(new WebViewClient());

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                }
                filePathCallback = callback;
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                try {
                    startActivityForResult(Intent.createChooser(intent, "选择备份文件"), REQUEST_FILE);
                } catch (ActivityNotFoundException e) {
                    filePathCallback = null;
                    return false;
                }
                return true;
            }
        });

        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void download(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
                if (url.startsWith("blob:")) {
                    final String fname = guessFilename(contentDisposition, mimetype);
                    String js = "(function(){var x=new XMLHttpRequest();x.open('GET'," + jsStr(url) + ",true);x.responseType='blob';"
                            + "x.onload=function(){var r=new FileReader();r.onloadend=function(){AIVPAndroid.saveBase64File(r.result," + jsStr(fname) + ");};r.readAsDataURL(x.response);};"
                            + "x.send();})()";
                    webView.evaluateJavascript(js, null);
                    return;
                }
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    try {
                        String fname = guessFilename(contentDisposition, mimetype);
                        DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
                        req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fname);
                        req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                        DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                        if (dm != null) dm.enqueue(req);
                    } catch (Exception e) {
                        try {
                            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        });

        if (savedInstanceState == null) {
            webView.loadUrl(HOME_URL);
        } else {
            webView.restoreState(savedInstanceState);
        }
    }

    private static String jsStr(String v) {
        if (v == null) return "''";
        return JSONObject.quote(v);
    }

    private static String guessFilename(String contentDisposition, String mimetype) {
        String name = "float_backup_" + System.currentTimeMillis();
        if (contentDisposition != null) {
            int idx = contentDisposition.indexOf("filename=");
            if (idx >= 0) {
                String sub = contentDisposition.substring(idx + 9).replace("\"", "").trim();
                int sc = sub.indexOf(';');
                if (sc > 0) sub = sub.substring(0, sc);
                if (sub.length() > 0) name = sub;
            }
        }
        if (!name.contains(".")) {
            if (mimetype != null && mimetype.contains("zip")) name += ".zip";
            else name += ".json";
        }
        return name;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_FILE) {
            if (filePathCallback != null) {
                Uri[] results = null;
                if (resultCode == RESULT_OK && data != null) {
                    if (data.getClipData() != null) {
                        int n = data.getClipData().getItemCount();
                        results = new Uri[n];
                        for (int i = 0; i < n; i++) results[i] = data.getClipData().getItemAt(i).getUri();
                    } else if (data.getData() != null) {
                        results = new Uri[]{data.getData()};
                    }
                }
                filePathCallback.onReceiveValue(results);
                filePathCallback = null;
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            long now = System.currentTimeMillis();
            if (now - lastBackPress < 2000) {
                super.onBackPressed();
            } else {
                lastBackPress = now;
                Toast.makeText(this, "再按一次返回键退出", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
