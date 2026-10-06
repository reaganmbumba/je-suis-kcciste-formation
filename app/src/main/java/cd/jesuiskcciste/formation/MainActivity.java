package cd.jesuiskcciste.formation;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * JE SUIS KCCiste – FORMATION
 * Affiche l'application HTML (app/src/main/assets/index.html) dans une WebView.
 * - localStorage activé : progression, favoris et accès membre sont conservés.
 * - Les liens externes (WhatsApp) s'ouvrent dans l'application correspondante.
 * - Le bouton Retour d'Android revient à l'écran précédent de la formation.
 * - Le certificat s'enregistre en PDF via la fenêtre d'impression d'Android,
 *   ou en image (galerie du téléphone, WhatsApp, Facebook…).
 */
public class MainActivity extends Activity {

    private static final String START_URL = "file:///android_asset/index.html";
    private static final int REQ_STORAGE = 7;
    private WebView web;
    /** Image en attente de l'autorisation de stockage (Android 9 et moins). */
    private byte[] pendingImage;
    private String pendingName, pendingOk, pendingErr;

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

        /** Enregistre l'image (JPEG en base64) dans la galerie, dossier Images/KCCiste. */
        @JavascriptInterface
        public void saveImage(final String base64, final String name, final String okMsg, final String errMsg) {
            final byte[] data = Base64.decode(base64, Base64.DEFAULT);
            runOnUiThread(new Runnable() {
                @Override
                public void run() { saveToGallery(data, name, okMsg, errMsg); }
            });
        }

        /** Partage l'image ; target = "whatsapp", "facebook" ou "" (choix de l'application). */
        @JavascriptInterface
        public void shareImage(final String base64, final String name, final String target,
                               final String title, final String missingMsg) {
            final byte[] data = Base64.decode(base64, Base64.DEFAULT);
            runOnUiThread(new Runnable() {
                @Override
                public void run() { share(data, name, target, title, missingMsg); }
            });
        }
    }

    private static String safeName(String name) {
        String n = name == null ? "" : name.replaceAll("[^A-Za-z0-9._-]", "_");
        return n.isEmpty() ? "Certificat-KCCiste.jpg" : n;
    }

    private void saveToGallery(byte[] data, String name, String okMsg, String errMsg) {
        name = safeName(name);
        if (Build.VERSION.SDK_INT < 29 && Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            pendingImage = data; pendingName = name; pendingOk = okMsg; pendingErr = errMsg;
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                ContentResolver cr = getContentResolver();
                ContentValues v = new ContentValues();
                v.put(MediaStore.Images.Media.DISPLAY_NAME, name);
                v.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                v.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/KCCiste");
                Uri uri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
                if (uri == null) throw new IllegalStateException("insert");
                OutputStream out = cr.openOutputStream(uri);
                if (out == null) throw new IllegalStateException("stream");
                try { out.write(data); } finally { out.close(); }
            } else {
                File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "KCCiste");
                if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("mkdirs");
                File f = new File(dir, name);
                FileOutputStream out = new FileOutputStream(f);
                try { out.write(data); } finally { out.close(); }
                Intent scan = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(f));
                sendBroadcast(scan);
            }
            Toast.makeText(this, okMsg, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, errMsg, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != REQ_STORAGE || pendingImage == null) return;
        byte[] data = pendingImage; pendingImage = null;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            saveToGallery(data, pendingName, pendingOk, pendingErr);
        } else {
            Toast.makeText(this, pendingErr, Toast.LENGTH_LONG).show();
        }
    }

    private void share(byte[] data, String name, String target, String title, String missingMsg) {
        try {
            File dir = new File(getCacheDir(), CertificateProvider.DIR);
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("mkdirs");
            File f = new File(dir, safeName(name));
            FileOutputStream out = new FileOutputStream(f);
            try { out.write(data); } finally { out.close(); }

            Uri uri = CertificateProvider.uriFor(this, f.getName());
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("image/jpeg");
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.putExtra(Intent.EXTRA_TEXT, title);
            send.setClipData(ClipData.newRawUri(title, uri));
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            String pkg = "whatsapp".equals(target) ? "com.whatsapp"
                    : "facebook".equals(target) ? "com.facebook.katana" : null;
            if (pkg != null) {
                send.setPackage(pkg);
                try { startActivity(send); return; }
                catch (Exception notInstalled) {
                    if ("com.whatsapp".equals(pkg)) {   // WhatsApp Business
                        send.setPackage("com.whatsapp.w4b");
                        try { startActivity(send); return; } catch (Exception ignored) { }
                    }
                    Toast.makeText(this, missingMsg, Toast.LENGTH_LONG).show();
                    send.setPackage(null);
                }
            }
            startActivity(Intent.createChooser(send, title));
        } catch (Exception e) {
            Toast.makeText(this, missingMsg, Toast.LENGTH_LONG).show();
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
