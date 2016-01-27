package org.earthlink.cinemana.player;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.accessibility.CaptioningManager;
import android.widget.FrameLayout;
import android.widget.MediaController;
import android.widget.Toast;

import com.google.android.exoplayer.AspectRatioFrameLayout;
import com.google.android.exoplayer.ExoPlayer;
import com.google.android.exoplayer.audio.AudioCapabilities;
import com.google.android.exoplayer.audio.AudioCapabilitiesReceiver;
import com.google.android.exoplayer.drm.UnsupportedDrmException;
import com.google.android.exoplayer.metadata.GeobMetadata;
import com.google.android.exoplayer.metadata.PrivMetadata;
import com.google.android.exoplayer.metadata.TxxxMetadata;
import com.google.android.exoplayer.text.CaptionStyleCompat;
import com.google.android.exoplayer.text.Cue;
import com.google.android.exoplayer.text.SubtitleLayout;
import com.google.android.exoplayer.util.Util;

import org.earthlink.cinemana.player.extractor.CinemanaPlayer;
import org.earthlink.cinemana.player.extractor.DashRendererBuilder;
import org.earthlink.cinemana.player.extractor.ExtractorRendererBuilder;
import org.earthlink.cinemana.player.extractor.HlsRendererBuilder;
import org.earthlink.cinemana.player.util.EventLogger;
import org.earthlink.cinemana.player.util.ViewGroupUtils;

import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.util.List;
import java.util.Map;


/**
 * Created by cklar on 23.09.15.
 */
