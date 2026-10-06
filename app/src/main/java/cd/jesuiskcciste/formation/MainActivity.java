package cd.jesuiskcciste.formation;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

/**
 * JE SUIS KCCiste – FORMATION
 * Affiche l'application HTML (app/src/main/assets/index.html) dans une WebView.
 * - localStorage activé : progression, favoris et accès membre sont conservés.
 * - Les liens externes (WhatsApp) s'ouvrent dans l'application correspondante.
 * - Le bouton Retour d'Android revient à l'écran précédent de la formation.
 * - Le certificat s'enregistre en PDF via la fenêtre d'impression d'Android.
 */
public class MainActivity extends Activity {

    private static final String START_URL = "file:///android_asset/index.html";
    private WebView web;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        web.setBackgroundColor(0xFFF2F4F9);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);      // indispensable pour localStorage
        s.setAllowFileAccess(true);
        s.setTextZoom(100);                // la taille de police système ne casse pas la mise en page

        web.addJavascriptInterface(new Bridge(), "KCCAndroid");
        web.setWebChromeClient(new WebChromeClient());  // affiche les boîtes de confirmation
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return openExternal(request.getUrl());
            }
            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return openExternal(Uri.parse(url));
            }
        });

        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else web.loadUrl(START_URL);
    }

    /** Fonctions appelées depuis index.html (window.KCCAndroid). */
    private class Bridge {
        @JavascriptInterface
        public void printCertificate(final String title) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() { printPage(title); }
            });
        }
    }

    /** Ouvre la fenêtre d'impression : « Enregistrer au format PDF » crée le fichier du certificat. */
    private void printPage(String title) {
        PrintManager pm = (PrintManager) getSystemService(PRINT_SERVICE);
        if (pm == null || web == null) return;
        String job = (title == null || title.isEmpty()) ? "Certificat KCCiste" : title;
        PrintAttributes attrs = new PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4.asLandscape())
                .build();
        pm.print(job, web.createPrintDocumentAdapter(job), attrs);
    }

    /** Pages internes : dans l'app. Liens externes (wa.me…) : application correspondante. */
    private boolean openExternal(Uri uri) {
        if (uri == null || "file".equals(uri.getScheme())) return false;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Exception e) {
            Toast.makeText(this, "Impossible d'ouvrir ce lien. WhatsApp est-il installé ?", Toast.LENGTH_LONG).show();
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (web != null) web.saveState(outState);
    }
}
