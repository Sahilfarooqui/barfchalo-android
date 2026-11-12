package com.barfchalo.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.splashscreen.SplashScreen;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import java.net.URISyntaxException;
import java.util.Collections;

public class MainActivity extends AppCompatActivity {
    static final String HOST = "sahilfarooqui.github.io";
    static final String PATH = "/barfchalo";
    static final String HOME = "https://" + HOST + PATH + "/";
    static final String OFFLINE = "file:///android_asset/offline.html";
    private static final int REQ_LOC = 42;

    private WebView web;
    private boolean pageReady = false;
    private boolean clearHistoryNext = false;
    private String lastUrl = HOME;
    private String geoOrigin;
    private GeolocationPermissions.Callback geoCallback;

    // Polyfills so the site's share / copy buttons use native Android features.
    private static final String POLYFILL =
        "(function(){if(!window.BarfChaloApp)return;" +
        "try{navigator.share=function(d){d=d||{};BarfChaloApp.share(d.title||'',[d.text,d.url].filter(Boolean).join('\\n'));return Promise.resolve();};}catch(e){}" +
        "try{var c=navigator.clipboard||{};c.writeText=function(t){BarfChaloApp.copy(String(t));return Promise.resolve();};" +
        "if(!navigator.clipboard)Object.defineProperty(navigator,'clipboard',{value:c});}catch(e){}" +
        // Use GPS + network (high accuracy) and allow a GPS cold start, so "My location" works even when
        // Google network location is switched off on the phone.
        "try{var g=navigator.geolocation,gc=g.getCurrentPosition.bind(g),gw=g.watchPosition.bind(g);" +
        "var fix=function(o){o=Object.assign({},o||{});o.enableHighAccuracy=true;o.timeout=Math.max(o.timeout||0,20000);if(o.maximumAge==null)o.maximumAge=120000;return o;};" +
        "g.getCurrentPosition=function(ok,err,o){return gc(ok,err,fix(o));};g.watchPosition=function(ok,err,o){return gw(ok,err,fix(o));};}catch(e){}" +
        "})();";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen splash = SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        final long start = System.currentTimeMillis();
        splash.setKeepOnScreenCondition(() -> !pageReady && System.currentTimeMillis() - start < 4000);

        if ((getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) WebView.setWebContentsDebuggingEnabled(true);
        web = new WebView(this);
        web.setBackgroundColor(0xFF0B1424);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setGeolocationEnabled(true);
        s.setSupportMultipleWindows(false); // target=_blank goes through shouldOverrideUrlLoading
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setUserAgentString(s.getUserAgentString() + " BarfChaloApp/" + BuildConfigVersion.NAME);

        web.addJavascriptInterface(new Bridge(), "BarfChaloApp");
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(web, POLYFILL, Collections.singleton("https://" + HOST));
        }

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                return handleUrl(req.getUrl());
            }
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                if (url != null && url.startsWith("http")) lastUrl = url;
            }
            @Override
            public void onPageFinished(WebView view, String url) {
                pageReady = true;
                if (clearHistoryNext && url != null && url.startsWith("http")) { view.clearHistory(); clearHistoryNext = false; }
                if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))
                    view.evaluateJavascript(POLYFILL, null);
            }
            @Override
            public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError err) {
                if (req.isForMainFrame()) showOffline();
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback cb) {
                if (hasLocation()) { cb.invoke(origin, true, false); return; }
                geoOrigin = origin; geoCallback = cb;
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOC);
            }
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                String cur = web.getUrl();
                if (cur != null && cur.startsWith(OFFLINE)) { finish(); return; }
                if (web.canGoBack()) web.goBack(); else finish();
            }
        });

        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else loadStart(getIntent());
    }

    private void loadStart(Intent intent) {
        String url = HOME;
        Uri data = intent != null ? intent.getData() : null;
        if (data != null && HOST.equals(data.getHost()) && data.getPath() != null && data.getPath().startsWith(PATH))
            url = data.toString();
        lastUrl = url;
        if (isOnline()) web.loadUrl(url); else showOffline();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent.getData() != null) loadStart(intent);
    }

    private boolean isOurs(Uri u) {
        return "https".equals(u.getScheme()) && HOST.equals(u.getHost())
            && u.getPath() != null && u.getPath().startsWith(PATH);
    }

    /** true = we handled it (opened elsewhere), false = load inside the app. */
    private boolean handleUrl(Uri u) {
        String scheme = u.getScheme() == null ? "" : u.getScheme();
        if (isOurs(u) || scheme.equals("file")) return false;
        try {
            if (scheme.equals("intent")) {
                Intent i = Intent.parseUri(u.toString(), Intent.URI_INTENT_SCHEME);
                i.addCategory(Intent.CATEGORY_BROWSABLE); i.setComponent(null); i.setSelector(null);
                try { startActivity(i); }
                catch (ActivityNotFoundException e) {
                    String fb = i.getStringExtra("browser_fallback_url");
                    if (fb != null) openExternal(Uri.parse(fb));
                }
                return true;
            }
            openExternal(u);
        } catch (URISyntaxException e) { /* ignore bad link */ }
        return true;
    }

    private void openExternal(Uri u) {
        Intent i = new Intent(Intent.ACTION_VIEW, u);
        i.addCategory(Intent.CATEGORY_BROWSABLE);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(i); }
        catch (ActivityNotFoundException e) { toast("No app found to open this link"); }
    }

    private void showOffline() {
        pageReady = true;
        web.loadUrl(OFFLINE);
    }

    private boolean hasLocation() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return true;
        NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
        return nc != null && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] perms, @NonNull int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (code == REQ_LOC && geoCallback != null) {
            boolean ok = hasLocation();
            geoCallback.invoke(geoOrigin, ok, false);
            if (!ok) toast("Location permission denied — type your start city instead");
            geoCallback = null;
        }
    }

    @Override protected void onSaveInstanceState(@NonNull Bundle out) { super.onSaveInstanceState(out); web.saveState(out); }
    @Override protected void onPause() { super.onPause(); web.onPause(); }
    @Override protected void onResume() { super.onResume(); web.onResume(); }

    class Bridge {
        private final Handler ui = new Handler(Looper.getMainLooper());
        @JavascriptInterface public void share(String title, String text) {
            ui.post(() -> {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_SUBJECT, title);
                i.putExtra(Intent.EXTRA_TEXT, text);
                startActivity(Intent.createChooser(i, "Share trip"));
            });
        }
        @JavascriptInterface public void copy(String text) {
            ui.post(() -> {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("BarfChalo", text));
                if (android.os.Build.VERSION.SDK_INT < 33) toast("Link copied");
            });
        }
        @JavascriptInterface public void retry() {
            ui.post(() -> {
                if (isOnline()) { clearHistoryNext = true; web.loadUrl(lastUrl == null || lastUrl.startsWith("file") ? HOME : lastUrl); }
                else toast("Still offline — check Wi-Fi or mobile data");
            });
        }
    }
}
