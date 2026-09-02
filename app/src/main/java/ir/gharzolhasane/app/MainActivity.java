package ir.gharzolhasane.app;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.view.Window;
import android.view.WindowManager;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.widget.Toast;

import java.io.File;
import java.net.URLConnection;

public class MainActivity extends Activity {

    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);

        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        webView = new WebView(this);

        WebSettings settings = webView.getSettings();

        // JavaScript
        settings.setJavaScriptEnabled(true);

        // Local storage / Supabase
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);

        // File access
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);

        // Media
        settings.setMediaPlaybackRequiresUserGesture(false);

        // Zoom
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        // Cookies
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        // Chrome client
        webView.setWebChromeClient(new WebChromeClient());

        // لینک‌ها داخل خود WebView باز شوند
        webView.setWebViewClient(new WebViewClient() {

            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view,
                    WebResourceRequest request
            ) {
                view.loadUrl(request.getUrl().toString());
                return true;
            }
        });

        /*
         * دانلود فایل‌ها و تصاویر
         */
        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(
                    String url,
                    String userAgent,
                    String contentDisposition,
                    String mimeType,
                    long contentLength
            ) {

                try {
                    String fileName = URLUtil.guessFileName(
                            url,
                            contentDisposition,
                            mimeType
                    );

                    if (fileName == null || fileName.isEmpty()) {
                        fileName = "image_" + System.currentTimeMillis() + ".jpg";
                    }

                    /*
                     * باز کردن لینک در مرورگر/سیستم اندروید
                     * تا کاربر بتواند Save / Share کند.
                     */
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse(url));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                    startActivity(intent);

                } catch (Exception e) {

                    Toast.makeText(
                            MainActivity.this,
                            "امکان باز کردن تصویر وجود ندارد",
                            Toast.LENGTH_LONG
                    ).show();
                }
            }
        });

        /*
         * Long press روی لینک/عکس
         */
        webView.setOnLongClickListener(v -> {

            WebView.HitTestResult result = webView.getHitTestResult();

            if (result == null) {
                return false;
            }

            int type = result.getType();

            // تصویر
            if (type == WebView.HitTestResult.IMAGE_TYPE
                    || type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {

                String imageUrl = result.getExtra();

                if (imageUrl != null && !imageUrl.isEmpty()) {

                    try {

                        Intent intent = new Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(imageUrl)
                        );

                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                        startActivity(intent);

                        return true;

                    } catch (Exception e) {

                        Toast.makeText(
                                MainActivity.this,
                                "امکان باز کردن تصویر وجود ندارد",
                                Toast.LENGTH_SHORT
                        ).show();

                        return true;
                    }
                }
            }

            return false;
        });

        /*
         * سایت صندوق
         */
        webView.loadUrl("https://teramchap.github.io");

        setContentView(webView);
    }

    @Override
    public void onBackPressed() {

        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {

        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }

        super.onDestroy();
    }
}