public class CinemanaVideoPlayer implements SurfaceHolder.Callback,
        CinemanaPlayer.Listener, CinemanaPlayer.CaptionListener,
        CinemanaPlayer.Id3MetadataListener, AudioCapabilitiesReceiver.Listener {


    private static final String TAG = "CinemanaVideoPlayer";

    private static final CookieManager defaultCookieManager;

    static {
        defaultCookieManager = new CookieManager();
        defaultCookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ORIGINAL_SERVER);
    }

    private Activity activity;
    private VideoFile video;
    private boolean autoplay;

    private EventLogger eventLogger;
    private MediaController mediaController;
    private View shutterView;
    private AspectRatioFrameLayout videoFrame;
    private SurfaceView surfaceView;
    private SubtitleLayout subtitleLayout;

    private CinemanaPlayer wrapper;
    private boolean playerNeedsPrepare;

    private int wantedResolution = VideoFile.QUALITY_720P;
    private long playerPosition;
    private final boolean autoAspectRatio;

    private AudioCapabilitiesReceiver audioCapabilitiesReceiver;
    private boolean enableBackgroundAudio; //Not yet implemented

    public CinemanaVideoPlayer(Activity activity,
                               FrameLayout root,
                               VideoFile video) {
        this(activity, root, video, true, 0, true);
    }

    public CinemanaVideoPlayer(Activity activity,
                               FrameLayout root,
                               VideoFile video,
                               boolean autoplay,
                               int startPostitionMs,
                               boolean autoAspectRatio) {
        this.activity = activity;
        this.video = video;
        this.autoplay = autoplay;
        this.playerPosition = startPostitionMs;
        this.autoAspectRatio = autoAspectRatio;

        bindView(root);
    }

    private void bindView(FrameLayout oldRoot) {
        @SuppressLint("InflateParams")
        View root = activity.getLayoutInflater().inflate(R.layout.player_view_layout, null);

        root.setLayoutParams(oldRoot.getLayoutParams());
        ViewGroupUtils.replaceView(oldRoot, root);



        root.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                    toggleControlsVisibility();
                } else if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
                    view.performClick();
                }
                return true;
            }
        });
        root.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_MENU) {
                    return false;
                }
                return mediaController.dispatchKeyEvent(event);
            }
        });

        audioCapabilitiesReceiver = new AudioCapabilitiesReceiver(activity.getApplicationContext(),
                this);

        shutterView = root.findViewById(R.id.shutter);

        videoFrame = (AspectRatioFrameLayout) root.findViewById(R.id.video_frame);
        surfaceView = (SurfaceView) root.findViewById(R.id.surface_view);
        surfaceView.getHolder().addCallback(this);

        subtitleLayout = (SubtitleLayout) root.findViewById(R.id.subtitles);


        mediaController = new MediaController(activity);
        mediaController.setAnchorView(root);


        CookieHandler currentHandler = CookieHandler.getDefault();
        if (currentHandler != defaultCookieManager) {
            CookieHandler.setDefault(defaultCookieManager);
        }


        configureSubtitleView();

        // The wrapper will be prepared on receiving audio capabilities.
        audioCapabilitiesReceiver.register();
        if (wrapper == null) {
            preparePlayer(autoplay);
        } else {
            wrapper.setBackgrounded(false);
        }


    }


    // old Activity lifecycle Must be called in ExoplayerWrapper#activity lifecycle


    public void onResume() {
        configureSubtitleView();

        // The wrapper will be prepared on receiving audio capabilities.
        audioCapabilitiesReceiver.register();
        if (wrapper == null) {
            preparePlayer(autoplay);
        } else {
            wrapper.setBackgrounded(false);
        }
    }

    public void onPause() {
        if (!enableBackgroundAudio) {
            releasePlayer();
        } else {
            wrapper.setBackgrounded(true);
        }
        shutterView.setVisibility(View.VISIBLE);
    }

    public void onDestroy() {
        audioCapabilitiesReceiver.unregister();
        releasePlayer();
    }


    // AudioCapabilitiesReceiver.Listener methods

    @Override
    public void onAudioCapabilitiesChanged(AudioCapabilities audioCapabilities) {
        if (wrapper == null) {
            return;
        }
        boolean backgrounded = wrapper.getBackgrounded();
        boolean playWhenReady = wrapper.getPlayWhenReady();
        releasePlayer();
        preparePlayer(playWhenReady);
        wrapper.setBackgrounded(backgrounded);
    }

    // Internal methods

    private CinemanaPlayer.RendererBuilder getRendererBuilder() {
        String userAgent = Util.getUserAgent(activity, "ExoPlayerDemo");
        switch (video.videoType) {
            case VideoFile.SS:

            case VideoFile.DASH:
                return new DashRendererBuilder(activity, userAgent, video.resolutions.get(wantedResolution),
                        null);
            case VideoFile.HLS:
                return new HlsRendererBuilder(activity, userAgent, video.resolutions.get(wantedResolution));
            case VideoFile.OTHER:
                return new ExtractorRendererBuilder(activity, userAgent,
                        Uri.parse(video.resolutions.get(wantedResolution)), Uri.parse(video.arTranslationFilePath));
            default:
                throw new IllegalStateException("Unsupported type: " + video.videoType);
        }
    }

    private void preparePlayer(boolean playWhenReady) {
        if (wrapper == null) {
            createNewWrapper();
        }
        wrapper.setSurface(surfaceView.getHolder().getSurface());
        wrapper.setPlayWhenReady(playWhenReady);
    }

    private void createNewWrapper() {
        wrapper = new CinemanaPlayer(getRendererBuilder());
        wrapper.addListener(this);
        wrapper.setCaptionListener(this);
        wrapper.setMetadataListener(this);
        wrapper.seekTo(playerPosition);
        playerNeedsPrepare = true;
        mediaController.setMediaPlayer(wrapper.getPlayerControl());
        mediaController.setEnabled(true);
        eventLogger = new EventLogger();
        eventLogger.startSession();
        wrapper.addListener(eventLogger);
        wrapper.setInfoListener(eventLogger);
        wrapper.setInternalErrorListener(eventLogger);
        if (playerNeedsPrepare) {
            wrapper.prepare();
            playerNeedsPrepare = false;
        }
    }




    public void changeResolution(int wantedResolution) {

        this.wantedResolution = wantedResolution;
        createNewWrapper();
        if (playerNeedsPrepare) {
            wrapper.prepare();
            playerNeedsPrepare = false;
        }
        wrapper.setSurface(surfaceView.getHolder().getSurface());


    }
    public void changeVideo(VideoFile video, long playerPosition, boolean playWhenReady){
        this.video = video;
        this.playerPosition = playerPosition;
        createNewWrapper();
        if (playerNeedsPrepare) {
            wrapper.prepare();
            playerNeedsPrepare = false;
        }
        wrapper.setSurface(surfaceView.getHolder().getSurface());
        wrapper.setPlayWhenReady(playWhenReady);

    }

    public void releasePlayer() {
        if (wrapper != null) {
            playerPosition = wrapper.getCurrentPosition();
            wrapper.release();
            wrapper = null;
            eventLogger.endSession();
            eventLogger = null;
        }
    }

    /**
     * Pause video playback.
     */
    public void pause() {
        // Set the autoplay for the video surface layer in case the surface hasn't been created yet.
        // This way, when the surface is created, it won't start playing.
        wrapper.getPlayerControl().pause();
    }
    /**
     * Pause video playback.
     */
    public void play() {
        // Set the autoplay for the video surface layer in case the surface hasn't been created yet.
        // This way, when the surface is created, it won't start playing.
        wrapper.setPlayWhenReady(false);
    }

    /**
     * Returns the current playback position in milliseconds.
     */
    public long getCurrentPosition() {
        return wrapper.getCurrentPosition();
    }

    // ExoplayerWrapper.Listener implementation

    @Override
    public void onStateChanged(boolean playWhenReady, int playbackState) {
        if (playbackState == ExoPlayer.STATE_ENDED) {
            showControls();
        }
        String text = "playWhenReady=" + playWhenReady + ", playbackState=";
        switch (playbackState) {
            case ExoPlayer.STATE_BUFFERING:
                text += "buffering";
                break;
            case ExoPlayer.STATE_ENDED:
                text += "ended";
                break;
            case ExoPlayer.STATE_IDLE:
                text += "idle";
                break;
            case ExoPlayer.STATE_PREPARING:
                text += "preparing";
                break;
            case ExoPlayer.STATE_READY:
                text += "ready";
                break;
            default:
                text += "unknown";
                break;
        }
        Log.v(TAG, text);
    }

    @Override
    public void onError(Exception e) {
        if (e instanceof UnsupportedDrmException) {
            // Special case DRM failures.
            UnsupportedDrmException unsupportedDrmException = (UnsupportedDrmException) e;
            int stringId = Util.SDK_INT < 18 ? R.string.drm_error_not_supported
                    : unsupportedDrmException.reason == UnsupportedDrmException.REASON_UNSUPPORTED_SCHEME
                    ? R.string.drm_error_unsupported_scheme : R.string.drm_error_unknown;
            Toast.makeText(activity.getApplicationContext(), stringId, Toast.LENGTH_LONG).show();
        }
        playerNeedsPrepare = true;
        showControls();
    }

    @Override
    public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees,
                                   float pixelWidthHeightRatio) {
        shutterView.setVisibility(View.GONE);
        if (autoAspectRatio) {
            videoFrame.setAspectRatio(
                    height == 0 ? 1 : (width * pixelWidthHeightRatio) / height);
        }
    }

    // User controls

    private void toggleControlsVisibility() {
        if (mediaController.isShowing()) {
            mediaController.hide();
        } else {
            showControls();
        }
    }

    private void showControls() {
        mediaController.show(0);
    }

    // ExoplayerWrapper.CaptionListener implementation

    @Override
    public void onCues(List<Cue> cues) {
        subtitleLayout.setCues(cues);
    }

    // ExoplayerWrapper.MetadataListener implementation

    @Override
    public void onId3Metadata(Map<String, Object> metadata) {
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            if (TxxxMetadata.TYPE.equals(entry.getKey())) {
                TxxxMetadata txxxMetadata = (TxxxMetadata) entry.getValue();
                Log.i(TAG, String.format("ID3 TimedMetadata %s: description=%s, value=%s",
                        TxxxMetadata.TYPE, txxxMetadata.description, txxxMetadata.value));
            } else if (PrivMetadata.TYPE.equals(entry.getKey())) {
                PrivMetadata privMetadata = (PrivMetadata) entry.getValue();
                Log.i(TAG, String.format("ID3 TimedMetadata %s: owner=%s",
                        PrivMetadata.TYPE, privMetadata.owner));
            } else if (GeobMetadata.TYPE.equals(entry.getKey())) {
                GeobMetadata geobMetadata = (GeobMetadata) entry.getValue();
                Log.i(TAG, String.format("ID3 TimedMetadata %s: mimeType=%s, filename=%s, " +
                                "description=%s",
                        GeobMetadata.TYPE, geobMetadata.mimeType, geobMetadata.filename,
                        geobMetadata.description));
            } else {
                Log.i(TAG, String.format("ID3 TimedMetadata %s", entry.getKey()));
            }
        }
    }

    // SurfaceHolder.Callback implementation

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (wrapper != null) {
            wrapper.setSurface(holder.getSurface());
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        // Do nothing.
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (wrapper != null) {
            wrapper.blockingClearSurface();
        }
    }

    private void configureSubtitleView() {
        int defaultSubtitleColor = Color.argb(255, 218, 218, 218);
        int outlineColor = Color.argb(255, 43, 43, 43);
        Typeface subtitleTypeface = Typeface.createFromAsset(surfaceView.getContext().getAssets(), "fonts/Abdo-Line.otf");
        CaptionStyleCompat style =
                new CaptionStyleCompat(defaultSubtitleColor,
                        Color.TRANSPARENT, Color.TRANSPARENT,
                        CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                        outlineColor, subtitleTypeface);
        float fontScale = 2.0f;
//        if (Util.SDK_INT >= 19) {
////            style = getUserCaptionStyleV19();
//            fontScale = getUserCaptionFontScaleV19();
//        } else {
////            style = CaptionStyleCompat.DEFAULT;
//            fontScale = 1.5f;
//        }
        subtitleLayout.setStyle(style);
        subtitleLayout.setFractionalTextSize(SubtitleLayout.DEFAULT_TEXT_SIZE_FRACTION * fontScale);
    }

    @TargetApi(19)
    private float getUserCaptionFontScaleV19() {
        CaptioningManager captioningManager =
                (CaptioningManager) activity.getSystemService(Context.CAPTIONING_SERVICE);
        return captioningManager.getFontScale();
    }

    @TargetApi(19)
    private CaptionStyleCompat getUserCaptionStyleV19() {
        CaptioningManager captioningManager =
                (CaptioningManager) activity.getSystemService(Context.CAPTIONING_SERVICE);
        return CaptionStyleCompat.createFromCaptionStyle(captioningManager.getUserStyle());
    }

}
