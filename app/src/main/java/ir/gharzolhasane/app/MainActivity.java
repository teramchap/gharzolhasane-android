package ir.gharzolhasane.app;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.view.Window;
import android.view.WindowManager;
import android.view.View;
import android.graphics.Color;

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

        // جلوگیری از رفتن محتوای WebView زیر نوار ناوبری گوشی
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            getWindow().setNavigationBarColor(Color.BLACK);

            getWindow().getDecorView().setOnApplyWindowInsetsListener(
                    new View.OnApplyWindowInsetsListener() {
                        @Override
                        public android.view.WindowInsets onApplyWindowInsets(
                                View v,
                                android.view.WindowInsets insets) {

                            if (webView != null) {
                                webView.setPadding(
                                        webView.getPaddingLeft(),
                                        webView.getPaddingTop(),
                                        webView.getPaddingRight(),
                                        insets.getSystemWindowInsetBottom()
                                );
                            }

                            return insets;
                        }
                    }
            );
        }

        webView = new WebView(this);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);

        webView.setWebViewClient(new WebViewClient());

        webView.loadUrl("https://teramchap.github.io");

        setContentView(webView);

        // اعمال Insets بعد از ساخته شدن WebView
        webView.requestApplyInsets();
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
