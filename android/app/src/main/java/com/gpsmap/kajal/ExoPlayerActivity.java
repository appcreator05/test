package com.gpsmap.kajal;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
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

import java.lang.ref.WeakReference;

@SuppressWarnings("UnsafeOptInUsageError")
public class ExoPlayerActivity extends AppCompatActivity {

    public static final String EXTRA_URL = "extra_url";
    public static final String EXTRA_TITLE = "extra_title";
    public static final String EXTRA_IS_SUBSCRIBED = "extra_is_subscribed";
    public static final String EXTRA_PREVIEW_LIMIT = "extra_preview_limit";

    public static final int RESULT_PREVIEW_EXPIRED = 101;
    public static final int RESULT_VIDEO_ENDED = 102;

    private static WeakReference<ExoPlayerActivity> activeInstanceRef = null;

    private PlayerView playerView;
    private ExoPlayer exoPlayer;
    private LinearLayout topBar;
    private LinearLayout loadingOverlay;
    private TextView loadingText;
    private TextView titleText;
    private TextView previewBadge;
    private TextView speedBtn;
    private TextView resizeBtn;

    private String originalUrl = "";
    private String movieTitle = "Movie Player";
    private boolean isSubscribed = true;
    private int previewLimitSeconds = 20;

    private long lastValidPositionMs = 0L;
    private long pendingSeekPosMs = -1L;
    private int errorRetryCount = 0;
    private boolean isLandscapeLocked = true;
    private int currentResizeIndex = 0;
    private final int[] resizeModes = new int[]{
            AspectRatioFrameLayout.RESIZE_MODE_FIT,
            AspectRatioFrameLayout.RESIZE_MODE_FILL,
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    };
    private final String[] resizeLabels = new String[]{"FIT", "FILL", "ZOOM"};

    private int currentSpeedIndex = 1;
    private final float[] speedValues = new float[]{0.75f, 1.0f, 1.25f, 1.5f, 2.0f};
    private final String[] speedLabels = new String[]{"0.75x", "1x", "1.25x", "1.5x", "2x"};

    private final Handler previewHandler = new Handler(Looper.getMainLooper());
    private long accumulatedPreviewMs = 0L;
    private long lastPreviewTickMs = 0L;
    private boolean previewTriggeredExit = false;

    private final Runnable previewMonitorRunnable = new Runnable() {
        @Override
        public void run() {
            if (isFinishing() || isDestroyed() || exoPlayer == null) return;

            long now = System.currentTimeMillis();
            long curPos = Math.max(0L, exoPlayer.getCurrentPosition());
            if (exoPlayer.getPlaybackState() == Player.STATE_READY) {
                if (pendingSeekPosMs >= 0L) {
                    if (pendingSeekPosMs > 15000L && curPos < 5000L) {
                        long recoverTarget = pendingSeekPosMs;
                        pendingSeekPosMs = -1L;
                        exoPlayer.seekTo(recoverTarget);
                        previewHandler.postDelayed(this, 250);
                        return;
                    }
                    if (Math.abs(curPos - pendingSeekPosMs) <= 15000L || curPos >= pendingSeekPosMs) {
                        pendingSeekPosMs = -1L;
                        if (curPos > 0L) {
                            lastValidPositionMs = curPos;
                        }
                    }
                } else if (curPos > 0L) {
                    lastValidPositionMs = curPos;
                }
            }

            if (!isSubscribed && !previewTriggeredExit) {
                long limitMs = Math.max(1, previewLimitSeconds) * 1000L;

                // Block seeking past free preview limit
                if (curPos >= limitMs) {
                    triggerPreviewExpired();
                    return;
                }

                if (exoPlayer.isPlaying()) {
                    if (lastPreviewTickMs > 0L) {
                        accumulatedPreviewMs += Math.max(0L, Math.min(1000L, now - lastPreviewTickMs));
                    }
                    lastPreviewTickMs = now;
                } else {
                    lastPreviewTickMs = 0L;
                }

                long usedMs = Math.max(curPos, accumulatedPreviewMs);
                long remainingSec = Math.max(0L, (long) Math.ceil((limitMs - usedMs) / 1000.0));
                if (previewBadge != null) {
                    previewBadge.setText("Free Preview: " + remainingSec + "s");
                }

                if (usedMs >= limitMs) {
                    triggerPreviewExpired();
                    return;
                }
            }

            previewHandler.postDelayed(this, 250);
        }
    };

