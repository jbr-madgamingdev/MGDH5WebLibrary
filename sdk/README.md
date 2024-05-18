## Getting started with Android
[![license](http://img.shields.io/badge/license-BSD3-brightgreen.svg?style=flat)](https://github.com/jbr-madgamingdev/MGDH5WebLibrary/blob/master/LICENSE)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](https://github.com/jbr-madgamingdev/MGDH5WebLibrary/pulls)
---

## Dependencies:

Add MGDWeb gradle plugin as a dependency in your module's build.gradle
```gradle
compile 'io.mgdjo.websdk:sdk:3.1.0'
```

## Implement H5Web interface:
1. Implement a class which extends from ```MGDRuntime```

> MGDRuntime is a class which interacts with the overall running information in the system, including Context, UA, ID (which is the unique identification for the saved data) and other information.

```Java
/**
* Here is a sample subclass of MGDRuntime
*/
public class HostH5Runtime extends MGDRuntime {
    public HostH5Runtime(Context context) {
        super(context);
    }
    /**
     * @return User's UA
     */
    @Override
    public String getUserAgent() {
        return "";
    }
    /**
     * @return the ID of user.
     */
    @Override
    public String getCurrentUserAccount() {
        return "";
    }
    /**
     * @return the file path which is used to save Sonic caches.
     */
    @Override
    public File getMGDCacheDir() {
        String path = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator         + "sonic/";
        File file = new File(path.trim());
        if(!file.exists()){
            file.mkdir();
        }
        return file;
    }
}
```
2. Implement a subclass which extends from ```MGDSessionClient```

```Java
/**
 *
 * MGDSessionClient  is a thin API class that delegates its public API to a backend WebView class instance, such as loadUrl and loadDataWithBaseUrl.
 */
public class H5SessionClientImpl extends MGDSessionClient {
    private WebView webView;
    public void bindWebView(WebView webView) {
        this.webView = webView;
    }
    
    @Override
    public void loadUrl(String url, Bundle extraData) {
        webView.loadUrl(url);
    }

    @Override
    public void loadDataWithBaseUrl(String baseUrl, String data, String mimeType, String encoding,                
                                    String historyUrl) {
        webView.loadDataWithBaseURL(baseUrl, data, mimeType, encoding, historyUrl);
    }
}
```
## Android Demo
Here is a simple demo shows how to create an Android activity which uses the MGD H5 Web Framework
```Java

public class BrowserActivity extends Activity {

    public final static String PARAM_URL = "param_url";

    public final static String PARAM_MODE = "param_mode";

    private MGDSession h5Session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        String url = intent.getStringExtra(PARAM_URL);
        int mode = intent.getIntExtra(PARAM_MODE, -1);
        if (TextUtils.isEmpty(url) || -1 == mode) {
            finish();
            return;
        }

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);

        // step 1: Initialize sonic engine if necessary, or maybe u can do this when application created
        if (!MGDEngine.isGetInstanceAllowed()) {
            MGDEngine.createInstance(new H5RuntimeImpl(getApplication()), new MGDConfig.Builder().build());
        }

        H5SessionClientImpl h5SessionClient = null;

        // step 2: Create MGDSession
        h5Session = MGDEngine.getInstance().createSession(url,  new MGDSessionConfig.Builder().build());
        if (null != h5Session) {
            h5Session.bindClient(h5SessionClient = new h5SessionClientImpl());
        } else {
            // this only happen when a same webview session is already running,
            // u can comment following codes to feedback as a default mode.
            throw new UnknownError("create session fail!");
        }

        // step 3: BindWebView for sessionClient and bindClient for MGDSession
        // in the real world, the init flow may cost a long time as startup
        // runtime、init configs....
        setContentView(R.layout.activity_browser);
        WebView webView = (WebView) findViewById(R.id.webview);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (h5Session != null) {
                    h5Session.getSessionClient().pageFinish(url);
                }
            }

            @TargetApi(21)
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return shouldInterceptRequest(view, request.getUrl().toString());
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                if (h5Session != null) {
                //step 6: Call sessionClient.requestResource when host allow the application 
                // to return the local data .
                    return (WebResourceResponse) h5Session.getSessionClient().requestResource(url);
                }
                return null;
            }
        });

        WebSettings webSettings = webView.getSettings();

        // step 4: bind javascript
        // note:if api level lower than 17(android 4.2), addJavascriptInterface has security
        // issue, please use x5 or see https://developer.android.com/reference/android/webkit/
        // WebView.html#addJavascriptInterface(java.lang.Object, java.lang.String)
        webSettings.setJavaScriptEnabled(true);
        webView.removeJavascriptInterface("searchBoxJavaBridge_");
        intent.putExtra(SonicJavaScriptInterface.PARAM_LOAD_URL_TIME, System.currentTimeMillis());
        webView.addJavascriptInterface(new H5JavaScriptInterface(h5SessionClient, intent), "sonic");

        // init webview settings
        webSettings.setAllowContentAccess(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAppCacheEnabled(true);
        webSettings.setSavePassword(false);
        webSettings.setSaveFormData(false);
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);


        // step 5: webview is ready now, just tell session client to bind
        if (h5SessionClient != null) {
            h5SessionClient.bindWebView(webView);
            h5SessionClient.clientReady();
        } else { // default mode
            webView.loadUrl(url);
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (null != h5Session) {
            h5Session.destroy();
            h5Session = null;
        }
        super.onDestroy();
    }
```

## License
MGD H5 Web SDK is under the BSD license. See the [LICENSE](https://github.com/jbr-madgamingdev/MGDH5WebLibrary/blob/master/LICENSE) file for details.
