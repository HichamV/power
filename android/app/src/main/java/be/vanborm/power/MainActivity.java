package be.vanborm.power;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.content.pm.PackageManager;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;
import org.json.JSONArray;

/** Power: één WebView die de app uit de assets laadt. Alle data blijft in de WebView-opslag van de app. */
public class MainActivity extends Activity {
  private WebView web;
  private ValueCallback<Uri[]> fileCb;
  private static final int REQ_FILE = 41;

  @Override protected void onCreate(Bundle state) {
    super.onCreate(state);
    web = new WebView(this);
    WebSettings st = web.getSettings();
    st.setJavaScriptEnabled(true);
    st.setDomStorageEnabled(true);
    st.setAllowFileAccess(true);
    st.setMediaPlaybackRequiresUserGesture(false);
    st.setTextZoom(100);
    web.setWebViewClient(new WebViewClient() {
      @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
        Uri u = r.getUrl();
        if ("file".equals(u.getScheme())) return false;
        try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception e) {}
        return true;
      }
    });
    web.setWebChromeClient(new WebChromeClient() {
      @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams params) {
        if (fileCb != null) fileCb.onReceiveValue(null);
        fileCb = cb;
        try { startActivityForResult(params.createIntent(), REQ_FILE); } catch (Exception e) { fileCb = null; return false; }
        return true;
      }
    });
    web.addJavascriptInterface(new Bridge(), "Android");
    web.setBackgroundColor(Color.parseColor("#F6F7F9"));
    setContentView(web);
    if (state != null) web.restoreState(state); else web.loadUrl("file:///android_asset/www/index.html");
  }

  @Override protected void onActivityResult(int req, int res, Intent data) {
    if (req == REQ_FILE && fileCb != null) { fileCb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data)); fileCb = null; return; }
    super.onActivityResult(req, res, data);
  }

  private void js(String code) { runOnUiThread(() -> web.evaluateJavascript(code, null)); }

  private static HttpURLConnection open(String url, String token, String accept) throws Exception {
    HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
    c.setConnectTimeout(15000); c.setReadTimeout(30000);
    c.setRequestProperty("Accept", accept);
    c.setRequestProperty("User-Agent", "Power-app");
    if (token != null && !token.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + token);
    c.setInstanceFollowRedirects(true);
    return c;
  }

  @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); web.saveState(out); }

  private boolean immersive = false;
  private void applyImmersive() {
    View d = getWindow().getDecorView();
    int f = d.getSystemUiVisibility();
    int hide = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
             | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
    d.setSystemUiVisibility(immersive ? (f | hide) : (f & ~hide));
  }
  @Override public void onWindowFocusChanged(boolean has) { super.onWindowFocusChanged(has); if (has && immersive) applyImmersive(); }

  @Override public void onBackPressed() {
    web.evaluateJavascript("(window.powerBack&&window.powerBack())?'1':'0'", v -> { if (!"\"1\"".equals(v)) MainActivity.super.onBackPressed(); });
  }

  /** Functies die de pagina aanroept als window.Android.* */
  public class Bridge {
    @JavascriptInterface public void toast(String msg) { runOnUiThread(() -> Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show()); }

    @JavascriptInterface public boolean saveFile(String name, String text, String mime) {
      try {
        if (Build.VERSION.SDK_INT >= 29) {
          ContentValues cv = new ContentValues();
          cv.put(MediaStore.Downloads.DISPLAY_NAME, name);
          cv.put(MediaStore.Downloads.MIME_TYPE, mime);
          cv.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Power");
          Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
          if (uri == null) return false;
          try (OutputStream os = getContentResolver().openOutputStream(uri)) { os.write(text.getBytes("UTF-8")); }
        } else {
          File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Power");
          dir.mkdirs();
          try (FileOutputStream os = new FileOutputStream(new File(dir, name))) { os.write(text.getBytes("UTF-8")); }
        }
        toast("Opgeslagen in Downloads/Power/" + name);
        return true;
      } catch (Exception e) { toast("Opslaan mislukt: " + e.getMessage()); return false; }
    }

    @JavascriptInterface public void share(String title, String text) {
      Intent i = new Intent(Intent.ACTION_SEND);
      i.setType("text/plain");
      i.putExtra(Intent.EXTRA_SUBJECT, title);
      i.putExtra(Intent.EXTRA_TEXT, text);
      startActivity(Intent.createChooser(i, title));
    }

    /** Schermvullend: verbergt status- en navigatiebalk (immersive sticky); vegen vanaf de rand toont ze tijdelijk. */
    @JavascriptInterface public void setFullscreen(boolean on) { runOnUiThread(() -> { immersive = on; applyImmersive(); }); }
    @JavascriptInterface public boolean isFullscreen() { return immersive; }

    @JavascriptInterface public void keepAwake(boolean on) {
      runOnUiThread(() -> { if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); });
    }

    @JavascriptInterface public void setBars(String statusHex, String navHex, boolean light) {
      runOnUiThread(() -> {
        try {
          getWindow().setStatusBarColor(Color.parseColor(statusHex));
          getWindow().setNavigationBarColor(Color.parseColor(navHex));
          View d = getWindow().getDecorView();
          int f = d.getSystemUiVisibility();
          f = light ? (f | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR) : (f & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR & ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
          d.setSystemUiVisibility(f);
        } catch (Exception e) {}
      });
    }

    /** Zoekt de nieuwste release op GitHub (owner/repo) en meldt ze aan de pagina via window.onUpdateInfo({version,url,notes}). */
    @JavascriptInterface public void checkUpdate(String repo, String token) {
      new Thread(() -> {
        try {
          HttpURLConnection c = open("https://api.github.com/repos/" + repo + "/releases/latest", token, "application/vnd.github+json");
          if (c.getResponseCode() != 200) { js("window.onUpdateInfo&&window.onUpdateInfo(null)"); return; }
          StringBuilder sb = new StringBuilder(); try (InputStream in = c.getInputStream()) { byte[] b = new byte[4096]; int n; while ((n = in.read(b)) > 0) sb.append(new String(b, 0, n, "UTF-8")); }
          JSONObject o = new JSONObject(sb.toString());
          String tag = o.optString("tag_name", ""); String notes = o.optString("body", "");
          String url = ""; JSONArray as = o.optJSONArray("assets");
          if (as != null) for (int i = 0; i < as.length(); i++) { JSONObject a = as.getJSONObject(i); if (a.optString("name", "").endsWith(".apk")) { url = a.optString("url", ""); break; } }
          JSONObject out = new JSONObject(); out.put("version", tag); out.put("url", url); out.put("notes", notes);
          js("window.onUpdateInfo&&window.onUpdateInfo(" + out.toString() + ")");
        } catch (Exception e) { js("window.onUpdateInfo&&window.onUpdateInfo(null)"); }
      }).start();
    }

    /** Downloadt de APK (API-asset-URL) naar de cache en opent het installatiescherm van Android. */
    @JavascriptInterface public void installUpdate(String assetUrl, String token) {
      new Thread(() -> {
        try {
          HttpURLConnection c = open(assetUrl, token, "application/octet-stream");
          int code = c.getResponseCode();
          if (code == 302 || code == 301 || code == 307) { String loc = c.getHeaderField("Location"); c = open(loc, "", "application/octet-stream"); }
          if (c.getResponseCode() != 200) { toast("Download mislukt (" + c.getResponseCode() + ")"); return; }
          File f = new File(getCacheDir(), "update.apk");
          try (InputStream in = c.getInputStream(); FileOutputStream os = new FileOutputStream(f)) { byte[] b = new byte[16384]; int n; while ((n = in.read(b)) > 0) os.write(b, 0, n); }
          Uri u = Uri.parse("content://be.vanborm.power.apk/update.apk");
          Intent i = new Intent(Intent.ACTION_VIEW);
          i.setDataAndType(u, "application/vnd.android.package-archive");
          i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
          startActivity(i);
        } catch (Exception e) { toast("Update mislukt: " + e.getMessage()); }
      }).start();
    }

    @JavascriptInterface public boolean hasObtainium() {
      try { getPackageManager().getPackageInfo("dev.imranr.obtainium", 0); return true; } catch (PackageManager.NameNotFoundException e) { try { getPackageManager().getPackageInfo("dev.imranr.obtainium.fdroid", 0); return true; } catch (Exception e2) { return false; } }
    }

    @JavascriptInterface public void openObtainium(String repo) {
      try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("obtainium://add/https://github.com/" + repo))); }
      catch (Exception e) { try { startActivity(getPackageManager().getLaunchIntentForPackage("dev.imranr.obtainium")); } catch (Exception e2) { toast("Obtainium niet gevonden"); } }
    }

    @JavascriptInterface public String version() { try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception e) { return "?"; } }
  }
}
