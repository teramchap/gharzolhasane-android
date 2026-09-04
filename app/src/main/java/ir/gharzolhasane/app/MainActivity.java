package ir.gharzolhasane.app;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import android.view.Window;
import android.view.WindowManager;

import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

public class MainActivity extends Activity {

    private WebView webView;

    // File chooser
    private ValueCallback<Uri[]> filePathCallback;
    private static final int FILE_CHOOSER_REQUEST_CODE = 1001;

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

        /*
         * Chrome Client
         *
         * مهم:
         * این قسمت برای کار کردن input type="file"
         * در WebView ضروری است.
         */
        webView.setWebChromeClient(new WebChromeClient() {

            @Override
            public boolean onShowFileChooser(
                    WebView webView,
                    ValueCallback<Uri[]> filePathCallback,
                    FileChooserParams fileChooserParams
            ) {

                // اگر قبلاً callback باز مانده، آن را لغو کن
                if (MainActivity.this.filePathCallback != null) {
                    MainActivity.this.filePathCallback.onReceiveValue(null);
                }

                MainActivity.this.filePathCallback = filePathCallback;

                try {

                    Intent intent = fileChooserParams.createIntent();

                    intent.addCategory(Intent.CATEGORY_OPENABLE);

                    /*
                     * اجازه انتخاب تصویر/فایل
                     * بر اساس acceptهای HTML صفحه
                     */
                    startActivityForResult(
                            Intent.createChooser(
                                    intent,
                                    "انتخاب فایل"
                            ),
                            FILE_CHOOSER_REQUEST_CODE
                    );

                    return true;

                } catch (Exception e) {

                    MainActivity.this.filePathCallback = null;

                    Toast.makeText(
                            MainActivity.this,
                            "امکان انتخاب فایل وجود ندارد",
                            Toast.LENGTH_LONG
                    ).show();

                    return false;
                }
            }
        });

        /*
         * لینک‌ها داخل خود WebView باز شوند
         */
        webView.setWebViewClient(new WebViewClient() {

            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view,
                    WebResourceRequest request
            ) {

                view.loadUrl(request.getUrl().toString());
                return true;
            }

            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view,
                    String url
            ) {

                view.loadUrl(url);
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
                        fileName =
                                "image_" +
                                System.currentTimeMillis() +
                                ".jpg";
                    }

                    /*
                     * باز کردن لینک در برنامه مناسب اندروید
                     * برای Save / Share
                     */
                    Intent intent = new Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(url)
                    );

                    intent.addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK
                    );

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
         * Long Press روی عکس
         *
         * با نگه داشتن انگشت روی عکس،
         * تصویر در برنامه مناسب اندروید باز می‌شود
         * و کاربر می‌تواند آن را ذخیره یا Share کند.
         */
        webView.setOnLongClickListener(v -> {

            WebView.HitTestResult result =
                    webView.getHitTestResult();

            if (result == null) {
                return false;
            }

            int type = result.getType();

            if (type == WebView.HitTestResult.IMAGE_TYPE
                    || type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {

                String imageUrl = result.getExtra();

                if (imageUrl != null
                        && !imageUrl.isEmpty()) {

                    try {

                        Intent intent = new Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(imageUrl)
                        );

                        intent.addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK
                        );

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
        webView.loadUrl(
                "https://teramchap.github.io"
        );

        setContentView(webView);
    }

    /*
     * دریافت نتیجه انتخاب عکس/فایل
     */
    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {

        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        if (requestCode != FILE_CHOOSER_REQUEST_CODE) {
            return;
        }

        if (filePathCallback == null) {
            return;
        }

        Uri[] results = null;

        /*
         * کاربر Cancel کرده
         */
        if (resultCode != RESULT_OK) {

            filePathCallback.onReceiveValue(null);
            filePathCallback = null;

            return;
        }

        /*
         * انتخاب یک یا چند فایل
         */
        if (data != null) {

            // چند فایل
            if (data.getClipData() != null) {

                int count = data.getClipData().getItemCount();

                results = new Uri[count];

                for (int i = 0; i < count; i++) {

                    results[i] =
                            data.getClipData()
                                    .getItemAt(i)
                                    .getUri();
                }

            }
            // یک فایل
            else if (data.getData() != null) {

                results = new Uri[]{
                        data.getData()
                };
            }
        }

        filePathCallback.onReceiveValue(results);
        filePathCallback = null;
    }

    /*
     * دکمه Back
     */
    @Override
    public void onBackPressed() {

        if (webView != null
                && webView.canGoBack()) {

            webView.goBack();

        } else {

            super.onBackPressed();
        }
    }

    /*
     * پاکسازی
     */
    @Override
    protected void onDestroy() {

        if (filePathCallback != null) {

            filePathCallback.onReceiveValue(null);
            filePathCallback = null;
        }

        if (webView != null) {

            webView.stopLoading();
            webView.destroy();
            webView = null;
        }

        super.onDestroy();
    }
}
