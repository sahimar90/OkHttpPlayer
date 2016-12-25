package org.earthlink.cinemana.player;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.ext.okhttp.OkHttpDataSourceFactory;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.mediacodec.MediaCodecRenderer;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil;
import com.google.android.exoplayer2.source.ConcatenatingMediaSource;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MergingMediaSource;
import com.google.android.exoplayer2.source.SingleSampleMediaSource;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.text.CaptionStyleCompat;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.TextRenderer;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.FixedTrackSelection;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.google.android.exoplayer2.ui.SubtitleView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;

import java.util.List;
import java.util.Map;
import java.util.TimerTask;

import fr.castorflex.android.smoothprogressbar.SmoothProgressBar;
import okhttp3.OkHttpClient;


/**
 * Created by cklar on 23.09.15.
 */
public class CinemanaVideoPlayer extends Activity implements View.OnClickListener, ExoPlayer.EventListener,
        CinemanaPlaybackControlView.VisibilityListener, TextRenderer.Output {


    private static final String TAG = CinemanaVideoPlayer.class.getSimpleName();
    public static final String KEY_VIDEO_FILE = "key_video_file";
    public static final String KEY_START_POSITION = "key_start_position";


    private static final DefaultBandwidthMeter BANDWIDTH_METER = new DefaultBandwidthMeter();

    private static final long MAX_POSITION_FOR_SEEK_TO_PREVIOUS = 3000;

    protected String userAgent;
    private VideoFile videoFile;
    private EventLogger eventLogger;
    private SubtitleView subtitleLayout;


    ImageView incrementSubs, decrementSubs;
    private float fontScale = 2f;

    TextView videoTitle;
//    private ProgressBar circleProgress;

    private SmoothProgressBar progressBar;
    private Handler mainHandler;
    private Timeline.Window window;
    private CinemanaPlayerView cinemanaPlayerView;
    private Button retryButton;

    private DataSource.Factory mediaDataSourceFactory;
    private SimpleExoPlayer player;
    private MappingTrackSelector trackSelector;

    private boolean playerNeedsSource;

    private boolean shouldAutoPlay;
    private int playerWindow;
    private long playerPosition;
//    protected Timer hideOSBarsTimer;
    protected static final int HIDE_SYSTEM_BARS_DELAY = 2500;


    OkHttpClient okHttpClient;

    public interface SettingsChanged {
        void onResolutionChanged(int resolution);
        void onSubtitleFontSizedChanged(float fontScale);
        void onPaused(long position);
    }

    SettingsChanged settingsChanged;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        videoFile = (VideoFile) getIntent().getSerializableExtra(KEY_VIDEO_FILE);
        playerPosition = getIntent().getLongExtra(KEY_START_POSITION, 0);

        initUi();
        hideSystemBars();
    }

    void initUi() {
        setContentView(R.layout.player_view_layout);

        userAgent = Util.getUserAgent(this, TAG);

        okHttpClient = new OkHttpClient();

//        hideOSBarsTimer = new Timer();
//        hideOSBarsTimer.schedule(new HideSystemBarsTask(), HIDE_SYSTEM_BARS_DELAY);

        subtitleLayout = (SubtitleView) findViewById(R.id.subtitles);


        retryButton = (Button) findViewById(R.id.retry_button);
        retryButton.setOnClickListener(this);


        incrementSubs = (ImageView) findViewById(R.id.incrementSubs);
        decrementSubs = (ImageView) findViewById(R.id.decrementSubs);

        videoTitle = (TextView) findViewById(R.id.videoTitle);
        Typeface videoTitleTypeface
                = Typeface.createFromAsset(getAssets(), "fonts/helveticaneueltarabic-roman.ttf");
        videoTitle.setTypeface(videoTitleTypeface);



        videoTitle.setText(videoFile.title);

//        circleProgress = (ProgressBar) findViewById(circleProgress);
//        circleProgress.setAlpha(0.4f);

        progressBar = (SmoothProgressBar) findViewById(R.id.progressBar);
//        incrementSubs.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                incrementFontSize();
//            }
//        });

//        decrementSubs.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                decrementFontSize();
//            }
//        });
        configureSubtitleView();

        shouldAutoPlay = true;
        mediaDataSourceFactory = buildDataSourceFactory(true);
        mainHandler = new Handler();
        window = new Timeline.Window();

        Log.i(TAG, "window.getDefaultPositionMs(): " + window.getDefaultPositionMs());


        View rootView = findViewById(R.id.root);
        rootView.setOnClickListener(this);


        AspectRatioFrameLayout aspectRatioFrameLayout = (AspectRatioFrameLayout) findViewById(R.id.video_frame);
        aspectRatioFrameLayout.setOnClickListener(this);

        retryButton = (Button) findViewById(R.id.retry_button);
        retryButton.setOnClickListener(this);

        cinemanaPlayerView = (CinemanaPlayerView) findViewById(R.id.player_view);
        cinemanaPlayerView.setControllerVisibilityListener(this);
        cinemanaPlayerView.requestFocus();

        initializePlayer();
    }


    private void hideSystemBars() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