    public static void closeActiveInstance() {
        if (activeInstanceRef != null) {
            final ExoPlayerActivity act = activeInstanceRef.get();
            if (act != null && !act.isFinishing()) {
                act.runOnUiThread(() -> {
                    act.setResult(RESULT_CANCELED);
                    act.finish();
                });
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activeInstanceRef = new WeakReference<>(this);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        hideSystemBars();

        setContentView(R.layout.activity_exo_player);

        playerView = findViewById(R.id.exoPlayerView);
        topBar = findViewById(R.id.exoTopBar);
        loadingOverlay = findViewById(R.id.exoLoadingOverlay);
        loadingText = findViewById(R.id.exoLoadingText);
        titleText = findViewById(R.id.exoTitleText);
        previewBadge = findViewById(R.id.exoPreviewBadge);
        speedBtn = findViewById(R.id.exoSpeedBtn);
        resizeBtn = findViewById(R.id.exoResizeBtn);
        ImageButton backBtn = findViewById(R.id.exoBackBtn);
        ImageButton rotateBtn = findViewById(R.id.exoRotateBtn);

        readIntentExtras(getIntent());

        if (titleText != null) {
            titleText.setText(movieTitle);
        }
        if (previewBadge != null) {
            if (!isSubscribed) {
                previewBadge.setVisibility(View.VISIBLE);
                previewBadge.setText("Free Preview: " + previewLimitSeconds + "s");
            } else {
                previewBadge.setVisibility(View.GONE);
            }
        }

        if (backBtn != null) {
            backBtn.setOnClickListener(v -> finish());
        }
        if (rotateBtn != null) {
            rotateBtn.setOnClickListener(v -> toggleOrientation());
        }
        if (resizeBtn != null) {
            resizeBtn.setOnClickListener(v -> cycleResizeMode());
        }
        if (speedBtn != null) {
            speedBtn.setOnClickListener(v -> cyclePlaybackSpeed());
        }

        if (playerView != null) {
            playerView.setControllerVisibilityListener((PlayerView.ControllerVisibilityListener) visibility -> {
                if (topBar != null) {
                    topBar.setVisibility(visibility);
                }
                if (visibility != View.VISIBLE) {
                    hideSystemBars();
                }
            });
        }

        if (originalUrl == null || originalUrl.trim().isEmpty()) {
            Toast.makeText(this, "Invalid video link", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initExoPlayer(originalUrl, 0L);
        previewHandler.postDelayed(previewMonitorRunnable, 250);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        readIntentExtras(intent);
        if (titleText != null) titleText.setText(movieTitle);
        lastValidPositionMs = 0L;
        pendingSeekPosMs = -1L;
        errorRetryCount = 0;
        accumulatedPreviewMs = 0L;
        lastPreviewTickMs = 0L;
        previewTriggeredExit = false;
        if (exoPlayer != null && originalUrl != null && !originalUrl.isEmpty()) {
            MediaItem item = MediaItem.fromUri(Uri.parse(originalUrl));
            exoPlayer.setMediaItem(item, 0L);
            exoPlayer.prepare();
            exoPlayer.play();
        }
    }

    private void readIntentExtras(Intent intent) {
        if (intent == null) return;
        String u = intent.getStringExtra(EXTRA_URL);
        String t = intent.getStringExtra(EXTRA_TITLE);
        originalUrl = u != null ? u.trim() : "";
        movieTitle = (t != null && !t.trim().isEmpty()) ? t.trim() : "Movie Player";
        isSubscribed = intent.getBooleanExtra(EXTRA_IS_SUBSCRIBED, true);
        previewLimitSeconds = intent.getIntExtra(EXTRA_PREVIEW_LIMIT, 20);
    }

    private void initExoPlayer(String streamUrl, long startPositionMs) {
        releasePlayer();

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

        exoPlayer = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(mediaSourceFactory)
                .setLoadControl(loadControl)
                .build();

        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build();
        exoPlayer.setAudioAttributes(audioAttributes, true);

        if (playerView != null) {
            playerView.setPlayer(exoPlayer);
        }

        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onPositionDiscontinuity(@NonNull Player.PositionInfo oldPosition, @NonNull Player.PositionInfo newPosition, int reason) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    long targetMs = Math.max(0L, newPosition.positionMs);
                    pendingSeekPosMs = (targetMs > 15000L) ? targetMs : -1L;
                    lastValidPositionMs = targetMs;
                }
            }

            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (loadingOverlay != null) {
                    if (playbackState == Player.STATE_BUFFERING) {
                        if (loadingText != null) loadingText.setText("Buffering...");
                        loadingOverlay.setVisibility(View.VISIBLE);
                    } else if (playbackState == Player.STATE_READY || playbackState == Player.STATE_ENDED) {
                        loadingOverlay.setVisibility(View.GONE);
                        if (playbackState == Player.STATE_READY) {
                            errorRetryCount = 0;
                            if (exoPlayer != null) {
                                long curPos = Math.max(0L, exoPlayer.getCurrentPosition());
                                if (pendingSeekPosMs > 15000L && curPos < 5000L) {
                                    long recoverTarget = pendingSeekPosMs;
                                    pendingSeekPosMs = -1L;
                                    exoPlayer.seekTo(recoverTarget);
                                    return;
                                }
                                if (pendingSeekPosMs >= 0L && (Math.abs(curPos - pendingSeekPosMs) <= 15000L || curPos >= pendingSeekPosMs)) {
                                    pendingSeekPosMs = -1L;
                                }
                                if (curPos > 0L) {
                                    lastValidPositionMs = curPos;
                                }
                            }
                        }
                    }
                }

                if (playbackState == Player.STATE_ENDED && !previewTriggeredExit) {
                    setResult(RESULT_VIDEO_ENDED);
                    finish();
                }
            }

            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                if (previewTriggeredExit || isFinishing()) return;
                long curPos = (exoPlayer != null) ? Math.max(0L, exoPlayer.getCurrentPosition()) : 0L;
                long seekAnchor = Math.max(0L, pendingSeekPosMs);
                long resumePos = Math.max(lastValidPositionMs, Math.max(curPos, seekAnchor));

                if (errorRetryCount < 4 && originalUrl != null && !originalUrl.isEmpty()) {
                    errorRetryCount++;
                    if (loadingOverlay != null) {
                        if (loadingText != null) loadingText.setText("Reconnecting stream...");
                        loadingOverlay.setVisibility(View.VISIBLE);
                    }
                    String refreshedUrl = buildRefreshedStreamUrl(originalUrl);
                    previewHandler.postDelayed(() -> {
                        if (exoPlayer != null && !isFinishing()) {
                            MediaItem retryItem = MediaItem.fromUri(Uri.parse(refreshedUrl));
                            exoPlayer.setMediaItem(retryItem, resumePos);
                            exoPlayer.prepare();
                            exoPlayer.play();
                        }
                    }, 900);
                } else {
                    if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
                    Toast.makeText(ExoPlayerActivity.this, "Stream connection error. Please retry or switch server.", Toast.LENGTH_LONG).show();
                }
            }
        });

        MediaItem mediaItem = MediaItem.fromUri(Uri.parse(streamUrl));
        exoPlayer.setMediaItem(mediaItem, Math.max(0L, startPositionMs));
        exoPlayer.setPlayWhenReady(true);
        exoPlayer.prepare();
    }

    private String buildRefreshedStreamUrl(String url) {
        if (url == null) return "";
        String lower = url.toLowerCase();
        boolean isSigned = lower.contains("x-amz-") || lower.contains("signature=") || lower.contains("sig=") || lower.contains("token=");
        boolean isGitHub = lower.contains("github.com") && lower.contains("/releases/download/");
        if (!isGitHub || isSigned) {
            return url;
        }
        String clean = url.replaceAll("([?&])_ghr=\\d+(&|$)", "$1")
                .replaceAll("[?&]$", "");
        String sep = clean.contains("?") ? "&" : "?";
        return clean + sep + "_ghr=" + System.currentTimeMillis();
    }

    private void triggerPreviewExpired() {
        if (previewTriggeredExit) return;
        previewTriggeredExit = true;
        previewHandler.removeCallbacks(previewMonitorRunnable);
        if (exoPlayer != null) {
            try {
                exoPlayer.pause();
                exoPlayer.stop();
            } catch (Exception ignored) {}
        }
        setResult(RESULT_PREVIEW_EXPIRED);
        finish();
    }

    private void cycleResizeMode() {
        currentResizeIndex = (currentResizeIndex + 1) % resizeModes.length;
        if (playerView != null) {
            playerView.setResizeMode(resizeModes[currentResizeIndex]);
        }
        if (resizeBtn != null) {
            resizeBtn.setText(resizeLabels[currentResizeIndex]);
        }
        Toast.makeText(this, "Screen: " + resizeLabels[currentResizeIndex], Toast.LENGTH_SHORT).show();
    }

    private void cyclePlaybackSpeed() {
        currentSpeedIndex = (currentSpeedIndex + 1) % speedValues.length;
        float speed = speedValues[currentSpeedIndex];
        if (exoPlayer != null) {
            exoPlayer.setPlaybackParameters(new PlaybackParameters(speed));
        }
        if (speedBtn != null) {
            speedBtn.setText(speedLabels[currentSpeedIndex]);
        }
    }

    private void toggleOrientation() {
        isLandscapeLocked = !isLandscapeLocked;
        setRequestedOrientation(isLandscapeLocked
                ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    private void hideSystemBars() {
        try {
            WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
            WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
            if (controller != null) {
                controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                controller.hide(WindowInsetsCompat.Type.systemBars());
            }
        } catch (Exception ignored) {}
    }

    private void releasePlayer() {
        if (exoPlayer != null) {
            try {
                lastValidPositionMs = Math.max(lastValidPositionMs, exoPlayer.getCurrentPosition());
                exoPlayer.release();
            } catch (Exception ignored) {}
            exoPlayer = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemBars();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (exoPlayer != null && exoPlayer.isPlaying()) {
            exoPlayer.pause();
        }
    }

    @Override
    protected void onDestroy() {
        previewHandler.removeCallbacks(previewMonitorRunnable);
        releasePlayer();
        if (activeInstanceRef != null && activeInstanceRef.get() == this) {
            activeInstanceRef = null;
        }
        super.onDestroy();
    }
}
