package com.catalogo.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.view.Window;
import android.webkit.*;

public class MainActivity extends Activity {

    private static final String URL = "https://script.google.com/macros/s/AKfycbyR3ZojNxG35SrILebuAxjCCSpHS740iXVkOmSbTWmBLzSC5hCl12DvseDqXnhiTsPx/exec";

    private WebView web;
    private WebView webImpresion;   // se guarda para que no lo elimine el recolector de basura mientras se imprime
    private ValueCallback<Uri[]> callback;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Window w = getWindow();
        w.setStatusBarColor(Color.parseColor("#6E1A78")); // morado del encabezado

        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);

        // Puente para imprimir: la página llama a AndroidPrint.imprimirHtml(html)
        web.addJavascriptInterface(new PuenteImpresion(), "AndroidPrint");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                String host = r.getUrl().getHost();
                if (host != null && (host.endsWith("google.com") || host.endsWith("googleusercontent.com"))) {
                    return false; // se queda dentro de la app
                }
                startActivity(new Intent(Intent.ACTION_VIEW, r.getUrl()));
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (callback != null) callback.onReceiveValue(null);
                callback = cb;
                try {
                    startActivityForResult(p.createIntent(), 1);
                } catch (Exception e) {
                    callback = null;
                    return false;
                }
                return true;
            }
        });

        web.loadUrl(URL);
    }

    /** Recibe el HTML armado por la página y abre el diálogo de impresión / guardar como PDF de Android. */
    private class PuenteImpresion {
        @JavascriptInterface
        public void imprimirHtml(final String html) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() { imprimir(html); }
            });
        }
    }

    private void imprimir(String html) {
        webImpresion = new WebView(this);
        webImpresion.getSettings().setJavaScriptEnabled(false);   // solo se dibuja el contenido
        webImpresion.setWebViewClient(new WebViewClient() {
            private boolean lanzado = false;

            @Override
            public void onPageFinished(final WebView view, String url) {
                if (lanzado) return;
                lanzado = true;
                // espera breve para que terminen de cargar las fotos
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        PrintManager pm = (PrintManager) getSystemService(Context.PRINT_SERVICE);
                        String nombre = "Catalogo_de_productos";
                        PrintDocumentAdapter adaptador = view.createPrintDocumentAdapter(nombre);
                        PrintAttributes atributos = new PrintAttributes.Builder()
                                .setMediaSize(PrintAttributes.MediaSize.NA_LETTER)
                                .build();
                        pm.print(nombre, adaptador, atributos);
                    }
                }, 1500);
            }
        });
        webImpresion.loadDataWithBaseURL("https://script.google.com/", html, "text/html", "UTF-8", null);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == 1 && callback != null) {
            callback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data));
            callback = null;
        }
        super.onActivityResult(req, res, data);
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack(); else super.onBackPressed();
    }
}
