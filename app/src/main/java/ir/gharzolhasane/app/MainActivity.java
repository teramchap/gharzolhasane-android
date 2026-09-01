package ir.gharzolhasane.app;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.webkit.WebChromeClient;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient.FileChooserParams;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Color;
import android.view.Window;
import android.view.WindowManager;

public class MainActivity extends Activity {

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;

    private static final int FILE_CHOOSER_REQUEST_CODE = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);

        // Fullscreen حذف شده تا نوار ناوبری فضای خودش را داشته باشد
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            getWindow().setNavigationBarColor(Color.BLACK);
        }

        webView = new WebView(this);

        WebSettings settings = webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);

        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        settings.setMediaPlaybackRequiresUserGesture(false);

        webView.setWebViewClient(new WebViewClient());

        // پشتیبانی از انتخاب و آپلود فایل/عکس
        webView.setWebChromeClient(new WebChromeClient() {

            @Override
            public boolean onShowFileChooser(
                    WebView webView,
                    ValueCallback<Uri[]> callback,
                    FileChooserParams fileChooserParams) {

                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                }

                filePathCallback = callback;

                try {
                    Intent intent =
                            fileChooserParams.createIntent();

                    startActivityForResult(
                            intent,
                            FILE_CHOOSER_REQUEST_CODE
                    );

                    return true;

                } catch (Exception e) {

                    filePathCallback = null;
                    return false;
                }
            }
        });

        setContentView(webView);

        webView.loadUrl("https://teramchap.github.io");
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data) {

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

        if (resultCode == RESULT_OK && data != null) {

            if (data.getClipData() != null) {

                int count =
                        data.getClipData().getItemCount();

                results = new Uri[count];

                for (int i = 0; i < count; i++) {

                    results[i] =
                            data.getClipData()
                                    .getItemAt(i)
                                    .getUri();
                }

            } else if (data.getData() != null) {

                results = new Uri[]{
                        data.getData()
                };
            }
        }

        filePathCallback.onReceiveValue(results);

        filePathCallback = null;
    }

    @Override
    public void onBackPressed() {

        if (webView.canGoBack()) {

            webView.goBack();

        } else {

            super.onBackPressed();
        }
    }
}
