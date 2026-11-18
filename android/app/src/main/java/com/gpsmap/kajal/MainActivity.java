package com.gpsmap.kajal;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.app.DownloadManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import androidx.core.app.NotificationCompat;
import android.content.ContentValues;
import android.media.MediaScannerConnection;
import android.provider.MediaStore;
import android.util.Base64;
import android.widget.Toast;
import java.io.OutputStream;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import android.widget.FrameLayout;
import android.view.Gravity;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd;
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.OnBackPressedCallback;
import android.util.DisplayMetrics;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy;
import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

public class MainActivity extends BridgeActivity {

    @CapacitorPlugin(name = "AppUpdate")
    public static class AppUpdatePlugin extends Plugin {
        @PluginMethod
        public void installApk(PluginCall call) {
            String url = call.getString("url");
            String fileName = call.getString("fileName");
            MainActivity activity = (MainActivity) getActivity();
            if (activity != null) {
                activity.startDirectApkDownload(url, fileName);
            }
            call.resolve();
        }
    }

    private AndroidNativeInterface nativeInterface;
    private static final int PERMISSION_REQ_CODE = 1001;
    private static final int FILE_CHOOSER_REQ_CODE = 2001;
    private static final int NATIVE_PDF_PICKER_REQ = 2002;
    private static final int NATIVE_STORE35_PICKER_REQ = 2003;
    private static final int NATIVE_CAMERA_REQ = 2004;
    private static final int MIC_PERMISSION_REQ_CODE = 2005;
    private static final int EXO_PLAYER_REQ_CODE = 2006;
    private ValueCallback<Uri[]> filePathCallback = null;
    private Uri cameraCaptureUri = null;
    private String cameraCaptureFacing = null;
    private boolean isCameraLaunchPending = false;
    private PermissionRequest pendingWebAudioPermissionRequest = null;
    private boolean isVoiceSearchPending = false;
    private String pendingPdfBase64 = null;
    private String pendingPdfName = null;

    // AdMob Real Production Ads Fields
    private AdView adMobBannerView = null;
    private FrameLayout adMobBannerContainer = null;
    private InterstitialAd adMobInterstitialAd = null;
    private RewardedAd adMobRewardedAd = null;
    private RewardedInterstitialAd adMobRewardedInterstitialAd = null;
    private boolean isAdMobInitialized = false;
    private String lastAdMobBannerUnit = null;
    private String lastAdMobInterstitialUnit = null;
    private String lastAdMobRewardedUnit = null;
    private String lastAdMobRewardedInterstitialUnit = null;

    // Inline Native Media3 ExoPlayer (plays in Portrait 16:9 box & expands to Landscape on Fullscreen click)
    private FrameLayout inlineExoContainer = null;
    private PlayerView inlineExoPlayerView = null;
    private ExoPlayer inlineExoPlayer = null;
    private LinearLayout inlineExoTopBar = null;
    private ImageButton inlineExoExitFsBtn = null;
    private TextView inlineExoTitleText = null;
    private TextView inlineExoPreviewBadge = null;
    private TextView inlineExoSpeedBtn = null;
    private TextView inlineExoResizeBtn = null;
    private ImageButton inlineExoRotateBtn = null;
    private LinearLayout inlineExoLoadingOverlay = null;
    private TextView inlineExoLoadingText = null;

    private boolean isInlineExoActive = false;
    private boolean isInlineExoFullscreen = false;
    private boolean isInlineExoControllerVisible = true;
    private boolean isInlineExoHiddenForOverlay = false;
    private boolean isInlineExoLandscapeLocked = true;
    private boolean wasAdMobBannerVisibleBeforeExoFs = false;
    private boolean inlineExoScrollListenerAttached = false;

    private String inlineExoCurrentUrl = "";
    private String inlineExoCurrentTitle = "Movie Player";
    private boolean inlineExoIsSubscribed = true;
    private int inlineExoPreviewLimitSec = 20;
    private long inlineExoLastValidPosMs = 0L;
    private long inlineExoPendingSeekPosMs = -1L;
    private int inlineExoErrorRetryCount = 0;
    private long inlineExoAccumulatedPreviewMs = 0L;
    private long inlineExoLastPreviewTickMs = 0L;
    private boolean inlineExoPreviewTriggeredExit = false;

    private int inlineExoLeftPx = 0;
    private int inlineExoDocTopPx = 0;
    private int inlineExoWidthPx = 0;
    private int inlineExoHeightPx = 0;
    private int inlineExoHeaderBottomPx = 0;

    private int inlineExoPortraitLeftPx = 0;
    private int inlineExoPortraitDocTopPx = 0;
    private int inlineExoPortraitWidthPx = 0;
    private int inlineExoPortraitHeightPx = 0;
    private int inlineExoPortraitHeaderBottomPx = 0;

    private int inlineExoResizeIndex = 0;
    private final int[] inlineExoResizeModes = new int[]{
            AspectRatioFrameLayout.RESIZE_MODE_FIT,
            AspectRatioFrameLayout.RESIZE_MODE_FILL,
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    };
    private final String[] inlineExoResizeLabels = new String[]{"FIT", "FILL", "ZOOM"};

    private int inlineExoSpeedIndex = 1;
    private final float[] inlineExoSpeedValues = new float[]{0.75f, 1.0f, 1.25f, 1.5f, 2.0f};
    private final String[] inlineExoSpeedLabels = new String[]{"0.75x", "1x", "1.25x", "1.5x", "2x"};

    private final Handler inlineExoHandler = new Handler(Looper.getMainLooper());
    private OnBackPressedCallback inlineExoBackCallback = null;

    public static final String CUSTOM_USER_AGENT = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(AppUpdatePlugin.class);
        super.onCreate(savedInstanceState);

