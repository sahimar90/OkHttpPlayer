package org.earthlink.cinemana.player;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
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
        CinemanaPlayer.Listener, CinemanaPlayer.CaptionListener, View.OnClickListener,
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

    private CinemanaPlayer player;
    private boolean playerNeedsPrepare;

    private int wantedResolution = VideoFile.QUALITY_480P;
    private long playerPosition;
    private final boolean autoAspectRatio;
    private boolean enableBackgroundAudio = false;

    private AudioCapabilitiesReceiver audioCapabilitiesReceiver;

    LinearLayout qualityTextLL, videoControlsLL, additionalControlsLL;
    ImageView incrementSubs, decrementSubs;
    private float fontScale = 2f;

    TextView videoTitle;
    Button retryButton;
    private ProgressBar circleProgress;


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


        qualityTextLL = (LinearLayout) root.findViewById(R.id.qualityTextLL);
        videoControlsLL = (LinearLayout) root.findViewById(R.id.videoControlsLL);

        retryButton = (Button) root.findViewById(R.id.retry_button);
        retryButton.setOnClickListener(this);

        mediaController = new MediaController(activity);
        mediaController.setAnchorView(root);

        incrementSubs = (ImageView) root.findViewById(R.id.incrementSubs);
        decrementSubs = (ImageView) root.findViewById(R.id.decrementSubs);

        videoTitle = (TextView) root.findViewById(R.id.videoTitle);

        videoTitle.setText(video.title);

        additionalControlsLL = (LinearLayout) root.findViewById(R.id.additionalControlsLL);

        circleProgress = (ProgressBar) root.findViewById(R.id.circleProgress);
        circleProgress.setAlpha(0.4f);

        incrementSubs.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                incrementFontSize();
            }
        });
        decrementSubs.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                decrementFontSize();
            }
        });

        CookieHandler currentHandler = CookieHandler.getDefault();
        if (currentHandler != defaultCookieManager) {
            CookieHandler.setDefault(defaultCookieManager);
        }


        configureSubtitleView();

        // The player will be prepared on receiving audio capabilities.
        audioCapabilitiesReceiver.register();
        if (player == null) {
            preparePlayer(autoplay);
        } else {
            player.setBackgrounded(player.getBackgrounded());
        }


        wantedResolution = video.wantedResolution;


        addQualitiesTextViews();


        initializeAndPreparePlayer();

    }



    // adds a control view to the end of videoControlsLL
    public void addViewControl(View view) {
        int margin = (int) surfaceView.getContext().getResources().getDimension(R.dimen.side_control_margin);
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(margin, margin, margin, margin); //substitute parameters for left, top, right, bottom
        view.setLayoutParams(params);

        additionalControlsLL.addView(view);
    }


    @Override
    public void onClick(View view) {


        Log.i(TAG, "view with id clicked: " + view.getId());
        if (view == retryButton) {
            initializeAndPreparePlayer();
        }

    }


    // old Activity lifecycle Must be called in ExoplayerWrapper#activity lifecycle


    public void onResume() {
        configureSubtitleView();

        // The player will be prepared on receiving audio capabilities.
        audioCapabilitiesReceiver.register();
        if (player == null) {
            preparePlayer(autoplay);
        } else {
            player.setBackgrounded(false);
        }
    }

    public void onPause() {
        if (!enableBackgroundAudio) {
            releasePlayer();
        } else {
            player.setBackgrounded(true);
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
        if (player == null) {
            return;
        }
        boolean playWhenReady = player.getPlayWhenReady();
        releasePlayer();
        preparePlayer(playWhenReady);
        player.setBackgrounded(player.getBackgrounded());
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
        if (player == null) {
            player = new CinemanaPlayer(getRendererBuilder());
            player.addListener(this);
            player.seekTo(playerPosition);
            playerNeedsPrepare = true;
            player.setBackgrounded(enableBackgroundAudio);
            mediaController.setMediaPlayer(player.getPlayerControl());
            mediaController.setEnabled(true);
            eventLogger = new EventLogger();
            eventLogger.startSession();
            player.addListener(eventLogger);
            player.setInfoListener(eventLogger);
            player.setCaptionListener(this);
            player.setInternalErrorListener(eventLogger);
        }
        if (playerNeedsPrepare) {
            player.prepare();
            playerNeedsPrepare = false;
        }
        player.setSurface(surfaceView.getHolder().getSurface());
        player.setPlayWhenReady(playWhenReady);
    }

    private void releasePlayer() {
        if (player != null) {
            playerPosition = player.getCurrentPosition();
            player.release();
            player = null;
            eventLogger.endSession();
            eventLogger = null;
        }
    }


    public void changeResolution(int wantedResolution) {

        this.wantedResolution = wantedResolution;

        releasePlayer();

        initializeAndPreparePlayer();

    }


    /**
     * Pause video playback.
     */
    public void pause() {
        // Set the autoplay for the video surface layer in case the surface hasn't been created yet.
        // This way, when the surface is created, it won't start playing.
        player.getPlayerControl().pause();
    }

    /**
     * Pause video playback.
     */
    public void play() {
        // Set the autoplay for the video surface layer in case the surface hasn't been created yet.
        // This way, when the surface is created, it won't start playing.
        player.setPlayWhenReady(false);
    }

    /**
     * Returns the current playback position in milliseconds.
     */
    public long getCurrentPosition() {
        return player.getCurrentPosition();
    }


    @Override
    public void onStateChanged(boolean playWhenReady, int playbackState) {
        if (playbackState == ExoPlayer.STATE_ENDED) {
            showControls();
        }
        String text = "playWhenReady=" + playWhenReady + ", playbackState=";
        switch (playbackState) {
            case ExoPlayer.STATE_BUFFERING:
                text += "buffering";
                circleProgress.setVisibility(View.VISIBLE);
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
                circleProgress.setVisibility(View.INVISIBLE);
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
            hideControls();
        } else {
            showControls();
        }
    }

    private void hideControls() {
        videoControlsLL.setVisibility(View.GONE);
        qualityTextLL.setVisibility(View.GONE);
        videoTitle.setVisibility(View.GONE);
        additionalControlsLL.setVisibility(View.GONE);
        mediaController.hide();

    }

    private void showControls() {
        retryButton.setVisibility(playerNeedsPrepare ? View.VISIBLE : View.GONE);

        videoControlsLL.setVisibility(View.VISIBLE);
        qualityTextLL.setVisibility(View.VISIBLE);
        videoTitle.setVisibility(View.VISIBLE);
        additionalControlsLL.setVisibility(View.VISIBLE);
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
        if (player != null) {
            player.setSurface(holder.getSurface());
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        // Do nothing.
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (player != null) {
            player.blockingClearSurface();
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

        subtitleLayout.setStyle(style);
        subtitleLayout.setFractionalTextSize(SubtitleLayout.DEFAULT_TEXT_SIZE_FRACTION * fontScale);
    }

    private void initializeAndPreparePlayer() {
        circleProgress.setVisibility(View.VISIBLE);

        if (player == null) {
            preparePlayer(true);
        } else {
            player.setBackgrounded(player.getBackgrounded());
        }
    }


    private void decrementFontSize() {
        fontScale = Math.max(fontScale / 1.05f, 0.5f);
        subtitleLayout.setFractionalTextSize(SubtitleLayout.DEFAULT_TEXT_SIZE_FRACTION * fontScale);

        Log.i(TAG, "fontScale: " + fontScale);

    }

    private void incrementFontSize() {

        fontScale = Math.min(fontScale * 1.05f, 3f);
        subtitleLayout.setFractionalTextSize(SubtitleLayout.DEFAULT_TEXT_SIZE_FRACTION * fontScale);

        Log.i(TAG, "fontScale: " + fontScale);

    }

    private void addQualitiesTextViews() {

        Context context = surfaceView.getContext();
        Log.i(TAG, "adding quality TextViews");

        Switch videoFormatSwitch = new Switch(context);
        videoFormatSwitch.setLayoutParams(
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));


        for (int quality : video.resolutions.keySet()) {
            TextView qualityTextView = new TextView(context);
            String qualityText = VideoFile.getQualityString(quality);    // spaces before and after the text, e.g., 720p,
            // to have a space when its background border is active
            qualityTextView.setText(qualityText);

            qualityTextView.setTag(video.resolutions.get(quality)); // the url link

            qualityTextView.setOnClickListener(qualityChangeListener);

            if (wantedResolution == quality) {
                setQualityTextStyle(qualityTextView, true, false);

            } else {
                setQualityTextStyle(qualityTextView, false, false);
            }
            qualityTextLL.addView(qualityTextView);
        }

    }

    View.OnClickListener qualityChangeListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {

            for (int i = 0; i < qualityTextLL.getChildCount(); i++) {
                TextView qualityTextView = (TextView) qualityTextLL.getChildAt(i);
                setQualityTextStyle(qualityTextView, false, true);

            }
            setQualityTextStyle((TextView) view, true, true);

            wantedResolution = VideoFile.getQualityIndex((String) ((TextView) view).getText());


            Log.i(TAG, "will change to resolution " + VideoFile.getQualityString(wantedResolution));
            changeResolution(wantedResolution);
        }
    };


    private void setQualityTextStyle(TextView qualityTextView, boolean active, boolean onlyBoldAndColor) {
//        Typeface videoQualityTextTypeface = Typeface.createFromAsset(surfaceView.getContext().getAssets(),
//                "fonts/ITCFranklinGothicStd-BkCp.otf");

        if (!onlyBoldAndColor) {
            float qualityTextSize =
                    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 20, surfaceView.getContext().getResources().getDisplayMetrics());


            LinearLayout.LayoutParams qualityTextViewsMargins = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            qualityTextViewsMargins.setMargins(3, 3, 3, 3);
//            qualityTextView.setTextSize(qualityTextSize);
            qualityTextView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            qualityTextView.setLayoutParams(qualityTextViewsMargins);
        }

//        qualityTextView.setTypeface(videoQualityTextTypeface, Typeface.NORMAL);


        if (active) {
            qualityTextView.setTextColor(surfaceView.getContext().getResources().getColor(R.color.selectedQuality));
//            qualityTextView.setBackgroundResource(R.drawable.video_rounded_frame);
            qualityTextView.setTypeface(null, Typeface.BOLD);
        } else {
            qualityTextView.setTextColor(surfaceView.getContext().getResources().getColor(R.color.unselectedQuality));
//            qualityTextView.setBackground(null);
            qualityTextView.setTypeface(null,Typeface.NORMAL);
        }
    }
}
