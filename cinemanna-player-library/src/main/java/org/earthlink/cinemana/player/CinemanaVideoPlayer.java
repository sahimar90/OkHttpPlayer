package org.earthlink.cinemana.player;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
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
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.smoothstreaming.DefaultSsChunkSource;
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource;
import com.google.android.exoplayer2.text.CaptionStyleCompat;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.TextRenderer;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.FixedTrackSelection;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelections;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.google.android.exoplayer2.ui.PlaybackControlView;
import com.google.android.exoplayer2.ui.SimpleExoPlayerView;
import com.google.android.exoplayer2.ui.SubtitleView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import okhttp3.OkHttpClient;


/**
 * Created by cklar on 23.09.15.
 */
public class CinemanaVideoPlayer extends Activity implements View.OnClickListener, ExoPlayer.EventListener,
        TrackSelector.EventListener<MappingTrackSelector.MappedTrackInfo>,
        PlaybackControlView.VisibilityListener, TextRenderer.Output {


    private static final String TAG = CinemanaVideoPlayer.class.getSimpleName();
    public static final String KEY_VIDEO_FILE = "key_video_file";
    public static final String KEY_START_POSITION = "key_start_position";


    private static final DefaultBandwidthMeter BANDWIDTH_METER = new DefaultBandwidthMeter();

    private static final long MAX_POSITION_FOR_SEEK_TO_PREVIOUS = 3000;

    protected String userAgent;
    private VideoFile videoFile;
    private EventLogger eventLogger;
    private SubtitleView subtitleLayout;

    private int wantedResolution = VideoFile.QUALITY_720P;


    LinearLayout qualityTextLL, videoControlsLL, additionalControlsLL;
    ImageView incrementSubs, decrementSubs;
    private float fontScale = 2f;

    TextView videoTitle;
    private ProgressBar circleProgress;

    private Handler mainHandler;
    private Timeline.Window window;
    private SimpleExoPlayerView simpleExoPlayerView;
    private Button retryButton;

    private DataSource.Factory mediaDataSourceFactory;
    private SimpleExoPlayer player;
    private MappingTrackSelector trackSelector;

    private boolean playerNeedsSource;

    private boolean shouldAutoPlay;
    private int playerWindow;
    private long playerPosition;
    protected Timer hideOSBarsTimer;
    protected static final int HIDE_SYSTEM_BARS_DELAY = 2500;


    OkHttpClient okHttpClient;

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

        hideOSBarsTimer = new Timer();
        hideOSBarsTimer.schedule(new HideSystemBarsTask(), HIDE_SYSTEM_BARS_DELAY);


        subtitleLayout = (SubtitleView) findViewById(R.id.subtitles);

        qualityTextLL = (LinearLayout) findViewById(R.id.qualityTextLL);
        videoControlsLL = (LinearLayout) findViewById(R.id.videoControlsLL);

        retryButton = (Button) findViewById(R.id.retry_button);
        retryButton.setOnClickListener(this);


        incrementSubs = (ImageView) findViewById(R.id.incrementSubs);
        decrementSubs = (ImageView) findViewById(R.id.decrementSubs);

        videoTitle = (TextView) findViewById(R.id.videoTitle);

        videoTitle.setText(videoFile.title);

        additionalControlsLL = (LinearLayout) findViewById(R.id.additionalControlsLL);

        circleProgress = (ProgressBar) findViewById(R.id.circleProgress);
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

        configureSubtitleView();
        wantedResolution = videoFile.wantedResolution;


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

        simpleExoPlayerView = (SimpleExoPlayerView) findViewById(R.id.player_view);
        simpleExoPlayerView.setControllerVisibilityListener(this);
//        simpleExoPlayerView.requestFocus();


        addQualitiesTextViews();

        initializePlayer();

    }


    private void hideSystemBars() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav bar
        );


    }


    private void initializePlayer() {

        circleProgress.setVisibility(View.VISIBLE);


        if (player == null) {

            eventLogger = new EventLogger();
            TrackSelection.Factory videoTrackSelectionFactory =
                    new FixedTrackSelection.Factory();
            trackSelector = new DefaultTrackSelector(mainHandler, videoTrackSelectionFactory);
            trackSelector.addListener(this);
            trackSelector.addListener(eventLogger);

            player = ExoPlayerFactory.newSimpleInstance(this, trackSelector, new DefaultLoadControl());
            player.addListener(this);
            player.addListener(eventLogger);
            player.setAudioDebugListener(eventLogger);
            player.setVideoDebugListener(eventLogger);
            player.setId3Output(eventLogger);

            simpleExoPlayerView.setPlayer(player);
            if (playerPosition == C.TIME_UNSET) {
                player.seekToDefaultPosition(playerWindow);
            } else {
                player.seekTo(playerWindow, playerPosition);
            }

            player.setPlayWhenReady(shouldAutoPlay);
            playerNeedsSource = true;
        }
        if (playerNeedsSource) {
            Uri[] uris;
            String[] extensions;

            List<String> uriStrings = new ArrayList<>();

            for (Map.Entry<Integer, String> entry : videoFile.resolutions.entrySet()) {
                int key = entry.getKey();
                String value = entry.getValue();
                uriStrings.add(value);
            }

            uris = new Uri[uriStrings.size()];
            for (int i = 0; i < uriStrings.size(); i++) {
                uris[i] = Uri.parse(uriStrings.get(i));
            }

            extensions = new String[uriStrings.size()];

            if (Util.maybeRequestReadExternalStoragePermission(this, uris)) {
                // The player will be reinitialized if the permission is granted.
                return;
            }

            MediaSource[] mediaSources = new MediaSource[uris.length];
            for (int i = 0; i < uris.length; i++) {
                mediaSources[i] = buildMediaSource(uris[i], Uri.parse(videoFile.arTranslationFilePath), extensions[i]);
                Log.i(TAG, "uris[" + i + "]: " + uris[i]);
            }

            MediaSource mediaSource = mediaSources.length == 1 ? mediaSources[0]
                    : new ConcatenatingMediaSource(mediaSources);


            player.prepare(mediaSource, false, false);
            playerNeedsSource = false;
            updateButtonVisibilities();
        }
    }

