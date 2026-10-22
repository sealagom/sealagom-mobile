package com.sealagom.app;

import android.app.DownloadManager;
import android.content.ContentValues;
import android.os.Build;
import android.provider.MediaStore;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Base64;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.URLUtil;
import android.webkit.WebView;
import android.widget.Toast;

import com.getcapacitor.BridgeActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * The site in a WebView (Capacitor). A WebView ignores file downloads on its own, so exports
 * (PDF, KML, GPX, ECDIS user charts...) go to the phone's Downloads folder here:
 * - normal links through Android's download manager, with the signed-in session cookie;
 * - files the page builds itself (blob: links, e.g. "Download CSV") through a small JS bridge.
 */
public class MainActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WebView webView = getBridge().getWebView();
        webView.addJavascriptInterface(new BlobSaver(), "SeaLagomDownloads");
        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            if (url.startsWith("blob:")) {
                saveBlob(webView, url, contentDisposition, mimeType);
                return;
            }
            try {
                String name = URLUtil.guessFileName(url, contentDisposition, mimeType);
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                String cookies = CookieManager.getInstance().getCookie(url);
                if (cookies != null) request.addRequestHeader("Cookie", cookies);
                request.addRequestHeader("User-Agent", userAgent);
                request.setMimeType(mimeType);
                request.setTitle(name);
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
                DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                dm.enqueue(request);
                Toast.makeText(this, "Downloading " + name, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Download failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    /** Reads a blob: URL inside the page and hands it to BlobSaver as base64. */
    private void saveBlob(WebView webView, String url, String contentDisposition, String mimeType) {
        String name = URLUtil.guessFileName(url, contentDisposition, mimeType);
        String js = "(async function(){try{const r=await fetch('" + url + "');const b=await r.blob();"
                + "const fr=new FileReader();fr.onloadend=function(){SeaLagomDownloads.save(fr.result.split(',')[1],"
                + "'" + name.replace("'", "") + "');};fr.readAsDataURL(b);}catch(e){SeaLagomDownloads.fail(String(e));}})();";
        webView.post(() -> webView.evaluateJavascript(js, null));
    }

    private class BlobSaver {
        @JavascriptInterface
        public void save(String base64, String name) {
            try {
                byte[] data = Base64.decode(base64, Base64.DEFAULT);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Android 10+: apps write to Downloads through MediaStore, no storage permission.
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Downloads.DISPLAY_NAME, name);
                    values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                    Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (uri == null) throw new IllegalStateException("no space in Downloads");
                    try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                        os.write(data);
                    }
                } else {
                    File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    if (!dir.exists()) dir.mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(new File(dir, name))) {
                        fos.write(data);
                    }
                }
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Saved to Downloads: " + name, Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                fail(e.getMessage());
            }
        }

        @JavascriptInterface
        public void fail(String message) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Download failed: " + message, Toast.LENGTH_LONG).show());
        }
    }
}