//                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION  // in portrait mode,
                        // the player controllers overlap with the navigation bar
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav bar
        );
    }


    private void initializePlayer() {

//        circleProgress.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.VISIBLE);

        if (player == null) {

            TrackSelection.Factory videoTrackSelectionFactory =
                    new FixedTrackSelection.Factory();
            trackSelector = new DefaultTrackSelector(videoTrackSelectionFactory);
//            trackSelector.addListener(this);
//            trackSelector.addListener(eventLogger);
            eventLogger = new EventLogger(trackSelector);

            player = ExoPlayerFactory.newSimpleInstance(this, trackSelector, new DefaultLoadControl());

            player.addListener(this);
            player.addListener(eventLogger);
            player.setAudioDebugListener(eventLogger);
            player.setVideoDebugListener(eventLogger);
            player.setId3Output(eventLogger);


            // CONTROLLER
            cinemanaPlayerView.setPlayer(player);
            Quality lastQuality = PlayerPrefs.with(this).getLastSelectedQuality();
            if (lastQuality == null) {
                videoFile.wantedResolution = VideoFile.Resolution.RESOLUTION_360P;
                player.seekToDefaultPosition(2);
            } else {
                videoFile.wantedResolution = lastQuality.resolution;
                player.seekTo(lastQuality.windowIndex,
                        PlayerPrefs.with(this).getLastSeekPosition(videoFile.id));
            }

            cinemanaPlayerView.getController().setVideoData(videoFile, trackSelector);
            cinemanaPlayerView.getController().setDecorView(getWindow().getDecorView());


            if (videoFile.arTranslationFilePath.contains(".srt")) {
                cinemanaPlayerView.getController().setSubtitleLayout(subtitleLayout);
            }

            player.setPlayWhenReady(shouldAutoPlay);
            playerNeedsSource = true;
        }

        if (playerNeedsSource) {
            String[] extensions = new String[videoFile.qualities.entrySet().size()];

            MediaSource[] mediaSources = new MediaSource[videoFile.qualities.entrySet().size()];

            int j = 0;
            for (Map.Entry<VideoFile.Resolution, Quality> entry : videoFile.qualities.entrySet()) {
//                VideoFile.Resolution resolution = entry.getKey();
                Quality quality = entry.getValue();

                mediaSources[j] =
                        buildMediaSource(
                                Uri.parse(quality.url),
                                Uri.parse(videoFile.arTranslationFilePath),
                                extensions[j]
                        );

                Log.i(TAG, quality.name + "@" + j);
                quality.windowIndex = j++;
            }

            MediaSource mediaSource = mediaSources.length == 1 ? mediaSources[0]
                    : new ConcatenatingMediaSource(mediaSources);

            player.prepare(mediaSource, false, false);
            playerNeedsSource = false;
            updateButtonVisibilities();
        }
    }

    @Override
    public void onClick(View view) {
        if (view == retryButton) {
            initializePlayer();
        }
    }

    private void releasePlayer() {
        if (player != null) {
            shouldAutoPlay = player.getPlayWhenReady();
            playerWindow = player.getCurrentWindowIndex();
            playerPosition = C.TIME_UNSET;
            Timeline timeline = player.getCurrentTimeline();
            if (timeline != null && timeline.getWindow(playerWindow, window).isSeekable) {
                playerPosition = player.getCurrentPosition();
            }
            player.release();
            player = null;
            trackSelector = null;
            eventLogger = null;
        }
    }


    /**
     * Pause videoFile playback.
     */
    public void play() {
        // Set the autoplay for the videoFile surface layer in case the surface hasn't been created yet.
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
    public void onLoadingChanged(boolean isLoading) {

    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        if (playbackState == ExoPlayer.STATE_ENDED) {
            showControls();
        }
        updateButtonVisibilities();

        String text = "playWhenReady=" + playWhenReady + ", playbackState=";
        switch (playbackState) {
            case ExoPlayer.STATE_BUFFERING:
                text += "buffering";
//                circleProgress.setVisibility(View.VISIBLE);
                progressBar.setVisibility(View.VISIBLE);
                break;
            case ExoPlayer.STATE_ENDED:
                text += "ended";
                break;
            case ExoPlayer.STATE_IDLE:
                text += "idle";
                break;
            case ExoPlayer.STATE_READY:
//                circleProgress.setVisibility(View.INVISIBLE);
                progressBar.setVisibility(View.INVISIBLE);
                text += "ready";
                break;
            default:
                text += "unknown";
                break;
        }

        Log.v(TAG, text);

    }

    @Override
    public void onTimelineChanged(Timeline timeline, Object manifest) {

    }

    @Override
    public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
        updateButtonVisibilities();
        MappingTrackSelector.MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
        if (mappedTrackInfo != null) {
            if (mappedTrackInfo.getTrackTypeRendererSupport(C.TRACK_TYPE_VIDEO)
                    == MappingTrackSelector.MappedTrackInfo.RENDERER_SUPPORT_UNSUPPORTED_TRACKS) {
                showToast(R.string.error_unsupported_video);
            }
            if (mappedTrackInfo.getTrackTypeRendererSupport(C.TRACK_TYPE_AUDIO)
                    == MappingTrackSelector.MappedTrackInfo.RENDERER_SUPPORT_UNSUPPORTED_TRACKS) {
                showToast(R.string.error_unsupported_audio);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        PlayerPrefs.with(this).setLastSeekPosition(player.getCurrentPosition(), videoFile.id);
//        settingsChanged.onPaused(player.getCurrentPosition());

        player.setPlayWhenReady(false);

    }


    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    public void onPlayerError(ExoPlaybackException e) {
        String errorString = null;
        if (e.type == ExoPlaybackException.TYPE_RENDERER) {
            Exception cause = e.getRendererException();
            if (cause instanceof MediaCodecRenderer.DecoderInitializationException) {
                // Special case for decoder initialization failures.
                MediaCodecRenderer.DecoderInitializationException decoderInitializationException =
                        (MediaCodecRenderer.DecoderInitializationException) cause;
                if (decoderInitializationException.decoderName == null) {
                    if (decoderInitializationException.getCause() instanceof MediaCodecUtil.DecoderQueryException) {
                        errorString = getString(R.string.error_querying_decoders);
                    } else if (decoderInitializationException.secureDecoderRequired) {
                        errorString = getString(R.string.error_no_secure_decoder,
                                decoderInitializationException.mimeType);
                    } else {
                        errorString = getString(R.string.error_no_decoder,
                                decoderInitializationException.mimeType);
                    }
                } else {
                    errorString = getString(R.string.error_instantiating_decoder,
                            decoderInitializationException.decoderName);
                }
            }
        }
        if (errorString != null) {
            showToast(errorString);
        }
        playerNeedsSource = true;
        updateButtonVisibilities();
        showControls();
    }

    @Override
    public void onPositionDiscontinuity() {

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

//        if (null != hideOSBarsTimer) {
//            hideOSBarsTimer.cancel();
//        }

        player.stop();
        releasePlayer();
    }

//    private void hideControls() {
//        videoControlsLL.setVisibility(View.GONE);
//        qualityTextLL.setVisibility(View.GONE);
//        videoTitle.setVisibility(View.GONE);
//        additionalControlsLL.setVisibility(View.GONE);
//    }

    private void showControls() {
        retryButton.setVisibility(View.VISIBLE);
//        circleProgress.setVisibility(View.INVISIBLE);
        progressBar.setVisibility(View.INVISIBLE);
        videoTitle.setVisibility(View.VISIBLE);
    }


    private void updateButtonVisibilities() {
        retryButton.setVisibility(playerNeedsSource ? View.VISIBLE : View.GONE);

        if (player == null) {
            return;
        }

        MappingTrackSelector.MappedTrackInfo mappedTrackInfo
                = trackSelector.getCurrentMappedTrackInfo();

        if (mappedTrackInfo == null) {
            return;
        }

        TrackGroupArray trackSelections
                = mappedTrackInfo.getUnassociatedTrackGroups();

        int rendererCount = trackSelections.length;
        for (int i = 0; i < rendererCount; i++) {
            TrackGroup trackGroups = trackSelections.get(i);
            if (trackGroups.length != 0) {
                Button button = new Button(this);
                int label;
                switch (player.getRendererType(i)) {
                    case C.TRACK_TYPE_AUDIO:
                        label = R.string.audio;
                        break;
                    case C.TRACK_TYPE_VIDEO:
                        label = R.string.video;
                        break;
                    case C.TRACK_TYPE_TEXT:
                        label = R.string.text;
                        break;
                    default:
                        continue;
                }
                button.setText(label);
                button.setTag(i);
                button.setOnClickListener(this);
            }
        }
    }


    private void configureSubtitleView() {
        int defaultSubtitleColor = Color.argb(255, 218, 218, 218);
        int outlineColor = Color.argb(255, 43, 43, 43);
        Typeface subtitleTypeface = Typeface.createFromAsset(getAssets(), "fonts/Abdo-Line.otf");
        CaptionStyleCompat style =
                new CaptionStyleCompat(defaultSubtitleColor,
                        Color.TRANSPARENT, Color.TRANSPARENT,
                        CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                        outlineColor, subtitleTypeface);

        subtitleLayout.setStyle(style);
        subtitleLayout.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * fontScale);
    }


    private DataSource.Factory buildDataSourceFactory(boolean useBandwidthMeter) {
        return buildDataSourceFactory(useBandwidthMeter ? BANDWIDTH_METER : null);
    }


    DataSource.Factory buildDataSourceFactory(DefaultBandwidthMeter bandwidthMeter) {
        return new OkHttpDataSourceFactory(okHttpClient, userAgent, bandwidthMeter);
    }


    private MediaSource buildMediaSource(Uri uri, Uri subtitleUri, String overrideExtension) {
        int type = Util.inferContentType(!TextUtils.isEmpty(overrideExtension) ? "." + overrideExtension
                : uri.getLastPathSegment());
        switch (type) {
            case C.TYPE_OTHER:
                Format textFormat = Format.createTextSampleFormat(null, MimeTypes.APPLICATION_SUBRIP,
                        null, Format.NO_VALUE, Format.NO_VALUE, "ar", null);

                MediaSource subtitleSource =
                        new SingleSampleMediaSource(subtitleUri,
                                mediaDataSourceFactory, textFormat, C.TIME_UNSET);

                ExtractorMediaSource extractorMediaSource =
                        new ExtractorMediaSource(uri, mediaDataSourceFactory, new DefaultExtractorsFactory(),
                                mainHandler, eventLogger);
                return new MergingMediaSource(extractorMediaSource, subtitleSource);


            default: {
                throw new IllegalStateException("Unsupported type: " + type);
            }
        }
    }


    @Override
    public void onVisibilityChange(int visibility) {

//        hideOSBarsTimer = new Timer();
//        hideOSBarsTimer.schedule(new HideSystemBarsTask(), HIDE_SYSTEM_BARS_DELAY);

    }

    @Override
    public void onCues(List<Cue> cues) {
        subtitleLayout.setCues(cues);

        Log.i(TAG, "calling onCues...");
    }

    private void showToast(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
    }

    private void showToast(int messageId) {
        showToast(getString(messageId));
    }


    private class HideSystemBarsTask extends TimerTask {
        @Override
        public void run() {

            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    hideSystemBars();
                }
            });
        }
    }
}