        // Keep mobile screen always on while VDOSky app is open
        try {
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Apply true edge-to-edge fullscreen immersive mode hiding status bar & system navigation
        hideSystemBars();

        nativeInterface = new AndroidNativeInterface();
        setupJavascriptBridge();
        applyCustomUserAgent();

        // Hardware back exits inline ExoPlayer fullscreen back to portrait mode cleanly
        inlineExoBackCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                if (isInlineExoFullscreen) {
                    setInlineExoFullscreen(false);
                } else {
                    setEnabled(false);
                }
            }
        };
        getOnBackPressedDispatcher().addCallback(this, inlineExoBackCallback);

        // Immediately initialize Google Mobile Ads SDK (Ad units will load live from Firebase)
        initGoogleMobileAds();

        // Do not auto-request camera/location permissions on startup - let user open movie app smoothly
        handleIncomingPdfIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingPdfIntent(intent);
    }

    private void handleIncomingPdfIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        Uri dataUri = intent.getData();
        if (Intent.ACTION_VIEW.equals(action) && dataUri != null) {
            new Thread(() -> {
                try {
                    String fileName = "document.pdf";
                    android.database.Cursor cursor = getContentResolver().query(dataUri, null, null, null, null);
                    if (cursor != null) {
                        int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                        if (nameIndex != -1 && cursor.moveToFirst()) {
                            fileName = cursor.getString(nameIndex);
                        }
                        cursor.close();
                    } else if (dataUri.getLastPathSegment() != null) {
                        fileName = dataUri.getLastPathSegment();
                    }

                    InputStream inputStream = getContentResolver().openInputStream(dataUri);
                    if (inputStream != null) {
                        java.io.ByteArrayOutputStream byteBuffer = new java.io.ByteArrayOutputStream();
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = inputStream.read(buffer)) != -1) {
                            byteBuffer.write(buffer, 0, len);
                        }
                        inputStream.close();
                        byte[] pdfBytes = byteBuffer.toByteArray();
                        final String base64 = android.util.Base64.encodeToString(pdfBytes, android.util.Base64.NO_WRAP);
                        final String finalFileName = fileName;

                        pendingPdfBase64 = base64;
                        pendingPdfName = finalFileName;

                        runOnUiThread(() -> {
                            try {
                                if (getBridge() != null && getBridge().getWebView() != null) {
                                    getBridge().getWebView().evaluateJavascript(
                                        "window.handleIncomingPdfFromAndroid && window.handleIncomingPdfFromAndroid('" + finalFileName.replace("'", "\\'") + "')",
                                        null
                                    );
                                }
                            } catch (Exception ex) {
                                ex.printStackTrace();
                            }
                        });
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
        }
    }

    public void checkAndRequestAppPermissions() {
        try {
            java.util.List<String> neededPermissions = new java.util.ArrayList<>();
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                neededPermissions.add(android.Manifest.permission.CAMERA);
            }
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                neededPermissions.add(android.Manifest.permission.ACCESS_FINE_LOCATION);
            }
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                neededPermissions.add(android.Manifest.permission.ACCESS_COARSE_LOCATION);
            }
            if (!neededPermissions.isEmpty()) {
                ActivityCompat.requestPermissions(this, neededPermissions.toArray(new String[0]), PERMISSION_REQ_CODE);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void notifyRewardCompleted() {
        runOnUiThread(() -> {
            try {
                if (getBridge() != null && getBridge().getWebView() != null) {
                    getBridge().getWebView().evaluateJavascript(
                        "window.onAndroidRewardedVideoCompleted && window.onAndroidRewardedVideoCompleted()", 
                        null
                    );
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        setupJavascriptBridge();
        applyCustomUserAgent();
        hideSystemBars();
    }

    @Override
    public void onResume() {
        super.onResume();
        try {
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } catch (Exception e) {
            e.printStackTrace();
        }
        setupJavascriptBridge();
        applyCustomUserAgent();
        hideSystemBars();
        if (isInlineExoActive && !isInlineExoFullscreen) {
            syncWebViewExoBoundsAsync();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (inlineExoPlayer != null && inlineExoPlayer.isPlaying()) {
            try {
                inlineExoPlayer.pause();
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void onDestroy() {
        releaseInlineExoPlayer();
        if (adMobBannerView != null) {
            try {
                adMobBannerView.destroy();
                adMobBannerView = null;
            } catch (Exception ignored) {}
        }
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (isInlineExoActive) {
            if (isInlineExoFullscreen) {
                applyInlineExoFullscreenLayout();
            } else {
                applyInlineExoPortraitPosition();
                inlineExoHandler.postDelayed(this::syncWebViewExoBoundsAsync, 60);
                inlineExoHandler.postDelayed(this::syncWebViewExoBoundsAsync, 200);
                inlineExoHandler.postDelayed(this::syncWebViewExoBoundsAsync, 420);
            }
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemBars();
        }
    }

    public void hideSystemBars() {
        runOnUiThread(() -> {
            try {
                Window window = getWindow();
                if (window == null) return;

                // Configure cutout display mode to render edge-to-edge behind camera notches on Android 9+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    WindowManager.LayoutParams lp = window.getAttributes();
                    lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                    window.setAttributes(lp);
                }

                // Fullscreen window without FLAG_LAYOUT_NO_LIMITS (so bottom views are never pushed off-screen)
                window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                window.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);

                // WindowCompat setup for modern Android (Android 11 / API 30+)
                WindowCompat.setDecorFitsSystemWindows(window, false);
                WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, window.getDecorView());
                if (controller != null) {
                    controller.hide(WindowInsetsCompat.Type.systemBars());
                    controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                }

                // Sticky immersive flags
                View decorView = window.getDecorView();
                if (decorView != null) {
                    decorView.setFitsSystemWindows(false);
                    decorView.setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    );
                    
                    // Re-apply whenever visibility changes
                    decorView.setOnSystemUiVisibilityChangeListener(visibility -> {
                        if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                            decorView.setSystemUiVisibility(
                                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                | View.SYSTEM_UI_FLAG_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            );
                        }
                    });

                    // Zero out all system insets so no padding or gap is added on top/bottom
                    ViewCompat.setOnApplyWindowInsetsListener(decorView, (v, insets) -> {
                        return WindowInsetsCompat.CONSUMED;
                    });
                }

                // Ensure the underlying Capacitor WebView fitsSystemWindows is set to false and zero insets
                if (getBridge() != null && getBridge().getWebView() != null) {
                    View webView = getBridge().getWebView();
                    webView.setFitsSystemWindows(false);
                    ViewCompat.setOnApplyWindowInsetsListener(webView, (v, insets) -> {
                        return WindowInsetsCompat.CONSUMED;
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void applyCustomUserAgent() {
        runOnUiThread(() -> {
            try {
                if (getBridge() != null && getBridge().getWebView() != null) {
                    getBridge().getWebView().getSettings().setUserAgentString(CUSTOM_USER_AGENT);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void setupJavascriptBridge() {
        runOnUiThread(() -> {
            try {
                if (getBridge() != null && getBridge().getWebView() != null) {
                    android.webkit.WebView webView = getBridge().getWebView();
                    if (nativeInterface == null) {
                        nativeInterface = new AndroidNativeInterface();
                    }
                    webView.addJavascriptInterface(nativeInterface, "Android");

                    // WebSettings: Enable Geolocation, DOM storage and multi-touch zoom
                    WebSettings settings = webView.getSettings();
                    settings.setGeolocationEnabled(true);
                    settings.setDomStorageEnabled(true);
                    settings.setDatabaseEnabled(true);
                    settings.setSupportZoom(true);
                    settings.setBuiltInZoomControls(true);
                    settings.setDisplayZoomControls(false);
                    settings.setMediaPlaybackRequiresUserGesture(false);

                    // WebChromeClient: grant camera & geolocation permissions for HTML5/WebRTC and handle HTML file inputs
                    webView.setWebChromeClient(new WebChromeClient() {
                        @Override
                        public void onPermissionRequest(final PermissionRequest request) {
                            runOnUiThread(() -> {
                                try {
                                    boolean requestsAudio = false;
                                    for (String res : request.getResources()) {
                                        if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(res)) {
                                            requestsAudio = true;
                                            break;
                                        }
                                    }
                                    if (requestsAudio && ContextCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                                        pendingWebAudioPermissionRequest = request;
                                        ActivityCompat.requestPermissions(MainActivity.this, new String[]{ android.Manifest.permission.RECORD_AUDIO }, MIC_PERMISSION_REQ_CODE);
                                        return;
                                    }
                                    request.grant(request.getResources());
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            });
                        }

                        @Override
                        public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                            callback.invoke(origin, true, false);
                        }

                        @Override
                        public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback, WebChromeClient.FileChooserParams fileChooserParams) {
                            if (filePathCallback != null) {
                                filePathCallback.onReceiveValue(null);
                                filePathCallback = null;
                            }
                            filePathCallback = callback;

                            boolean isCapture = (cameraCaptureFacing != null && !cameraCaptureFacing.trim().isEmpty())
                                    || (fileChooserParams != null && fileChooserParams.isCaptureEnabled());

                            if (isCapture) {
                                try {
                                    if (ContextCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                                        ActivityCompat.requestPermissions(MainActivity.this, new String[]{ android.Manifest.permission.CAMERA }, PERMISSION_REQ_CODE);
                                    }
                                    Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                                    File storageDir = getExternalCacheDir() != null ? getExternalCacheDir() : getCacheDir();
                                    File photoFile = File.createTempFile("CAPTURE_" + System.currentTimeMillis() + "_", ".jpg", storageDir);
                                    cameraCaptureUri = FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".fileprovider", photoFile);
                                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraCaptureUri);
                                    takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

                                    if ("user".equalsIgnoreCase(cameraCaptureFacing) || "front".equalsIgnoreCase(cameraCaptureFacing)) {
                                        takePictureIntent.putExtra("android.intent.extras.CAMERA_FACING", 1);
                                        takePictureIntent.putExtra("android.intent.extras.LENS_FACING_FRONT", 1);
                                        takePictureIntent.putExtra("android.intent.extra.USE_FRONT_CAMERA", true);
                                    } else {
                                        takePictureIntent.putExtra("android.intent.extras.CAMERA_FACING", 0);
                                        takePictureIntent.putExtra("android.intent.extras.LENS_FACING_FRONT", 0);
                                        takePictureIntent.putExtra("android.intent.extra.USE_FRONT_CAMERA", false);
                                    }

                                    startActivityForResult(takePictureIntent, FILE_CHOOSER_REQ_CODE);
                                    return true;
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }

                            Intent intent = null;
                            if (fileChooserParams != null) {
                                try {
                                    intent = fileChooserParams.createIntent();
                                } catch (Exception e) {
                                    intent = null;
                                }
                            }
                            if (intent == null) {
                                intent = new Intent(Intent.ACTION_GET_CONTENT);
                                intent.addCategory(Intent.CATEGORY_OPENABLE);
                                intent.setType("*/*");
                            }
                            try {
                                startActivityForResult(Intent.createChooser(intent, "Choose File"), FILE_CHOOSER_REQ_CODE);
                                return true;
                            } catch (Exception e) {
                                if (filePathCallback != null) {
                                    filePathCallback.onReceiveValue(null);
                                    filePathCallback = null;
                                }
                                return false;
                            }
                        }
                    });

                    // Set DownloadListener for direct native downloads in WebView
                    webView.setDownloadListener(new DownloadListener() {
                        @Override
                        public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
                            if (url == null) return;
                            if (url.startsWith("data:")) {
                                String guessedName = URLUtil.guessFileName(url, contentDisposition, mimeType);
                                if (nativeInterface != null) {
                                    nativeInterface.saveBase64File(url, guessedName, mimeType);
                                }
                            } else if (url.startsWith("blob:")) {
                                runOnUiThread(() -> {
                                    try {
                                        if (getBridge() != null && getBridge().getWebView() != null) {
                                            getBridge().getWebView().evaluateJavascript(
                                                "window.downloadBlobUrlFromNative && window.downloadBlobUrlFromNative('" + url.replace("'", "\\'") + "', '" + (mimeType != null ? mimeType : "") + "')",
                                                null
                                            );
                                        }
                                    } catch (Exception ignore) {}
                                });
                            } else {
                                try {
                                    DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                                    if (mimeType != null && !mimeType.isEmpty()) request.setMimeType(mimeType);
                                    String fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);
                                    request.setTitle(fileName);
                                    request.setDescription("Downloading file...");
                                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
                                    DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                                    if (dm != null) dm.enqueue(request);
                                    Toast.makeText(MainActivity.this, "Downloading " + fileName + "...", Toast.LENGTH_SHORT).show();
                                } catch (Exception e) {
                                    openExternalUrl(url);
                                }
                            }
                        }
                    });

                    applyCustomUserAgent();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void initGoogleMobileAds() {
        if (isAdMobInitialized) return;
        runOnUiThread(() -> {
            try {
                MobileAds.initialize(this, initializationStatus -> {
                    isAdMobInitialized = true;
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void showAdMobBanner(final String adUnitId) {
        if (adUnitId == null || adUnitId.trim().isEmpty()) {
            hideAdMobBanner();
            return;
        }
        final String targetUnitId = adUnitId.trim();
        runOnUiThread(() -> {
            try {
                initGoogleMobileAds();
                if (adMobBannerContainer == null) {
                    adMobBannerContainer = findViewById(R.id.admob_banner_container);
                    if (adMobBannerContainer == null) {
                        ViewGroup rootView = (ViewGroup) getWindow().getDecorView().findViewById(android.R.id.content);
                        if (rootView == null) {
                            rootView = (ViewGroup) getWindow().getDecorView();
                        }
                        adMobBannerContainer = new FrameLayout(this);
                        FrameLayout.LayoutParams containerParams = new FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT
                        );
                        containerParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
                        adMobBannerContainer.setLayoutParams(containerParams);
                        adMobBannerContainer.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                        ViewCompat.setElevation(adMobBannerContainer, 9999f);
                        rootView.addView(adMobBannerContainer);
                        rootView.bringChildToFront(adMobBannerContainer);
                    }
                }

                adMobBannerContainer.bringToFront();
                adMobBannerContainer.setVisibility(View.VISIBLE);

                // If banner is already loaded/loading for the exact same unit ID, do not destroy and recreate it
                if (adMobBannerView != null && targetUnitId.equals(lastAdMobBannerUnit)) {
                    return;
                }

                if (adMobBannerView != null) {
                    adMobBannerContainer.removeView(adMobBannerView);
                    adMobBannerView.destroy();
                    adMobBannerView = null;
                }

                lastAdMobBannerUnit = targetUnitId;
                adMobBannerView = new AdView(this);
                adMobBannerView.setAdSize(AdSize.BANNER);
                adMobBannerView.setAdUnitId(targetUnitId);

                adMobBannerView.setAdListener(new com.google.android.gms.ads.AdListener() {
                    @Override
                    public void onAdLoaded() {
                        android.util.Log.d("AdMob", "Banner Ad loaded successfully for unit: " + targetUnitId);
                        if (adMobBannerContainer != null) {
                            adMobBannerContainer.setVisibility(View.VISIBLE);
                            adMobBannerContainer.bringToFront();
                            try {
                                ViewGroup p = (ViewGroup) adMobBannerContainer.getParent();
                                if (p != null) {
                                    p.bringChildToFront(adMobBannerContainer);
                                }
                            } catch (Exception ignore) {}
                        }
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        android.util.Log.e("AdMob", "Banner Ad failed to load: " + loadAdError.getMessage() + " (code: " + loadAdError.getCode() + ")");
                    }
                });

                FrameLayout.LayoutParams adParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                );
                adParams.gravity = Gravity.CENTER_HORIZONTAL;
                adMobBannerView.setLayoutParams(adParams);
                adMobBannerContainer.addView(adMobBannerView);

                // Production or Test ad request
                AdRequest adRequest = new AdRequest.Builder().build();
                adMobBannerView.loadAd(adRequest);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void hideAdMobBanner() {
        runOnUiThread(() -> {
            try {
                lastAdMobBannerUnit = null;
                if (adMobBannerContainer != null) {
                    adMobBannerContainer.setVisibility(View.GONE);
                }
                if (adMobBannerView != null) {
                    adMobBannerView.destroy();
                    adMobBannerView = null;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void loadAndShowAdMobInterstitial(final String adUnitId) {
        if (adUnitId == null || adUnitId.trim().isEmpty()) return;
        final String targetUnitId = adUnitId.trim();
        runOnUiThread(() -> {
            try {
                initGoogleMobileAds();
                lastAdMobInterstitialUnit = targetUnitId;
                if (adMobInterstitialAd != null) {
                    adMobInterstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                        @Override
                        public void onAdDismissedFullScreenContent() {
                            adMobInterstitialAd = null;
                            preloadAdMobInterstitial(targetUnitId);
                        }
                        @Override
                        public void onAdFailedToShowFullScreenContent(@NonNull com.google.android.gms.ads.AdError adError) {
                            adMobInterstitialAd = null;
                            android.util.Log.e("AdMob", "Interstitial failed to show: " + adError.getMessage());
                        }
                    });
                    adMobInterstitialAd.show(MainActivity.this);
                    return;
                }

                AdRequest adRequest = new AdRequest.Builder().build();
                InterstitialAd.load(MainActivity.this, targetUnitId, adRequest, new InterstitialAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull InterstitialAd interstitialAd) {
                        android.util.Log.d("AdMob", "Interstitial loaded successfully for: " + targetUnitId);
                        adMobInterstitialAd = interstitialAd;
                        adMobInterstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                            @Override
                            public void onAdDismissedFullScreenContent() {
                                adMobInterstitialAd = null;
                                preloadAdMobInterstitial(targetUnitId);
                            }
                            @Override
                            public void onAdFailedToShowFullScreenContent(@NonNull com.google.android.gms.ads.AdError adError) {
                                adMobInterstitialAd = null;
                            }
                        });
                        adMobInterstitialAd.show(MainActivity.this);
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        android.util.Log.e("AdMob", "Interstitial failed to load: " + loadAdError.getMessage() + " (code: " + loadAdError.getCode() + ")");
                        adMobInterstitialAd = null;
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void preloadAdMobInterstitial(final String adUnitId) {
        if (adUnitId == null || adUnitId.trim().isEmpty()) return;
        final String targetUnitId = adUnitId.trim();
        runOnUiThread(() -> {
            try {
                initGoogleMobileAds();
                lastAdMobInterstitialUnit = targetUnitId;
                AdRequest adRequest = new AdRequest.Builder().build();
                InterstitialAd.load(MainActivity.this, targetUnitId, adRequest, new InterstitialAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull InterstitialAd interstitialAd) {
                        android.util.Log.d("AdMob", "Interstitial preloaded successfully: " + targetUnitId);
                        adMobInterstitialAd = interstitialAd;
                    }
                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        android.util.Log.e("AdMob", "Interstitial preload failed: " + loadAdError.getMessage() + " (code: " + loadAdError.getCode() + ")");
                        adMobInterstitialAd = null;
                    }
                });
            } catch (Exception ignore) {}
        });
    }

    public void loadAndShowAdMobRewardedInterstitial(final String adUnitId) {
        if (adUnitId == null || adUnitId.trim().isEmpty()) {
            notifyRewardCompleted();
            return;
        }
        final String targetUnitId = adUnitId.trim();
        runOnUiThread(() -> {
            try {
                initGoogleMobileAds();
                lastAdMobRewardedInterstitialUnit = targetUnitId;
                if (adMobRewardedInterstitialAd != null) {
                    adMobRewardedInterstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                        @Override
                        public void onAdDismissedFullScreenContent() {
                            adMobRewardedInterstitialAd = null;
                            notifyRewardCompleted();
                            preloadAdMobRewardedInterstitial(targetUnitId);
                        }
                        @Override
                        public void onAdFailedToShowFullScreenContent(@NonNull com.google.android.gms.ads.AdError adError) {
                            adMobRewardedInterstitialAd = null;
                            android.util.Log.e("AdMob", "Rewarded Interstitial failed to show: " + adError.getMessage());
                            notifyRewardCompleted();
                        }
                    });
                    adMobRewardedInterstitialAd.show(MainActivity.this, rewardItem -> {
                        notifyRewardCompleted();
                    });
                    return;
                }

                AdRequest adRequest = new AdRequest.Builder().build();
                RewardedInterstitialAd.load(MainActivity.this, targetUnitId, adRequest, new RewardedInterstitialAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull RewardedInterstitialAd rewardedInterstitialAd) {
                        android.util.Log.d("AdMob", "Rewarded Interstitial loaded successfully: " + targetUnitId);
                        adMobRewardedInterstitialAd = rewardedInterstitialAd;
                        adMobRewardedInterstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                            @Override
                            public void onAdDismissedFullScreenContent() {
                                adMobRewardedInterstitialAd = null;
                                notifyRewardCompleted();
                                preloadAdMobRewardedInterstitial(targetUnitId);
                            }
                            @Override
                            public void onAdFailedToShowFullScreenContent(@NonNull com.google.android.gms.ads.AdError adError) {
                                adMobRewardedInterstitialAd = null;
                                notifyRewardCompleted();
                            }
                        });
                        adMobRewardedInterstitialAd.show(MainActivity.this, rewardItem -> {
                            notifyRewardCompleted();
                        });
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        android.util.Log.e("AdMob", "Rewarded Interstitial failed to load: " + loadAdError.getMessage() + " (code: " + loadAdError.getCode() + "). Falling back to Interstitial.");
                        adMobRewardedInterstitialAd = null;
                        // Fallback to standard Interstitial if format is standard interstitial
                        loadAndShowAdMobInterstitial(targetUnitId);
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                loadAndShowAdMobInterstitial(targetUnitId);
            }
        });
    }

    public void preloadAdMobRewardedInterstitial(final String adUnitId) {
        if (adUnitId == null || adUnitId.trim().isEmpty()) return;
        final String targetUnitId = adUnitId.trim();
        runOnUiThread(() -> {
            try {
                initGoogleMobileAds();
                lastAdMobRewardedInterstitialUnit = targetUnitId;
                AdRequest adRequest = new AdRequest.Builder().build();
                RewardedInterstitialAd.load(MainActivity.this, targetUnitId, adRequest, new RewardedInterstitialAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull RewardedInterstitialAd rewardedInterstitialAd) {
                        android.util.Log.d("AdMob", "Rewarded Interstitial preloaded: " + targetUnitId);
                        adMobRewardedInterstitialAd = rewardedInterstitialAd;
                    }
                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        android.util.Log.e("AdMob", "Rewarded Interstitial preload failed: " + loadAdError.getMessage() + " (code: " + loadAdError.getCode() + ")");
                        adMobRewardedInterstitialAd = null;
                        preloadAdMobInterstitial(targetUnitId);
                    }
                });
            } catch (Exception ignore) {}
        });
    }

    public void showAdMobInterstitialNative(final String adUnitId) {
        if (adUnitId == null || adUnitId.trim().isEmpty()) return;
        final String targetUnitId = adUnitId.trim();
        runOnUiThread(() -> {
            if (adMobRewardedInterstitialAd != null) {
                adMobRewardedInterstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                    @Override
                    public void onAdDismissedFullScreenContent() {
                        adMobRewardedInterstitialAd = null;
                        notifyRewardCompleted();
                        preloadAdMobRewardedInterstitial(targetUnitId);
                    }
                    @Override
                    public void onAdFailedToShowFullScreenContent(@NonNull com.google.android.gms.ads.AdError adError) {
                        adMobRewardedInterstitialAd = null;
                        notifyRewardCompleted();
                    }
                });
                adMobRewardedInterstitialAd.show(MainActivity.this, rewardItem -> notifyRewardCompleted());
                return;
            }
            if (adMobInterstitialAd != null) {
                adMobInterstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                    @Override
                    public void onAdDismissedFullScreenContent() {
                        adMobInterstitialAd = null;
                        preloadAdMobInterstitial(targetUnitId);
                    }
                    @Override
                    public void onAdFailedToShowFullScreenContent(@NonNull com.google.android.gms.ads.AdError adError) {
                        adMobInterstitialAd = null;
                    }
                });
                adMobInterstitialAd.show(MainActivity.this);
                return;
            }
            loadAndShowAdMobRewardedInterstitial(targetUnitId);
        });
    }

    public void loadAndShowAdMobRewarded(final String adUnitId) {
        if (adUnitId == null || adUnitId.trim().isEmpty()) {
            notifyRewardCompleted();
            return;
        }
        final String targetUnitId = adUnitId.trim();
        runOnUiThread(() -> {
            try {
                initGoogleMobileAds();
                lastAdMobRewardedUnit = targetUnitId;
                if (adMobRewardedAd != null) {
                    adMobRewardedAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                        @Override
                        public void onAdDismissedFullScreenContent() {
                            adMobRewardedAd = null;
                            notifyRewardCompleted();
                            preloadAdMobRewarded(targetUnitId);
                        }
                        @Override
                        public void onAdFailedToShowFullScreenContent(@NonNull com.google.android.gms.ads.AdError adError) {
                            adMobRewardedAd = null;
                            android.util.Log.e("AdMob", "Rewarded ad failed to show: " + adError.getMessage());
                            notifyRewardCompleted();
                        }
                    });
                    adMobRewardedAd.show(MainActivity.this, rewardItem -> {
                        notifyRewardCompleted();
                    });
                    return;
                }

                AdRequest adRequest = new AdRequest.Builder().build();
                RewardedAd.load(MainActivity.this, targetUnitId, adRequest, new RewardedAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull RewardedAd rewardedAd) {
                        android.util.Log.d("AdMob", "Rewarded ad loaded successfully: " + targetUnitId);
                        adMobRewardedAd = rewardedAd;
                        adMobRewardedAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                            @Override
                            public void onAdDismissedFullScreenContent() {
                                adMobRewardedAd = null;
                                notifyRewardCompleted();
                                preloadAdMobRewarded(targetUnitId);
                            }
                            @Override
                            public void onAdFailedToShowFullScreenContent(@NonNull com.google.android.gms.ads.AdError adError) {
                                adMobRewardedAd = null;
                                notifyRewardCompleted();
                            }
                        });
                        adMobRewardedAd.show(MainActivity.this, rewardItem -> {
                            notifyRewardCompleted();
                        });
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        android.util.Log.e("AdMob", "Rewarded ad failed to load: " + loadAdError.getMessage() + " (code: " + loadAdError.getCode() + ")");
                        adMobRewardedAd = null;
                        notifyRewardCompleted();
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                notifyRewardCompleted();
            }
        });
    }

    public void preloadAdMobRewarded(final String adUnitId) {
        if (adUnitId == null || adUnitId.trim().isEmpty()) return;
        final String targetUnitId = adUnitId.trim();
        runOnUiThread(() -> {
            try {
                initGoogleMobileAds();
                lastAdMobRewardedUnit = targetUnitId;
                AdRequest adRequest = new AdRequest.Builder().build();
                RewardedAd.load(MainActivity.this, targetUnitId, adRequest, new RewardedAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull RewardedAd rewardedAd) {
                        android.util.Log.d("AdMob", "Rewarded ad preloaded successfully: " + targetUnitId);
                        adMobRewardedAd = rewardedAd;
                    }
                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        android.util.Log.e("AdMob", "Rewarded ad preload failed: " + loadAdError.getMessage() + " (code: " + loadAdError.getCode() + ")");
                        adMobRewardedAd = null;
                    }
                });
            } catch (Exception ignore) {}
        });
    }

    public class AndroidNativeInterface {

        @JavascriptInterface
        public void initAdMob(final String appId) {
            MainActivity.this.initGoogleMobileAds();
        }

        @JavascriptInterface
        public void showAdMobBanner(final String adUnitId) {
            MainActivity.this.showAdMobBanner(adUnitId);
        }

        @JavascriptInterface
        public void hideAdMobBanner() {
            MainActivity.this.hideAdMobBanner();
        }

        @JavascriptInterface
        public void showAdMobInterstitial(final String adUnitId) {
            MainActivity.this.showAdMobInterstitialNative(adUnitId);
        }

        @JavascriptInterface
        public void preloadAdMobInterstitial(final String adUnitId) {
            MainActivity.this.preloadAdMobRewardedInterstitial(adUnitId);
        }

        @JavascriptInterface
        public void showAdMobRewardedInterstitial(final String adUnitId) {
            MainActivity.this.showAdMobInterstitialNative(adUnitId);
        }

        @JavascriptInterface
        public void preloadAdMobRewardedInterstitial(final String adUnitId) {
            MainActivity.this.preloadAdMobRewardedInterstitial(adUnitId);
        }

        @JavascriptInterface
        public void showAdMobRewarded(final String adUnitId) {
            MainActivity.this.loadAndShowAdMobRewarded(adUnitId);
        }

        @JavascriptInterface
        public void preloadAdMobRewarded(final String adUnitId) {
            MainActivity.this.preloadAdMobRewarded(adUnitId);
        }

        @JavascriptInterface
        public void applySubscriptionUserAgent() {
            MainActivity.this.applyCustomUserAgent();
        }

        @JavascriptInterface
        public String getUserAgent() {
            return CUSTOM_USER_AGENT;
        }

        @JavascriptInterface
        public boolean openUpiIntent(String upiUrl) {
            try {
                if (upiUrl != null && !upiUrl.isEmpty()) {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(upiUrl));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    return true;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return false;
        }

        @JavascriptInterface
        public void showRewardedVideo() {
            if (lastAdMobRewardedUnit != null && !lastAdMobRewardedUnit.isEmpty()) {
                MainActivity.this.loadAndShowAdMobRewarded(lastAdMobRewardedUnit);
            } else {
                notifyRewardCompleted();
            }
        }

        @JavascriptInterface
        public void showInterstitial() {
            if (lastAdMobInterstitialUnit != null && !lastAdMobInterstitialUnit.isEmpty()) {
                MainActivity.this.loadAndShowAdMobInterstitial(lastAdMobInterstitialUnit);
            }
        }

        @JavascriptInterface
        public void showBanner() {
            // compatibility
        }

        @JavascriptInterface
        public void trackImpression(String adId) {
        }

        @JavascriptInterface
        public void trackClick(String adId) {
        }

        @JavascriptInterface
        public void setLandscape() {
            runOnUiThread(() -> {
                try {
                    setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        @JavascriptInterface
        public void setPortrait() {
            runOnUiThread(() -> {
                try {
                    setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        @JavascriptInterface
        public void unlockOrientation() {
            runOnUiThread(() -> {
                try {
                    setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        @JavascriptInterface
        public boolean hasExoPlayer() {
            return true;
        }

        @JavascriptInterface
        public boolean hasInlineExoPlayer() {
            return true;
        }

        @JavascriptInterface
        public void startInlineExoPlayer(final String url, final String title, final boolean isSubscribed, final int previewLimitSeconds,
                                         final float leftCss, final float topCss, final float widthCss, final float heightCss, final float headerBottomCss) {
            MainActivity.this.startInlineExoPlayer(url, title, isSubscribed, previewLimitSeconds, leftCss, topCss, widthCss, heightCss, headerBottomCss);
        }

        @JavascriptInterface
        public void updateInlineExoBounds(final float leftCss, final float topCss, final float widthCss, final float heightCss, final float headerBottomCss) {
            MainActivity.this.updateInlineExoBounds(leftCss, topCss, widthCss, heightCss, headerBottomCss);
        }

        @JavascriptInterface
        public void setInlineExoVisible(final boolean visible) {
            MainActivity.this.setInlineExoVisible(visible);
        }

        @JavascriptInterface
        public void setInlineExoFullscreen(final boolean fullscreen) {
            MainActivity.this.setInlineExoFullscreen(fullscreen);
        }

        @JavascriptInterface
        public void setInlineExoSubscribed(final boolean subscribed) {
            MainActivity.this.setInlineExoSubscribed(subscribed);
        }

        @JavascriptInterface
        public void stopInlineExoPlayer() {
            MainActivity.this.stopInlineExoPlayer();
        }

        @JavascriptInterface
        public void openExoPlayer(final String url, final String title, final boolean isSubscribed, final int previewLimitSeconds) {
            if (url == null || url.trim().isEmpty()) return;
            runOnUiThread(() -> {
                try {
                    Intent intent = new Intent(MainActivity.this, ExoPlayerActivity.class);
                    intent.putExtra(ExoPlayerActivity.EXTRA_URL, url.trim());
                    intent.putExtra(ExoPlayerActivity.EXTRA_TITLE, title != null ? title : "Movie Player");
                    intent.putExtra(ExoPlayerActivity.EXTRA_IS_SUBSCRIBED, isSubscribed);
                    intent.putExtra(ExoPlayerActivity.EXTRA_PREVIEW_LIMIT, previewLimitSeconds > 0 ? previewLimitSeconds : 20);
                    startActivityForResult(intent, EXO_PLAYER_REQ_CODE);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        @JavascriptInterface
        public void closeExoPlayer() {
            ExoPlayerActivity.closeActiveInstance();
            MainActivity.this.stopInlineExoPlayer();
        }

        @JavascriptInterface
        public void keepScreenOn(final boolean enable) {
            runOnUiThread(() -> {
                try {
                    if (enable) {
                        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    } else {
                        getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        @JavascriptInterface
        public void hideSystemBars() {
            MainActivity.this.hideSystemBars();
        }

        @JavascriptInterface
        public void shareApp(final String text, final String url) {
            runOnUiThread(() -> {
                try {
                    Intent shareIntent = new Intent(Intent.ACTION_SEND);
                    shareIntent.setType("text/plain");
                    String finalBody = (text != null && !text.trim().isEmpty()) ? text.trim() + "\n" + url : url;
                    shareIntent.putExtra(Intent.EXTRA_SUBJECT, "VDOSky App");
                    shareIntent.putExtra(Intent.EXTRA_TEXT, finalBody);
                    Intent chooser = Intent.createChooser(shareIntent, "Share VDOSky App via");
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(chooser);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        @JavascriptInterface
        public void openInChrome(final String url) {
            openExternalUrl(url);
        }

        @JavascriptInterface
        public void openChromeDirectly(final String url) {
            openExternalUrl(url);
        }

        @JavascriptInterface
        public void openUrl(final String url) {
            openExternalUrl(url);
        }

        @JavascriptInterface
        public void openCustomTab(final String url) {
            if (url == null || url.trim().isEmpty()) return;
            runOnUiThread(() -> {
                try {
                    Uri uri = Uri.parse(url.trim());
                    CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
                    builder.setShowTitle(true);
                    CustomTabsIntent customTabsIntent = builder.build();
                    customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    customTabsIntent.launchUrl(MainActivity.this, uri);
                } catch (Exception e) {
                    openExternalUrl(url);
                }
            });
        }

        @JavascriptInterface
        public void openExternalUrl(final String url) {
            MainActivity.this.openExternalUrl(url);
        }

        @JavascriptInterface
        public void requestCameraAndLocationPermissions() {
            runOnUiThread(() -> {
                checkAndRequestAppPermissions();
            });
        }

        @JavascriptInterface
        public boolean hasMicrophonePermission() {
            return ContextCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        }

        @JavascriptInterface
        public void requestMicrophonePermission() {
            runOnUiThread(() -> {
                MainActivity.this.requestMicrophonePermission(false);
            });
        }

        @JavascriptInterface
        public void requestMicrophoneForVoiceSearch() {
            runOnUiThread(() -> {
                MainActivity.this.requestMicrophonePermission(true);
            });
        }

        @JavascriptInterface
        public String getPendingPdfData() {
            return pendingPdfBase64 != null ? pendingPdfBase64 : "";
        }

        @JavascriptInterface
        public String getPendingPdfName() {
            return pendingPdfName != null ? pendingPdfName : "";
        }

        @JavascriptInterface
        public void clearPendingPdfData() {
            pendingPdfBase64 = null;
            pendingPdfName = null;
        }

        @JavascriptInterface
        public void pickPdfFile() {
            runOnUiThread(() -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.setType("application/pdf");
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    startActivityForResult(Intent.createChooser(intent, "Select PDF File"), NATIVE_PDF_PICKER_REQ);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        @JavascriptInterface
        public void setCameraFacing(final String facing) {
            cameraCaptureFacing = facing != null ? facing : "environment";
        }

        @JavascriptInterface
        public void openNativeCamera(final String facing) {
            runOnUiThread(() -> {
                MainActivity.this.launchNativeCameraDirectly(facing);
            });
        }

        @JavascriptInterface
        public void pickStore35File() {
            runOnUiThread(() -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.setType("*/*");
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    startActivityForResult(Intent.createChooser(intent, "Select File (Max 35MB)"), NATIVE_STORE35_PICKER_REQ);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        @JavascriptInterface
        public void downloadAndInstallApk(final String downloadUrl, final String fileName) {
            startDirectApkDownload(downloadUrl, fileName);
        }

        @JavascriptInterface
        public boolean saveBase64File(final String base64Data, final String fileName, final String mimeType) {
            return MainActivity.this.saveBase64FileToDownloads(base64Data, fileName, mimeType);
        }

        @JavascriptInterface
        public void downloadUrl(final String url, final String fileName) {
            MainActivity.this.startSystemDownload(url, fileName);
        }

        @JavascriptInterface
        public void openDownloadedFile(final String uriString, final String mimeType) {
            MainActivity.this.openDownloadedFileDirectly(uriString, mimeType);
        }

        @JavascriptInterface
        public void shareDownloadedFile(final String uriString, final String mimeType, final String fileName) {
            MainActivity.this.shareDownloadedFile(uriString, mimeType, fileName);
        }

        @JavascriptInterface
        public boolean isAndroidNative() {
            return true;
        }
    }

    private void handleSelectedPdfUri(Uri uri) {
        if (uri == null) return;
        new Thread(() -> {
            try {
                String fileName = "document.pdf";
                android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null);
                if (cursor != null) {
                    int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        fileName = cursor.getString(nameIndex);
                    }
                    cursor.close();
                } else if (uri.getLastPathSegment() != null) {
                    fileName = uri.getLastPathSegment();
                }

                InputStream inputStream = getContentResolver().openInputStream(uri);
                if (inputStream != null) {
                    java.io.ByteArrayOutputStream byteBuffer = new java.io.ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = inputStream.read(buffer)) != -1) {
                        byteBuffer.write(buffer, 0, len);
                    }
                    inputStream.close();
                    byte[] pdfBytes = byteBuffer.toByteArray();
                    final String base64 = android.util.Base64.encodeToString(pdfBytes, android.util.Base64.NO_WRAP);
                    final String finalFileName = fileName;

                    pendingPdfBase64 = base64;
                    pendingPdfName = finalFileName;

                    runOnUiThread(() -> {
                        try {
                            if (getBridge() != null && getBridge().getWebView() != null) {
                                String escapedName = finalFileName.replace("'", "\\'");
                                getBridge().getWebView().evaluateJavascript(
                                    "window.openPdfSection && window.openPdfSection('" + base64 + "', '" + escapedName + "')",
                                    null
                                );
                            }
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void handleSelectedStore35Uri(Uri uri) {
        if (uri == null) return;
        new Thread(() -> {
            try {
                String fileName = "upload.bin";
                long fileSize = 0;
                android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null);
                if (cursor != null) {
                    int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    int sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE);
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        fileName = cursor.getString(nameIndex);
                    }
                    if (sizeIndex != -1) {
                        fileSize = cursor.getLong(sizeIndex);
                    }
                    cursor.close();
                } else if (uri.getLastPathSegment() != null) {
                    fileName = uri.getLastPathSegment();
                }

                String mimeType = getContentResolver().getType(uri);
                if (mimeType == null) mimeType = "application/octet-stream";

                final long finalSize = fileSize;
                final String finalFileName = fileName;
                final String finalMimeType = mimeType;

                if (finalSize > (35L * 1024L * 1024L)) {
                    runOnUiThread(() -> {
                        try {
                            if (getBridge() != null && getBridge().getWebView() != null) {
                                getBridge().getWebView().evaluateJavascript("window.showStore35SizeLimitModal && window.showStore35SizeLimitModal()", null);
                            }
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    });
                    return;
                }

                InputStream inputStream = getContentResolver().openInputStream(uri);
                if (inputStream != null) {
                    java.io.ByteArrayOutputStream byteBuffer = new java.io.ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = inputStream.read(buffer)) != -1) {
                        byteBuffer.write(buffer, 0, len);
                    }
                    inputStream.close();
                    byte[] fileBytes = byteBuffer.toByteArray();

                    if (fileBytes.length > (35 * 1024 * 1024)) {
                        runOnUiThread(() -> {
                            try {
                                if (getBridge() != null && getBridge().getWebView() != null) {
                                    getBridge().getWebView().evaluateJavascript("window.showStore35SizeLimitModal && window.showStore35SizeLimitModal()", null);
                                }
                            } catch (Exception ex) {
                                ex.printStackTrace();
                            }
                        });
                        return;
                    }

                    final String base64 = android.util.Base64.encodeToString(fileBytes, android.util.Base64.NO_WRAP);
                    runOnUiThread(() -> {
                        try {
                            if (getBridge() != null && getBridge().getWebView() != null) {
                                String escapedName = finalFileName.replace("'", "\\'");
                                getBridge().getWebView().evaluateJavascript(
                                    "window.handleNativeStore35FileSelected && window.handleNativeStore35FileSelected('" + escapedName + "', '" + base64 + "', '" + finalMimeType + "', " + fileBytes.length + ")",
                                    null
                                );
                            }
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void launchNativeCameraDirectly(final String facing) {
        try {
            cameraCaptureFacing = facing != null ? facing : "environment";
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                isCameraLaunchPending = true;
                ActivityCompat.requestPermissions(this, new String[]{ android.Manifest.permission.CAMERA }, PERMISSION_REQ_CODE);
                return;
            }

            isCameraLaunchPending = false;
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

            File storageDir = getExternalCacheDir() != null ? getExternalCacheDir() : getCacheDir();
            File photoFile = File.createTempFile("GPS_CAM_" + System.currentTimeMillis() + "_", ".jpg", storageDir);
            cameraCaptureUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photoFile);

            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraCaptureUri);
            takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

            boolean isFront = "user".equalsIgnoreCase(facing) || "front".equalsIgnoreCase(facing);
            if (isFront) {
                takePictureIntent.putExtra("android.intent.extras.CAMERA_FACING", 1);
                takePictureIntent.putExtra("android.intent.extras.LENS_FACING_FRONT", 1);
                takePictureIntent.putExtra("android.intent.extra.USE_FRONT_CAMERA", true);
            } else {
                takePictureIntent.putExtra("android.intent.extras.CAMERA_FACING", 0);
                takePictureIntent.putExtra("android.intent.extras.LENS_FACING_FRONT", 0);
                takePictureIntent.putExtra("android.intent.extra.USE_FRONT_CAMERA", false);
            }

            startActivityForResult(takePictureIntent, NATIVE_CAMERA_REQ);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void handleCapturedCameraUri(final Uri uri, final String facing) {
        new Thread(() -> {
            try {
                if (uri == null) return;
                InputStream inputStream = getContentResolver().openInputStream(uri);
                if (inputStream != null) {
                    java.io.ByteArrayOutputStream byteBuffer = new java.io.ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = inputStream.read(buffer)) != -1) {
                        byteBuffer.write(buffer, 0, len);
                    }
                    inputStream.close();
                    byte[] fileBytes = byteBuffer.toByteArray();
                    if (fileBytes.length > 0) {
                        final String base64 = android.util.Base64.encodeToString(fileBytes, android.util.Base64.NO_WRAP);
                        final String safeFacing = (facing != null ? facing : "environment").replace("'", "\\'");
                        runOnUiThread(() -> {
                            try {
                                if (getBridge() != null && getBridge().getWebView() != null) {
                                    getBridge().getWebView().evaluateJavascript(
                                        "window.handleGpsNativeCameraPhotoCaptured && window.handleGpsNativeCameraPhotoCaptured('data:image/jpeg;base64," + base64 + "', '" + safeFacing + "')",
                                        null
                                    );
                                }
                            } catch (Exception ex) {
                                ex.printStackTrace();
                            }
                        });
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void startDirectApkDownload(final String downloadUrl, final String fileName) {
        if (downloadUrl == null || downloadUrl.trim().isEmpty()) return;

        new Thread(() -> {
            HttpURLConnection connection = null;
            InputStream in = null;
            FileOutputStream out = null;
            try {
                final String apkFileName = (fileName != null && !fileName.trim().isEmpty())
                        ? fileName.trim()
                        : "VDOSKy_latest.apk";

                // Save to app-specific external files dir (NO storage permission required on any Android version)
                File downloadDir = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "updates");
                if (!downloadDir.exists()) {
                    downloadDir.mkdirs();
                }

                File apkFile = new File(downloadDir, apkFileName);
                if (apkFile.exists()) {
                    apkFile.delete();
                }

                // Connect with redirect support (GitHub / CDN 301/302 redirects)
                URL url = new URL(downloadUrl);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(30000);
                connection.setInstanceFollowRedirects(true);
                connection.connect();

                int responseCode = connection.getResponseCode();
                // Handle manual redirect if needed
                if (responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                    responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                    responseCode == 307 || responseCode == 308) {
                    String newUrl = connection.getHeaderField("Location");
                    connection.disconnect();
                    url = new URL(newUrl);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setConnectTimeout(15000);
                    connection.setReadTimeout(30000);
                    connection.connect();
                }

                int fileLength = connection.getContentLength();
                in = connection.getInputStream();
                out = new FileOutputStream(apkFile);

                byte[] buffer = new byte[8192];
                long total = 0;
                int count;
                int lastReportedPercent = -1;

                while ((count = in.read(buffer)) != -1) {
                    total += count;
                    out.write(buffer, 0, count);

                    if (fileLength > 0) {
                        int percent = (int) ((total * 100) / fileLength);
                        if (percent != lastReportedPercent && percent % 5 == 0) {
                            lastReportedPercent = percent;
                            dispatchProgressToWebView(percent);
                        }
                    }
                }

                out.flush();
                dispatchProgressToWebView(100);

                // APK download complete! Trigger native package installer directly
                if (apkFile.exists() && apkFile.length() > 0) {
                    runOnUiThread(() -> launchPackageInstaller(apkFile));
                }

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    if (out != null) out.close();
                    if (in != null) in.close();
                    if (connection != null) connection.disconnect();
                } catch (Exception ignored) {}
            }
        }).start();
    }

    private void dispatchProgressToWebView(final int percent) {
        runOnUiThread(() -> {
            try {
                if (getBridge() != null && getBridge().getWebView() != null) {
                    String script = "window.dispatchEvent(new CustomEvent('apkDownloadProgress', { detail: { progress: " + percent + " } }));";
                    getBridge().getWebView().evaluateJavascript(script, null);
                }
            } catch (Exception ignored) {}
        });
    }

    private void launchPackageInstaller(File apkFile) {
        try {
            Uri apkUri;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                apkUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", apkFile);
            } else {
                apkUri = Uri.fromFile(apkFile);
            }

            Intent installIntent = new Intent(Intent.ACTION_VIEW);
            installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            installIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

            startActivity(installIntent);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public void startActivity(Intent intent) {
        if (isYouTubeIntent(intent)) {
            // Block launching external YouTube app from VDOSky
            return;
        }
        super.startActivity(intent);
    }

    @Override
    public void startActivityForResult(Intent intent, int requestCode, Bundle options) {
        if (isYouTubeIntent(intent)) {
            // Block launching external YouTube app from VDOSky
            return;
        }
        super.startActivityForResult(intent, requestCode, options);
    }

    public void requestMicrophonePermission(final boolean forVoiceSearch) {
        try {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                isVoiceSearchPending = forVoiceSearch;
                ActivityCompat.requestPermissions(this, new String[]{ android.Manifest.permission.RECORD_AUDIO }, MIC_PERMISSION_REQ_CODE);
            } else {
                runOnUiThread(() -> {
                    if (getBridge() != null && getBridge().getWebView() != null) {
                        getBridge().getWebView().evaluateJavascript(
                            "window.onAndroidMicrophonePermissionGranted && window.onAndroidMicrophonePermissionGranted(" + forVoiceSearch + ")",
                            null
                        );
                    }
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQ_CODE) {
            boolean cameraGranted = false;
            if (permissions != null && grantResults != null) {
                for (int i = 0; i < permissions.length; i++) {
                    if (android.Manifest.permission.CAMERA.equals(permissions[i]) && grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        cameraGranted = true;
                    }
                }
            }
            if (cameraGranted && isCameraLaunchPending && cameraCaptureFacing != null) {
                isCameraLaunchPending = false;
                launchNativeCameraDirectly(cameraCaptureFacing);
            } else {
                isCameraLaunchPending = false;
            }
        } else if (requestCode == MIC_PERMISSION_REQ_CODE) {
            boolean micGranted = false;
            if (permissions != null && grantResults != null) {
                for (int i = 0; i < permissions.length; i++) {
                    if (android.Manifest.permission.RECORD_AUDIO.equals(permissions[i]) && grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        micGranted = true;
                    }
                }
            }
            if (micGranted) {
                if (pendingWebAudioPermissionRequest != null) {
                    try {
                        pendingWebAudioPermissionRequest.grant(pendingWebAudioPermissionRequest.getResources());
                    } catch (Exception ignored) {}
                    pendingWebAudioPermissionRequest = null;
                }
                final boolean wasVoiceSearch = isVoiceSearchPending;
                isVoiceSearchPending = false;
                runOnUiThread(() -> {
                    if (getBridge() != null && getBridge().getWebView() != null) {
                        getBridge().getWebView().evaluateJavascript(
                            "window.onAndroidMicrophonePermissionGranted && window.onAndroidMicrophonePermissionGranted(" + wasVoiceSearch + ")",
                            null
                        );
                    }
                });
            } else {
                if (pendingWebAudioPermissionRequest != null) {
                    try {
                        pendingWebAudioPermissionRequest.deny();
                    } catch (Exception ignored) {}
                    pendingWebAudioPermissionRequest = null;
                }
                isVoiceSearchPending = false;
                runOnUiThread(() -> {
                    if (getBridge() != null && getBridge().getWebView() != null) {
                        getBridge().getWebView().evaluateJavascript(
                            "window.onAndroidMicrophonePermissionDenied && window.onAndroidMicrophonePermissionDenied()",
                            null
                        );
                    }
                });
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CHOOSER_REQ_CODE) {
            if (filePathCallback != null) {
                Uri[] results = null;
                if (resultCode == RESULT_OK) {
                    if (cameraCaptureUri != null) {
                        results = new Uri[]{ cameraCaptureUri };
                    } else if (data != null) {
                        if (data.getData() != null) {
                            results = new Uri[]{ data.getData() };
                        } else if (data.getClipData() != null) {
                            int count = data.getClipData().getItemCount();
                            results = new Uri[count];
                            for (int i = 0; i < count; i++) {
                                results[i] = data.getClipData().getItemAt(i).getUri();
                            }
                        }
                    }
                }
                filePathCallback.onReceiveValue(results);
                filePathCallback = null;
                cameraCaptureUri = null;
            }
            return;
        } else if (requestCode == NATIVE_CAMERA_REQ) {
            if (resultCode == RESULT_OK && cameraCaptureUri != null) {
                handleCapturedCameraUri(cameraCaptureUri, cameraCaptureFacing != null ? cameraCaptureFacing : "environment");
            }
            cameraCaptureUri = null;
            cameraCaptureFacing = null;
            isCameraLaunchPending = false;
            return;
        } else if (requestCode == NATIVE_PDF_PICKER_REQ) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                handleSelectedPdfUri(data.getData());
            }
            return;
        } else if (requestCode == NATIVE_STORE35_PICKER_REQ) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                handleSelectedStore35Uri(data.getData());
            }
            return;
        } else if (requestCode == EXO_PLAYER_REQ_CODE) {
            runOnUiThread(() -> {
                try {
                    if (getBridge() != null && getBridge().getWebView() != null) {
                        if (resultCode == ExoPlayerActivity.RESULT_PREVIEW_EXPIRED) {
                            getBridge().getWebView().evaluateJavascript(
                                    "window.onNativeExoPreviewExpired && window.onNativeExoPreviewExpired()",
                                    null
                            );
                        } else if (resultCode == ExoPlayerActivity.RESULT_VIDEO_ENDED) {
                            getBridge().getWebView().evaluateJavascript(
                                    "window.onNativeExoVideoEnded && window.onNativeExoVideoEnded()",
                                    null
                            );
                        } else {
                            getBridge().getWebView().evaluateJavascript(
                                    "window.onNativeExoClosed && window.onNativeExoClosed()",
                                    null
                            );
                        }
                    }
                } catch (Exception ignored) {}
            });
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private boolean isYouTubeIntent(Intent intent) {
        if (intent == null) return false;
        try {
            Uri data = intent.getData();
            if (data != null) {
                String scheme = data.getScheme();
                String host = data.getHost();
                if ("vnd.youtube".equalsIgnoreCase(scheme)) return true;
                if (host != null && (host.contains("youtube.com") || host.contains("youtu.be"))) {
                    return true;
                }
            }
            String pkg = intent.getPackage();
            if (pkg != null && (pkg.contains("youtube") || pkg.contains("com.google.android.youtube"))) {
                return true;
            }
            if (intent.getComponent() != null) {
                String comp = intent.getComponent().flattenToString();
                if (comp.contains("youtube")) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    public void openExternalUrl(final String url) {
        if (url == null || url.trim().isEmpty()) return;
        runOnUiThread(() -> {
            try {
                Uri uri = Uri.parse(url.trim());
                try {
                    CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
                    builder.setShowTitle(true);
                    CustomTabsIntent customTabsIntent = builder.build();
                    customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    customTabsIntent.launchUrl(MainActivity.this, uri);
                    return;
                } catch (Exception e1) {}

                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    intent.setPackage("com.android.chrome");
                    startActivity(intent);
                } catch (Exception chromeNotInstalled) {
                    Intent fallbackIntent = new Intent(Intent.ACTION_VIEW, uri);
                    fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(fallbackIntent);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public boolean saveBase64FileToDownloads(final String base64Data, final String fileName, final String mimeType) {
        if (base64Data == null || base64Data.trim().isEmpty()) {
            return false;
        }
        new Thread(() -> {
            NotificationManager notifManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            int notifId = (int) (System.currentTimeMillis() % 100000);
            String channelId = "vdosky_downloads";

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && notifManager != null) {
                NotificationChannel channel = new NotificationChannel(channelId, "File Downloads", NotificationManager.IMPORTANCE_LOW);
                channel.setDescription("Download progress notifications");
                notifManager.createNotificationChannel(channel);
            }

            try {
                String cleanName = (fileName != null && !fileName.trim().isEmpty())
                        ? fileName.trim()
                        : "download_" + System.currentTimeMillis();
                String cleanMime = (mimeType != null && !mimeType.trim().isEmpty())
                        ? mimeType.trim()
                        : "application/octet-stream";

                cleanName = ensureExtension(cleanName, cleanMime);

                String cleanData = base64Data;
                int commaIdx = cleanData.indexOf(",");
                if (commaIdx != -1) {
                    cleanData = cleanData.substring(commaIdx + 1);
                }

                byte[] fileBytes;
                try {
                    fileBytes = Base64.decode(cleanData, Base64.DEFAULT);
                } catch (Exception e) {
                    fileBytes = Base64.decode(cleanData.replaceAll("\\s+", ""), Base64.DEFAULT);
                }

                if (fileBytes == null || fileBytes.length == 0) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to decode file", Toast.LENGTH_SHORT).show());
                    return;
                }

                final int totalBytes = fileBytes.length;
                final String finalName = cleanName;
                final String finalMime = cleanMime;

                // Post initial download progress notification
                NotificationCompat.Builder notifBuilder = new NotificationCompat.Builder(MainActivity.this, channelId)
                        .setContentTitle("Downloading " + cleanName)
                        .setContentText("0% • " + formatBytes(totalBytes))
                        .setSmallIcon(android.R.drawable.stat_sys_download)
                        .setProgress(100, 0, false)
                        .setOngoing(true)
                        .setOnlyAlertOnce(true);

                if (notifManager != null) {
                    notifManager.notify(notifId, notifBuilder.build());
                }

                // Notify web UI of initial 0% download start
                runOnUiThread(() -> {
                    try {
                        if (getBridge() != null && getBridge().getWebView() != null) {
                            getBridge().getWebView().evaluateJavascript(
                                    "window.onDownloadProgressUpdate && window.onDownloadProgressUpdate('" + finalName.replace("'", "\\'") + "', 0, 0, " + totalBytes + ")",
                                    null
                            );
                        }
                    } catch (Exception ignore) {}
                });

                boolean savedSuccessfully = false;
                Uri resultUri = null;
                File savedFile = null;

                // 1. Android 10+ (API 29+) MediaStore scoped storage -> save directly into user's public Downloads
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        ContentValues values = new ContentValues();
                        values.put(MediaStore.MediaColumns.DISPLAY_NAME, cleanName);
                        values.put(MediaStore.MediaColumns.MIME_TYPE, cleanMime);
                        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                        values.put(MediaStore.MediaColumns.IS_PENDING, 1);

                        Uri collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
                        resultUri = getContentResolver().insert(collection, values);
                        if (resultUri == null) {
                            String uniqueName = appendTimestampToFileName(cleanName);
                            values.put(MediaStore.MediaColumns.DISPLAY_NAME, uniqueName);
                            resultUri = getContentResolver().insert(collection, values);
                            if (resultUri != null) cleanName = uniqueName;
                        }

                        if (resultUri != null) {
                            try (OutputStream out = getContentResolver().openOutputStream(resultUri)) {
                                if (out != null) {
                                    int chunkSize = 32768; // 32KB
                                    int written = 0;
                                    while (written < totalBytes) {
                                        int len = Math.min(chunkSize, totalBytes - written);
                                        out.write(fileBytes, written, len);
                                        written += len;

                                        int pct = (int) (((long) written * 100) / totalBytes);
                                        if (pct % 10 == 0 || written == totalBytes) {
                                            notifBuilder.setProgress(100, pct, false)
                                                    .setContentText(pct + "% • " + formatBytes(written) + " / " + formatBytes(totalBytes));
                                            if (notifManager != null) notifManager.notify(notifId, notifBuilder.build());

                                            final int curPct = pct;
                                            final int curWritten = written;
                                            runOnUiThread(() -> {
                                                try {
                                                    if (getBridge() != null && getBridge().getWebView() != null) {
                                                        getBridge().getWebView().evaluateJavascript(
                                                                "window.onDownloadProgressUpdate && window.onDownloadProgressUpdate('" + finalName.replace("'", "\\'") + "', " + curPct + ", " + curWritten + ", " + totalBytes + ")",
                                                                null
                                                        );
                                                    }
                                                } catch (Exception ignore) {}
                                            });
                                        }
                                    }
                                    out.flush();
                                    savedSuccessfully = true;
                                }
                            }

                            // CRITICAL for Android 10+: clear IS_PENDING so file is visible immediately to user!
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && savedSuccessfully) {
                                try {
                                    ContentValues updateValues = new ContentValues();
                                    updateValues.put(MediaStore.MediaColumns.IS_PENDING, 0);
                                    getContentResolver().update(resultUri, updateValues, null, null);
                                } catch (Exception ignored) {}
                            }
                        }
                    } catch (Exception e1) {
                        e1.printStackTrace();
                    }

                    // 1b. If Downloads collection failed, and it is an image, write to MediaStore.Images
                    if (!savedSuccessfully && cleanMime.startsWith("image/")) {
                        try {
                            ContentValues values = new ContentValues();
                            values.put(MediaStore.Images.Media.DISPLAY_NAME, cleanName);
                            values.put(MediaStore.Images.Media.MIME_TYPE, cleanMime);
                            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/VDOSky");
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                values.put(MediaStore.Images.Media.IS_PENDING, 1);
                            }

                            Uri imgUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                            if (imgUri != null) {
                                try (OutputStream out = getContentResolver().openOutputStream(imgUri)) {
                                    if (out != null) {
                                        out.write(fileBytes);
                                        out.flush();
                                        savedSuccessfully = true;
                                        resultUri = imgUri;
                                    }
                                }
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && savedSuccessfully) {
                                    try {
                                        ContentValues updateValues = new ContentValues();
                                        updateValues.put(MediaStore.Images.Media.IS_PENDING, 0);
                                        getContentResolver().update(imgUri, updateValues, null, null);
                                    } catch (Exception ignored) {}
                                }
                            }
                        } catch (Exception eImg) {
                            eImg.printStackTrace();
                        }
                    }
                }

                // 2. Direct public Downloads directory (Android 9 or below, or legacy storage)
                if (!savedSuccessfully) {
                    try {
                        File publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                        if (publicDownloads != null) {
                            if (!publicDownloads.exists()) publicDownloads.mkdirs();
                            File target = new File(publicDownloads, cleanName);
                            if (target.exists()) {
                                target = new File(publicDownloads, appendTimestampToFileName(cleanName));
                            }
                            try (FileOutputStream fos = new FileOutputStream(target)) {
                                fos.write(fileBytes);
                                fos.flush();
                                savedSuccessfully = true;
                                savedFile = target;
                                cleanName = target.getName();
                            }
                        }
                    } catch (Exception e2) {
                        e2.printStackTrace();
                    }
                }

                // 3. Fallback to app external Downloads directory if public folder was blocked
                if (!savedSuccessfully) {
                    try {
                        File appDownloads = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                        if (appDownloads != null) {
                            if (!appDownloads.exists()) appDownloads.mkdirs();
                            File target = new File(appDownloads, cleanName);
                            try (FileOutputStream fos = new FileOutputStream(target)) {
                                fos.write(fileBytes);
                                fos.flush();
                                savedSuccessfully = true;
                                savedFile = target;
                                cleanName = target.getName();
                            }
                        }
                    } catch (Exception e3) {
                        e3.printStackTrace();
                    }
                }

                if (savedSuccessfully && savedFile != null) {
                    try {
                        MediaScannerConnection.scanFile(
                                MainActivity.this,
                                new String[]{ savedFile.getAbsolutePath() },
                                new String[]{ cleanMime },
                                null
                        );
                    } catch (Exception ignored) {}
                }

                final boolean finalSaved = savedSuccessfully;
                final Uri openUri = resultUri != null ? resultUri : (savedFile != null ? FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".fileprovider", savedFile) : null);
                final String openUriString = openUri != null ? openUri.toString() : "";

                if (finalSaved) {
                    // Update Android Notification to "Download complete" with Tap-to-Open Intent!
                    Intent viewIntent = new Intent(Intent.ACTION_VIEW);
                    if (openUri != null) {
                        viewIntent.setDataAndType(openUri, cleanMime);
                        viewIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                    }
                    int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        piFlags |= PendingIntent.FLAG_IMMUTABLE;
                    }
                    PendingIntent pi = PendingIntent.getActivity(MainActivity.this, notifId, viewIntent, piFlags);

                    notifBuilder.setContentTitle("Download complete")
                            .setContentText(cleanName + " (" + formatBytes(totalBytes) + ") • Tap to open")
                            .setSmallIcon(android.R.drawable.stat_sys_download_done)
                            .setProgress(0, 0, false)
                            .setOngoing(false)
                            .setAutoCancel(true)
                            .setContentIntent(pi);

                    if (notifManager != null) {
                        notifManager.notify(notifId, notifBuilder.build());
                    }

                    // Notify web UI of completed download with Open capability
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "✅ Download complete: " + finalName, Toast.LENGTH_LONG).show();
                        try {
                            if (getBridge() != null && getBridge().getWebView() != null) {
                                getBridge().getWebView().evaluateJavascript(
                                        "window.onDownloadProgressComplete && window.onDownloadProgressComplete('" + finalName.replace("'", "\\'") + "', '" + openUriString.replace("'", "\\'") + "', '" + finalMime.replace("'", "\\'") + "', " + totalBytes + ")",
                                        null
                                );
                            }
                        } catch (Exception ignore) {}
                    });
                } else {
                    if (notifManager != null) notifManager.cancel(notifId);
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "❌ Download failed", Toast.LENGTH_SHORT).show();
                        try {
                            if (getBridge() != null && getBridge().getWebView() != null) {
                                getBridge().getWebView().evaluateJavascript(
                                        "window.onDownloadProgressError && window.onDownloadProgressError('" + finalName.replace("'", "\\'") + "', 'Failed to write file to device')",
                                        null
                                );
                            }
                        } catch (Exception ignore) {}
                    });
                }

            } catch (Exception ex) {
                ex.printStackTrace();
                if (notifManager != null) notifManager.cancel(notifId);
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Download error: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
                    try {
                        if (getBridge() != null && getBridge().getWebView() != null) {
                            getBridge().getWebView().evaluateJavascript(
                                    "window.onDownloadProgressError && window.onDownloadProgressError('" + fileName.replace("'", "\\'") + "', '" + (ex.getMessage() != null ? ex.getMessage().replace("'", "\\'") : "Error") + "')",
                                    null
                            );
                        }
                    } catch (Exception ignore) {}
                });
            }
        }).start();
        return true;
    }

    public void openDownloadedFileDirectly(final String uriString, final String mimeType) {
        if (uriString == null || uriString.trim().isEmpty()) return;
        runOnUiThread(() -> {
            try {
                Uri uri = Uri.parse(uriString.trim());
                Intent intent = new Intent(Intent.ACTION_VIEW);
                String safeMime = (mimeType != null && !mimeType.trim().isEmpty()) ? mimeType.trim() : "*/*";
                intent.setDataAndType(uri, safeMime);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(Intent.createChooser(intent, "Open file with"));
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(MainActivity.this, "Cannot open file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void shareDownloadedFile(final String uriString, final String mimeType, final String fileName) {
        if (uriString == null || uriString.trim().isEmpty()) return;
        runOnUiThread(() -> {
            try {
                Uri uri = Uri.parse(uriString.trim());
                Intent intent = new Intent(Intent.ACTION_SEND);
                String safeMime = (mimeType != null && !mimeType.trim().isEmpty()) ? mimeType.trim() : "*/*";
                intent.setType(safeMime);
                intent.putExtra(Intent.EXTRA_STREAM, uri);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(intent, "Share " + (fileName != null ? fileName : "file")));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void startSystemDownload(final String url, final String fileName) {
        if (url == null || url.trim().isEmpty()) return;
        runOnUiThread(() -> {
            try {
                Uri downloadUri = Uri.parse(url.trim());
                DownloadManager.Request request = new DownloadManager.Request(downloadUri);
                String safeName = (fileName != null && !fileName.trim().isEmpty())
                        ? fileName.trim()
                        : URLUtil.guessFileName(url, null, null);
                request.setTitle(safeName);
                request.setDescription("Downloading file...");
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, safeName);
                DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                if (dm != null) {
                    dm.enqueue(request);
                    Toast.makeText(MainActivity.this, "📥 Downloading to phone: " + safeName, Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                openExternalUrl(url);
            }
        });
    }

    private String formatBytes(long bytes) {
        if (bytes <= 0) return "0 B";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0);
        return String.format(java.util.Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0));
    }

    private String ensureExtension(String fileName, String mimeType) {
        if (fileName == null) fileName = "download";
        if (fileName.contains(".")) return fileName;
        if ("image/gif".equalsIgnoreCase(mimeType)) return fileName + ".gif";
        if ("image/png".equalsIgnoreCase(mimeType)) return fileName + ".png";
        if ("image/jpeg".equalsIgnoreCase(mimeType) || "image/jpg".equalsIgnoreCase(mimeType)) return fileName + ".jpg";
        if ("audio/mp3".equalsIgnoreCase(mimeType) || "audio/mpeg".equalsIgnoreCase(mimeType)) return fileName + ".mp3";
        if ("application/pdf".equalsIgnoreCase(mimeType)) return fileName + ".pdf";
        return fileName;
    }

    private String appendTimestampToFileName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot > 0) {
            return fileName.substring(0, dot) + "_" + System.currentTimeMillis() + fileName.substring(dot);
        }
        return fileName + "_" + System.currentTimeMillis();
    }

    // =========================================================================
    // INLINE NATIVE MEDIA3 EXOPLAYER (PORTRAIT 16:9 + SEAMLESS LANDSCAPE FULLSCREEN)
    // =========================================================================

    private final Runnable inlineExoPreviewRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isInlineExoActive || inlineExoPlayer == null || isFinishing() || isDestroyed()) return;

            long now = System.currentTimeMillis();
            long curPos = Math.max(0L, inlineExoPlayer.getCurrentPosition());
            if (inlineExoPlayer.getPlaybackState() == Player.STATE_READY) {
                if (inlineExoPendingSeekPosMs >= 0L) {
                    if (inlineExoPendingSeekPosMs > 15000L && curPos < 5000L) {
                        long recoverTarget = inlineExoPendingSeekPosMs;
                        inlineExoPendingSeekPosMs = -1L;
                        inlineExoPlayer.seekTo(recoverTarget);
                        inlineExoHandler.postDelayed(this, 250);
                        return;
                    }
                    if (Math.abs(curPos - inlineExoPendingSeekPosMs) <= 15000L || curPos >= inlineExoPendingSeekPosMs) {
                        inlineExoPendingSeekPosMs = -1L;
                        if (curPos > 0L) {
                            inlineExoLastValidPosMs = curPos;
                        }
                    }
                } else if (curPos > 0L) {
                    inlineExoLastValidPosMs = curPos;
                }
            }

            if (!inlineExoIsSubscribed && !inlineExoPreviewTriggeredExit) {
                long limitMs = Math.max(1, inlineExoPreviewLimitSec) * 1000L;

                if (curPos >= limitMs) {
                    triggerInlineExoPreviewExpired();
                    return;
                }

                if (inlineExoPlayer.isPlaying()) {
                    if (inlineExoLastPreviewTickMs > 0L) {
                        inlineExoAccumulatedPreviewMs += Math.max(0L, Math.min(1000L, now - inlineExoLastPreviewTickMs));
                    }
                    inlineExoLastPreviewTickMs = now;
                } else {
                    inlineExoLastPreviewTickMs = 0L;
                }

                long usedMs = Math.max(curPos, inlineExoAccumulatedPreviewMs);
                long remainingSec = Math.max(0L, (long) Math.ceil((limitMs - usedMs) / 1000.0));
                if (inlineExoPreviewBadge != null) {
                    inlineExoPreviewBadge.setVisibility(View.VISIBLE);
                    inlineExoPreviewBadge.setText("Free Preview: " + remainingSec + "s");
                }

                if (usedMs >= limitMs) {
                    triggerInlineExoPreviewExpired();
                    return;
                }
            }

            inlineExoHandler.postDelayed(this, 250);
        }
    };

    private void syncWebViewExoBoundsAsync() {
        runOnUiThread(() -> {
            try {
                if (getBridge() != null && getBridge().getWebView() != null) {
                    getBridge().getWebView().evaluateJavascript("window.syncInlineExoBounds && window.syncInlineExoBounds()", null);
                }
            } catch (Exception ignored) {}
        });
    }

    @SuppressWarnings("UnsafeOptInUsageError")
    private void ensureInlineExoViewsAttached() {
        if (inlineExoContainer != null) return;
        ViewGroup rootView = (ViewGroup) getWindow().getDecorView().findViewById(android.R.id.content);
        if (rootView == null) return;

        View inflated = LayoutInflater.from(this).inflate(R.layout.view_inline_exo_player, rootView, false);
        inlineExoContainer = inflated.findViewById(R.id.inlineExoRoot);
        inlineExoPlayerView = inflated.findViewById(R.id.inlineExoPlayerView);
        inlineExoTopBar = inflated.findViewById(R.id.inlineExoTopBar);
        inlineExoExitFsBtn = inflated.findViewById(R.id.inlineExoExitFsBtn);
        inlineExoTitleText = inflated.findViewById(R.id.inlineExoTitleText);
        inlineExoPreviewBadge = inflated.findViewById(R.id.inlineExoPreviewBadge);
        inlineExoSpeedBtn = inflated.findViewById(R.id.inlineExoSpeedBtn);
        inlineExoResizeBtn = inflated.findViewById(R.id.inlineExoResizeBtn);
        inlineExoRotateBtn = inflated.findViewById(R.id.inlineExoRotateBtn);
        inlineExoLoadingOverlay = inflated.findViewById(R.id.inlineExoLoadingOverlay);
        inlineExoLoadingText = inflated.findViewById(R.id.inlineExoLoadingText);

        ViewCompat.setElevation(inlineExoContainer, 20f);
        FrameLayout.LayoutParams initLp = new FrameLayout.LayoutParams(1, 1);
        initLp.gravity = Gravity.TOP | Gravity.START;
        inlineExoContainer.setLayoutParams(initLp);
        rootView.addView(inlineExoContainer);

        if (adMobBannerContainer != null) {
            adMobBannerContainer.bringToFront();
        }

        if (inlineExoExitFsBtn != null) {
            inlineExoExitFsBtn.setOnClickListener(v -> setInlineExoFullscreen(false));
        }
        if (inlineExoSpeedBtn != null) {
            inlineExoSpeedBtn.setOnClickListener(v -> cycleInlineExoSpeed());
        }
        if (inlineExoResizeBtn != null) {
            inlineExoResizeBtn.setOnClickListener(v -> cycleInlineExoResize());
        }
        if (inlineExoRotateBtn != null) {
            inlineExoRotateBtn.setOnClickListener(v -> toggleInlineExoOrientation());
        }

        if (inlineExoPlayerView != null) {
            inlineExoPlayerView.setFullscreenButtonClickListener(isFullScreen -> toggleInlineExoFullscreen());
            inlineExoPlayerView.setControllerVisibilityListener((PlayerView.ControllerVisibilityListener) visibility -> {
                isInlineExoControllerVisible = (visibility == View.VISIBLE);
                if (inlineExoTopBar != null) {
                    boolean showTop = (isInlineExoFullscreen && isInlineExoControllerVisible) || (!inlineExoIsSubscribed);
                    inlineExoTopBar.setVisibility(showTop ? View.VISIBLE : View.GONE);
                }
                if (isInlineExoFullscreen && visibility != View.VISIBLE) {
                    hideSystemBarsLightweight();
                }
            });
        }

        if (!inlineExoScrollListenerAttached && getBridge() != null && getBridge().getWebView() != null) {
            inlineExoScrollListenerAttached = true;
            WebView wv = getBridge().getWebView();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                wv.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> applyInlineExoPortraitPosition());
            }
        }
    }

    @SuppressWarnings("UnsafeOptInUsageError")
    private void ensureInlineExoPlayerCreated() {
        if (inlineExoPlayer != null) return;

        DefaultHttpDataSource.Factory httpDataSourceFactory = new DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(20000)
                .setReadTimeoutMs(30000)
                .setUserAgent("Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");

        DefaultExtractorsFactory extractorsFactory = new DefaultExtractorsFactory()
                .setConstantBitrateSeekingEnabled(true)
                .setConstantBitrateSeekingAlwaysEnabled(true);

        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(15000, 60000, 1500, 3000)
                .build();

        DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(this, extractorsFactory)
                .setDataSourceFactory(httpDataSourceFactory)
                .setLoadErrorHandlingPolicy(new DefaultLoadErrorHandlingPolicy(6));

        inlineExoPlayer = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(mediaSourceFactory)
                .setLoadControl(loadControl)
                .build();

        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build();
        inlineExoPlayer.setAudioAttributes(audioAttributes, true);

        inlineExoPlayer.addListener(new Player.Listener() {
            @Override
            public void onPositionDiscontinuity(@NonNull Player.PositionInfo oldPosition, @NonNull Player.PositionInfo newPosition, int reason) {
                if (!isInlineExoActive) return;
                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    long targetMs = Math.max(0L, newPosition.positionMs);
                    inlineExoPendingSeekPosMs = (targetMs > 15000L) ? targetMs : -1L;
                    inlineExoLastValidPosMs = targetMs;
                }
            }

            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (!isInlineExoActive) return;
                if (inlineExoLoadingOverlay != null) {
                    if (playbackState == Player.STATE_BUFFERING) {
                        if (inlineExoLoadingText != null) inlineExoLoadingText.setText("Buffering...");
                        inlineExoLoadingOverlay.setVisibility(View.VISIBLE);
                    } else if (playbackState == Player.STATE_READY || playbackState == Player.STATE_ENDED) {
                        inlineExoLoadingOverlay.setVisibility(View.GONE);
                        if (playbackState == Player.STATE_READY) {
                            inlineExoErrorRetryCount = 0;
                            if (inlineExoPlayer != null) {
                                long curPos = Math.max(0L, inlineExoPlayer.getCurrentPosition());
                                if (inlineExoPendingSeekPosMs > 15000L && curPos < 5000L) {
                                    long recoverTarget = inlineExoPendingSeekPosMs;
                                    inlineExoPendingSeekPosMs = -1L;
                                    inlineExoPlayer.seekTo(recoverTarget);
                                    return;
                                }
                                if (inlineExoPendingSeekPosMs >= 0L && (Math.abs(curPos - inlineExoPendingSeekPosMs) <= 15000L || curPos >= inlineExoPendingSeekPosMs)) {
                                    inlineExoPendingSeekPosMs = -1L;
                                }
                                if (curPos > 0L) {
                                    inlineExoLastValidPosMs = curPos;
                                }
                            }
                        }
                    }
                }

                if (playbackState == Player.STATE_ENDED && !inlineExoPreviewTriggeredExit) {
                    if (getBridge() != null && getBridge().getWebView() != null) {
                        getBridge().getWebView().evaluateJavascript("window.onNativeExoVideoEnded && window.onNativeExoVideoEnded()", null);
                    }
                }
            }

            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                if (!isInlineExoActive || inlineExoPreviewTriggeredExit || isFinishing()) return;
                long curPos = (inlineExoPlayer != null) ? Math.max(0L, inlineExoPlayer.getCurrentPosition()) : 0L;
                long seekAnchor = Math.max(0L, inlineExoPendingSeekPosMs);
                long resumePos = Math.max(inlineExoLastValidPosMs, Math.max(curPos, seekAnchor));

                if (inlineExoErrorRetryCount < 4 && inlineExoCurrentUrl != null && !inlineExoCurrentUrl.isEmpty()) {
                    inlineExoErrorRetryCount++;
                    if (inlineExoLoadingOverlay != null) {
                        if (inlineExoLoadingText != null) inlineExoLoadingText.setText("Reconnecting stream...");
                        inlineExoLoadingOverlay.setVisibility(View.VISIBLE);
                    }
                    String refreshedUrl = buildExoRefreshedStreamUrl(inlineExoCurrentUrl);
                    inlineExoHandler.postDelayed(() -> {
                        if (inlineExoPlayer != null && isInlineExoActive && !isFinishing()) {
                            MediaItem retryItem = MediaItem.fromUri(Uri.parse(refreshedUrl));
                            inlineExoPlayer.setMediaItem(retryItem, resumePos);
                            inlineExoPlayer.prepare();
                            inlineExoPlayer.play();
                        }
                    }, 900);
                } else {
                    if (inlineExoLoadingOverlay != null) inlineExoLoadingOverlay.setVisibility(View.GONE);
                    setInlineExoFullscreen(false);
                    stopInlineExoPlayer();
                    if (getBridge() != null && getBridge().getWebView() != null) {
                        getBridge().getWebView().evaluateJavascript("window.onNativeExoError && window.onNativeExoError()", null);
                    }
                }
            }
        });

        if (inlineExoPlayerView != null) {
            inlineExoPlayerView.setPlayer(inlineExoPlayer);
        }
    }

    private String buildExoRefreshedStreamUrl(String url) {
        if (url == null) return "";
        String lower = url.toLowerCase();
        boolean isSigned = lower.contains("x-amz-") || lower.contains("signature=") || lower.contains("sig=") || lower.contains("token=");
        boolean isGitHub = lower.contains("github.com") && lower.contains("/releases/download/");
        if (!isGitHub || isSigned) {
            return url;
        }
        String clean = url.replaceAll("([?&])_ghr=\\d+(&|$)", "$1").replaceAll("[?&]$", "");
        String sep = clean.contains("?") ? "&" : "?";
        return clean + sep + "_ghr=" + System.currentTimeMillis();
    }

    private void hideSystemBarsLightweight() {
        try {
            Window window = getWindow();
            if (window == null) return;
            View decorView = window.getDecorView();
            WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, decorView);
            if (controller != null) {
                controller.hide(WindowInsetsCompat.Type.systemBars());
                controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
            if (decorView != null) {
                decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                );
            }
        } catch (Exception ignored) {}
    }

    public void setInlineExoSubscribed(final boolean subscribed) {
        runOnUiThread(() -> {
            try {
                if (inlineExoIsSubscribed == subscribed) return;
                inlineExoIsSubscribed = subscribed;
                inlineExoLastPreviewTickMs = 0L;
                updateInlineExoOverlayControls();
            } catch (Exception ignored) {}
        });
    }

    public void startInlineExoPlayer(final String url, final String title, final boolean isSubscribed, final int previewLimitSeconds,
                                     final float leftCss, final float topCss, final float widthCss, final float heightCss, final float headerBottomCss) {
        if (url == null || url.trim().isEmpty()) return;
        final String trimmedUrl = url.trim();
        runOnUiThread(() -> {
            try {
                ensureInlineExoViewsAttached();
                ensureInlineExoPlayerCreated();

                int currentState = (inlineExoPlayer != null) ? inlineExoPlayer.getPlaybackState() : Player.STATE_IDLE;
                boolean sameActiveStream = isInlineExoActive
                        && inlineExoPlayer != null
                        && trimmedUrl.equals(inlineExoCurrentUrl)
                        && currentState != Player.STATE_IDLE
                        && currentState != Player.STATE_ENDED
                        && inlineExoPlayer.getPlayerError() == null;

                inlineExoCurrentUrl = trimmedUrl;
                inlineExoCurrentTitle = (title != null && !title.trim().isEmpty()) ? title.trim() : "Movie Player";
                inlineExoIsSubscribed = isSubscribed;
                inlineExoPreviewLimitSec = previewLimitSeconds > 0 ? previewLimitSeconds : 20;
                isInlineExoHiddenForOverlay = false;

                if (inlineExoTitleText != null) inlineExoTitleText.setText(inlineExoCurrentTitle);
                updateInlineExoOverlayControls();

                storeInlineExoBounds(leftCss, topCss, widthCss, heightCss, headerBottomCss);
                if (!isInlineExoFullscreen) {
                    applyInlineExoPortraitPosition();
                } else {
                    applyInlineExoFullscreenLayout();
                }

                if (sameActiveStream) {
                    inlineExoHandler.removeCallbacks(inlineExoPreviewRunnable);
                    inlineExoHandler.postDelayed(inlineExoPreviewRunnable, 250);
                    return;
                }

                inlineExoLastValidPosMs = 0L;
                inlineExoPendingSeekPosMs = -1L;
                inlineExoErrorRetryCount = 0;
                inlineExoAccumulatedPreviewMs = 0L;
                inlineExoLastPreviewTickMs = 0L;
                inlineExoPreviewTriggeredExit = false;
                isInlineExoActive = true;

                if (inlineExoLoadingOverlay != null) {
                    if (inlineExoLoadingText != null) inlineExoLoadingText.setText("Loading Stream...");
                    inlineExoLoadingOverlay.setVisibility(View.VISIBLE);
                }

                MediaItem mediaItem = MediaItem.fromUri(Uri.parse(inlineExoCurrentUrl));
                inlineExoPlayer.setMediaItem(mediaItem, 0L);
                inlineExoPlayer.setPlayWhenReady(true);
                inlineExoPlayer.prepare();

                inlineExoHandler.removeCallbacks(inlineExoPreviewRunnable);
                inlineExoHandler.postDelayed(inlineExoPreviewRunnable, 250);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void updateInlineExoBounds(final float leftCss, final float topCss, final float widthCss, final float heightCss, final float headerBottomCss) {
        runOnUiThread(() -> {
            if (!isInlineExoActive || isInlineExoFullscreen) return;
            if (getResources().getConfiguration().orientation != Configuration.ORIENTATION_PORTRAIT) return;
            storeInlineExoBounds(leftCss, topCss, widthCss, heightCss, headerBottomCss);
            applyInlineExoPortraitPosition();
        });
    }

    private void storeInlineExoBounds(float leftCss, float topCss, float widthCss, float heightCss, float headerBottomCss) {
        if (widthCss <= 1f || heightCss <= 1f) return;
        if (isInlineExoFullscreen) return;
        if (getResources().getConfiguration().orientation != Configuration.ORIENTATION_PORTRAIT) return;

        WebView wv = (getBridge() != null) ? getBridge().getWebView() : null;
        float density = (wv != null && wv.getScale() > 0f) ? wv.getScale() : getResources().getDisplayMetrics().density;
        int scrollY = (wv != null) ? wv.getScrollY() : 0;

        int leftPx = Math.round(leftCss * density);
        int docTopPx = Math.round(topCss * density) + scrollY;
        int widthPx = Math.max(1, Math.round(widthCss * density));
        int heightPx = Math.max(1, Math.round(heightCss * density));
        int headerBottomPx = Math.max(0, Math.round(headerBottomCss * density));

        DisplayMetrics dm = getResources().getDisplayMetrics();
        int portraitScreenH = Math.max(dm.widthPixels, dm.heightPixels);
        if (heightPx > Math.round(portraitScreenH * 0.72f)) {
            return;
        }

        inlineExoLeftPx = leftPx;
        inlineExoDocTopPx = docTopPx;
        inlineExoWidthPx = widthPx;
        inlineExoHeightPx = heightPx;
        inlineExoHeaderBottomPx = headerBottomPx;

        inlineExoPortraitLeftPx = leftPx;
        inlineExoPortraitDocTopPx = docTopPx;
        inlineExoPortraitWidthPx = widthPx;
        inlineExoPortraitHeightPx = heightPx;
        inlineExoPortraitHeaderBottomPx = headerBottomPx;
    }

    private void applyInlineExoPortraitPosition() {
        if (inlineExoContainer == null || !isInlineExoActive || isInlineExoFullscreen) return;
        if (isInlineExoHiddenForOverlay) {
            inlineExoContainer.setVisibility(View.GONE);
            return;
        }

        int targetW = (inlineExoPortraitWidthPx > 1) ? inlineExoPortraitWidthPx : inlineExoWidthPx;
        int targetH = (inlineExoPortraitHeightPx > 1) ? inlineExoPortraitHeightPx : inlineExoHeightPx;
        int targetLeft = (inlineExoPortraitWidthPx > 1) ? inlineExoPortraitLeftPx : inlineExoLeftPx;
        int targetDocTop = (inlineExoPortraitWidthPx > 1) ? inlineExoPortraitDocTopPx : inlineExoDocTopPx;
        int targetHeaderBottom = (inlineExoPortraitWidthPx > 1) ? inlineExoPortraitHeaderBottomPx : inlineExoHeaderBottomPx;

        if (targetW <= 1 || targetH <= 1) {
            DisplayMetrics dm = getResources().getDisplayMetrics();
            targetW = Math.min(dm.widthPixels, dm.heightPixels);
            targetH = Math.max(1, Math.round(targetW * 9f / 16f));
            targetLeft = 0;
        }

        WebView wv = (getBridge() != null) ? getBridge().getWebView() : null;
        int currentTop = targetDocTop - (wv != null ? wv.getScrollY() : 0);

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) inlineExoContainer.getLayoutParams();
        if (lp.width != targetW || lp.height != targetH || lp.leftMargin != targetLeft || lp.topMargin != 0 || lp.gravity != (Gravity.TOP | Gravity.START)) {
            lp.width = targetW;
            lp.height = targetH;
            lp.leftMargin = targetLeft;
            lp.topMargin = 0;
            lp.gravity = Gravity.TOP | Gravity.START;
            inlineExoContainer.setLayoutParams(lp);
            inlineExoContainer.requestLayout();
        }
        ViewCompat.setElevation(inlineExoContainer, 20f);
        inlineExoContainer.setTranslationY(currentTop);

        if (currentTop < targetHeaderBottom) {
            int clipTop = Math.min(targetH, Math.max(0, targetHeaderBottom - currentTop));
            if (clipTop >= targetH) {
                inlineExoContainer.setVisibility(View.INVISIBLE);
            } else {
                inlineExoContainer.setVisibility(View.VISIBLE);
                inlineExoContainer.setClipBounds(new Rect(0, clipTop, targetW, targetH));
            }
        } else {
            inlineExoContainer.setVisibility(View.VISIBLE);
            inlineExoContainer.setClipBounds(null);
        }
        inlineExoContainer.invalidate();
    }

    private void applyInlineExoFullscreenLayout() {
        if (inlineExoContainer == null) return;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) inlineExoContainer.getLayoutParams();
        lp.width = FrameLayout.LayoutParams.MATCH_PARENT;
        lp.height = FrameLayout.LayoutParams.MATCH_PARENT;
        lp.leftMargin = 0;
        lp.topMargin = 0;
        lp.gravity = Gravity.TOP | Gravity.START;
        inlineExoContainer.setLayoutParams(lp);
        inlineExoContainer.requestLayout();
        inlineExoContainer.setTranslationY(0f);
        inlineExoContainer.setClipBounds(null);
        ViewCompat.setElevation(inlineExoContainer, 10000f);
        inlineExoContainer.bringToFront();
        inlineExoContainer.setVisibility(isInlineExoHiddenForOverlay ? View.GONE : View.VISIBLE);
        inlineExoContainer.invalidate();
    }

    private void updateInlineExoOverlayControls() {
        int fsOnlyVis = isInlineExoFullscreen ? View.VISIBLE : View.GONE;
        if (inlineExoExitFsBtn != null) inlineExoExitFsBtn.setVisibility(fsOnlyVis);
        if (inlineExoTitleText != null) inlineExoTitleText.setVisibility(fsOnlyVis);
        if (inlineExoSpeedBtn != null) inlineExoSpeedBtn.setVisibility(fsOnlyVis);
        if (inlineExoResizeBtn != null) inlineExoResizeBtn.setVisibility(fsOnlyVis);
        if (inlineExoRotateBtn != null) inlineExoRotateBtn.setVisibility(fsOnlyVis);
        if (inlineExoPreviewBadge != null) {
            inlineExoPreviewBadge.setVisibility(!inlineExoIsSubscribed ? View.VISIBLE : View.GONE);
        }
        if (inlineExoTopBar != null) {
            boolean controllerVisible = isInlineExoControllerVisible
                    || (inlineExoPlayerView != null && inlineExoPlayerView.isControllerFullyVisible());
            boolean showBar = (isInlineExoFullscreen && controllerVisible) || !inlineExoIsSubscribed;
            inlineExoTopBar.setVisibility(showBar ? View.VISIBLE : View.GONE);
        }
    }

    public void setInlineExoFullscreen(final boolean fullscreen) {
        runOnUiThread(() -> {
            if (!isInlineExoActive || inlineExoContainer == null) return;
            if (isInlineExoFullscreen == fullscreen) return;
            isInlineExoFullscreen = fullscreen;
            if (inlineExoBackCallback != null) {
                inlineExoBackCallback.setEnabled(fullscreen);
            }
            updateInlineExoOverlayControls();

            if (fullscreen) {
                isInlineExoLandscapeLocked = true;
                if (adMobBannerContainer != null) {
                    wasAdMobBannerVisibleBeforeExoFs = (adMobBannerContainer.getVisibility() == View.VISIBLE);
                    adMobBannerContainer.setVisibility(View.GONE);
                }
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                hideSystemBars();
                applyInlineExoFullscreenLayout();
            } else {
                isInlineExoLandscapeLocked = true;
                if (inlineExoPortraitWidthPx > 1 && inlineExoPortraitHeightPx > 1) {
                    inlineExoLeftPx = inlineExoPortraitLeftPx;
                    inlineExoDocTopPx = inlineExoPortraitDocTopPx;
                    inlineExoWidthPx = inlineExoPortraitWidthPx;
                    inlineExoHeightPx = inlineExoPortraitHeightPx;
                    inlineExoHeaderBottomPx = inlineExoPortraitHeaderBottomPx;
                }
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                inlineExoHandler.postDelayed(() -> {
                    if (!isInlineExoFullscreen) {
                        try {
                            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                        } catch (Exception ignored) {}
                    }
                }, 700);
                if (adMobBannerContainer != null && wasAdMobBannerVisibleBeforeExoFs && lastAdMobBannerUnit != null) {
                    adMobBannerContainer.setVisibility(View.VISIBLE);
                    adMobBannerContainer.bringToFront();
                }
                applyInlineExoPortraitPosition();
                inlineExoHandler.postDelayed(this::syncWebViewExoBoundsAsync, 80);
                inlineExoHandler.postDelayed(this::syncWebViewExoBoundsAsync, 220);
                inlineExoHandler.postDelayed(this::syncWebViewExoBoundsAsync, 450);
                inlineExoHandler.postDelayed(this::syncWebViewExoBoundsAsync, 750);
            }

            if (getBridge() != null && getBridge().getWebView() != null) {
                getBridge().getWebView().evaluateJavascript(
                        "window.onNativeExoFullscreenChanged && window.onNativeExoFullscreenChanged(" + fullscreen + ")",
                        null
                );
            }
        });
    }

    private void toggleInlineExoFullscreen() {
        setInlineExoFullscreen(!isInlineExoFullscreen);
    }

    private void cycleInlineExoResize() {
        inlineExoResizeIndex = (inlineExoResizeIndex + 1) % inlineExoResizeModes.length;
        if (inlineExoPlayerView != null) {
            inlineExoPlayerView.setResizeMode(inlineExoResizeModes[inlineExoResizeIndex]);
        }
        if (inlineExoResizeBtn != null) {
            inlineExoResizeBtn.setText(inlineExoResizeLabels[inlineExoResizeIndex]);
        }
        Toast.makeText(this, "Screen: " + inlineExoResizeLabels[inlineExoResizeIndex], Toast.LENGTH_SHORT).show();
    }

    private void cycleInlineExoSpeed() {
        inlineExoSpeedIndex = (inlineExoSpeedIndex + 1) % inlineExoSpeedValues.length;
        float speed = inlineExoSpeedValues[inlineExoSpeedIndex];
        if (inlineExoPlayer != null) {
            inlineExoPlayer.setPlaybackParameters(new PlaybackParameters(speed));
        }
        if (inlineExoSpeedBtn != null) {
            inlineExoSpeedBtn.setText(inlineExoSpeedLabels[inlineExoSpeedIndex]);
        }
    }

    private void toggleInlineExoOrientation() {
        isInlineExoLandscapeLocked = !isInlineExoLandscapeLocked;
        setRequestedOrientation(isInlineExoLandscapeLocked
                ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    public void setInlineExoVisible(final boolean visible) {
        runOnUiThread(() -> {
            isInlineExoHiddenForOverlay = !visible;
            if (!isInlineExoActive || inlineExoContainer == null) return;
            if (!visible) {
                inlineExoContainer.setVisibility(View.GONE);
                if (inlineExoPlayer != null && inlineExoPlayer.isPlaying()) {
                    try { inlineExoPlayer.pause(); } catch (Exception ignored) {}
                }
            } else {
                if (isInlineExoFullscreen) {
                    applyInlineExoFullscreenLayout();
                } else {
                    applyInlineExoPortraitPosition();
                    syncWebViewExoBoundsAsync();
                }
            }
        });
    }

    private void triggerInlineExoPreviewExpired() {
        if (inlineExoPreviewTriggeredExit) return;
        inlineExoPreviewTriggeredExit = true;
        inlineExoHandler.removeCallbacks(inlineExoPreviewRunnable);
        if (isInlineExoFullscreen) {
            setInlineExoFullscreen(false);
        }
        stopInlineExoPlayer();
        if (getBridge() != null && getBridge().getWebView() != null) {
            getBridge().getWebView().evaluateJavascript(
                    "window.onNativeExoPreviewExpired && window.onNativeExoPreviewExpired()",
                    null
            );
        }
    }

    public void stopInlineExoPlayer() {
        runOnUiThread(() -> {
            inlineExoHandler.removeCallbacks(inlineExoPreviewRunnable);
            if (isInlineExoFullscreen) {
                isInlineExoFullscreen = false;
                if (inlineExoBackCallback != null) inlineExoBackCallback.setEnabled(false);
                try {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                    inlineExoHandler.postDelayed(() -> {
                        if (!isInlineExoFullscreen) {
                            try { setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED); } catch (Exception ignored) {}
                        }
                    }, 450);
                } catch (Exception ignored) {}
                if (adMobBannerContainer != null && wasAdMobBannerVisibleBeforeExoFs && lastAdMobBannerUnit != null) {
                    adMobBannerContainer.setVisibility(View.VISIBLE);
                    adMobBannerContainer.bringToFront();
                }
            }
            isInlineExoActive = false;
            isInlineExoHiddenForOverlay = false;
            if (inlineExoPlayer != null) {
                try {
                    inlineExoPlayer.pause();
                    inlineExoPlayer.stop();
                    inlineExoPlayer.clearMediaItems();
                } catch (Exception ignored) {}
            }
            if (inlineExoContainer != null) {
                inlineExoContainer.setVisibility(View.GONE);
                inlineExoContainer.setClipBounds(null);
            }
        });
    }

    private void releaseInlineExoPlayer() {
        inlineExoHandler.removeCallbacks(inlineExoPreviewRunnable);
        isInlineExoActive = false;
        if (inlineExoPlayer != null) {
            try {
                inlineExoPlayer.release();
            } catch (Exception ignored) {}
            inlineExoPlayer = null;
        }
    }
}