//    // adds a control view to the end of videoControlsLL
//    public void addViewControl(View view) {
//        int margin = (int) surfaceView.getContext().getResources().getDimension(R.dimen.side_control_margin);
//        LinearLayout.LayoutParams params =
//                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
//                        ViewGroup.LayoutParams.WRAP_CONTENT);
//        params.setMargins(margin, margin, margin, margin); //substitute parameters for left, top, right, bottom
//        view.setLayoutParams(params);
//
//        additionalControlsLL.addView(view);
//    }

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
                circleProgress.setVisibility(View.VISIBLE);
                break;
            case ExoPlayer.STATE_ENDED:
                text += "ended";
                break;
            case ExoPlayer.STATE_IDLE:
                text += "idle";
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
    public void onTimelineChanged(Timeline timeline, Object manifest) {

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

        if (null != hideOSBarsTimer) {
            hideOSBarsTimer.cancel();
        }


        player.stop();

        releasePlayer();
    }


    private void hideControls() {
        videoControlsLL.setVisibility(View.GONE);
        qualityTextLL.setVisibility(View.GONE);
        videoTitle.setVisibility(View.GONE);
        additionalControlsLL.setVisibility(View.GONE);
    }

    private void showControls() {
        retryButton.setVisibility(View.VISIBLE);

        videoControlsLL.setVisibility(View.VISIBLE);
        qualityTextLL.setVisibility(View.VISIBLE);
        videoTitle.setVisibility(View.VISIBLE);
        additionalControlsLL.setVisibility(View.VISIBLE);
    }


    private void updateButtonVisibilities() {
        retryButton.setVisibility(playerNeedsSource ? View.VISIBLE : View.GONE);

        if (player == null) {
            return;
        }

        TrackSelections<MappingTrackSelector.MappedTrackInfo>
                trackSelections = trackSelector.getCurrentSelections();
        if (trackSelections == null) {
            return;
        }

        int rendererCount = trackSelections.length;
        for (int i = 0; i < rendererCount; i++) {
            TrackGroupArray trackGroups = trackSelections.info.getTrackGroups(i);
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


    private void decrementFontSize() {
        fontScale = Math.max(fontScale / 1.05f, 0.5f);
        subtitleLayout.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * fontScale);

        Log.i(TAG, "fontScale: " + fontScale);
    }

    private void incrementFontSize() {

        fontScale = Math.min(fontScale * 1.05f, 3f);
        subtitleLayout.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * fontScale);

        Log.i(TAG, "fontScale: " + fontScale);
    }

    private void addQualitiesTextViews() {
        Log.i(TAG, "adding quality TextViews");

//        Switch videoFormatSwitch = new Switch(this);
//        videoFormatSwitch.setLayoutParams(
//                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
//                        ViewGroup.LayoutParams.WRAP_CONTENT));

        int i = 0;
        for (int quality : videoFile.resolutions.keySet()) {
            TextView qualityTextView = new TextView(this);
            String qualityText = VideoFile.getQualityString(quality);    // spaces before and after the text, e.g., 720p,
            // to have a space when its background border is active
            qualityTextView.setText(qualityText);

            qualityTextView.setTag(videoFile.resolutions.get(quality)); // the url link
            qualityTextView.setTag(-101, i++);

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
            changeResolution(wantedResolution, (int) view.getTag(-101));
        }
    };

    public void changeResolution(int wantedResolution, int position) {
        this.wantedResolution = wantedResolution;

        Timeline currentTimeline = player.getCurrentTimeline();
        if (currentTimeline == null) {
            return;
        }
        int currentWindowIndex = player.getCurrentWindowIndex();
        Log.i(TAG, "position: " + position);
        Log.i(TAG, "player.getCurrentWindowIndex(): " + player.getCurrentWindowIndex());

        if (currentWindowIndex < currentTimeline.getWindowCount() - 1) {
//            Log.i(TAG, "will change window to " + (currentWindowIndex + 1));
//            player.seekToDefaultPosition(currentWindowIndex + 1);
            player.seekToDefaultPosition(position);
        } else if (currentTimeline.getWindow(currentWindowIndex, window, false).isDynamic) {
            player.seekToDefaultPosition();
        }
    }


    private void setQualityTextStyle(TextView qualityTextView, boolean active, boolean onlyBoldAndColor) {
//        Typeface videoQualityTextTypeface = Typeface.createFromAsset(surfaceView.getContext().getAssets(),
//                "fonts/ITCFranklinGothicStd-BkCp.otf");

        if (!onlyBoldAndColor) {
            float qualityTextSize =
                    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 20, getResources().getDisplayMetrics());


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
            qualityTextView.setTextColor(getResources().getColor(R.color.selectedQuality));
//            qualityTextView.setBackgroundResource(R.drawable.video_rounded_frame);
            qualityTextView.setTypeface(null, Typeface.BOLD);
        } else {
            qualityTextView.setTextColor(getResources().getColor(R.color.unselectedQuality));
//            qualityTextView.setBackground(null);
            qualityTextView.setTypeface(null, Typeface.NORMAL);
        }
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
            case C.TYPE_SS:
                return new SsMediaSource(uri, buildDataSourceFactory(false),
                        new DefaultSsChunkSource.Factory(mediaDataSourceFactory), mainHandler, eventLogger);
            case C.TYPE_DASH:
                return new DashMediaSource(uri, buildDataSourceFactory(false),
                        new DefaultDashChunkSource.Factory(mediaDataSourceFactory), mainHandler, eventLogger);
            case C.TYPE_HLS:
                return new HlsMediaSource(uri, mediaDataSourceFactory, mainHandler, eventLogger);
            case C.TYPE_OTHER:
                Log.i(TAG, "C.TYPE_OTHER");

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
    public void onTrackSelectionsChanged(TrackSelections<?
            extends MappingTrackSelector.MappedTrackInfo> trackSelections) {
        updateButtonVisibilities();
        MappingTrackSelector.MappedTrackInfo trackInfo = trackSelections.info;
        if (trackInfo.hasOnlyUnplayableTracks(C.TRACK_TYPE_VIDEO)) {
            showToast(R.string.error_unsupported_video);
        }
        if (trackInfo.hasOnlyUnplayableTracks(C.TRACK_TYPE_AUDIO)) {
            showToast(R.string.error_unsupported_audio);
        }
    }

    @Override
    public void onVisibilityChange(int visibility) {
        Log.i(TAG, "visibility changed...");

        hideOSBarsTimer = new Timer();
        hideOSBarsTimer.schedule(new HideSystemBarsTask(), HIDE_SYSTEM_BARS_DELAY);

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
            Log.i(TAG, "will hide system bars");

            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    hideSystemBars();
                }
            });
        }
    }
}
