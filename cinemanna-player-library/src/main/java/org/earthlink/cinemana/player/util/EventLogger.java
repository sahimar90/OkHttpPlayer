package org.earthlink.cinemana.player.util;

import android.media.MediaCodec;
import android.os.SystemClock;
import android.util.Log;

import com.google.android.exoplayer.MediaCodecTrackRenderer;
import com.google.android.exoplayer.TimeRange;
import com.google.android.exoplayer.audio.AudioTrack;
import com.google.android.exoplayer.chunk.Format;
import com.google.android.exoplayer.util.VerboseLogUtil;

import org.earthlink.cinemana.player.extractor.CinemanaPlayer;

import java.io.IOException;
import java.text.NumberFormat;
import java.util.Locale;


/**
 * Created by cklar on 23.09.15.
 */
public class EventLogger implements CinemanaPlayer.Listener, CinemanaPlayer.InfoListener,
        CinemanaPlayer.InternalErrorListener {

    private static final String TAG = "EventLogger";
    private static final NumberFormat TIME_FORMAT;

    static {
        TIME_FORMAT = NumberFormat.getInstance(Locale.US);
        TIME_FORMAT.setMinimumFractionDigits(2);
        TIME_FORMAT.setMaximumFractionDigits(2);
    }

    private long sessionStartTimeMs;
    private long[] loadStartTimeMs;
    private long[] availableRangeValuesUs;

    public EventLogger() {
        loadStartTimeMs = new long[CinemanaPlayer.RENDERER_COUNT];
    }

    public void startSession() {
        sessionStartTimeMs = SystemClock.elapsedRealtime();
        Log.d(TAG, "start [0]");
    }

    public void endSession() {
        Log.d(TAG, "end [" + getSessionTimeString() + "]");
    }

    // CinemanaPlayer.Listener

    @Override
    public void onStateChanged(boolean playWhenReady, int state) {
        Log.d(TAG, "state [" + getSessionTimeString() + ", " + playWhenReady + ", "
                + getStateString(state) + "]");
    }

    @Override
    public void onError(Exception e) {
        Log.e(TAG, "playerFailed [" + getSessionTimeString() + "]", e);
    }

    @Override
    public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees,
                                   float pixelWidthHeightRatio) {
        Log.d(TAG, "videoSizeChanged [" + width + ", " + height + ", " + unappliedRotationDegrees
                + ", " + pixelWidthHeightRatio + "]");
    }

    // CinemanaPlayer.InfoListener

    @Override
    public void onBandwidthSample(int elapsedMs, long bytes, long bitrateEstimate) {
        Log.d(TAG, "bandwidth [" + getSessionTimeString() + ", " + bytes + ", "
                + getTimeString(elapsedMs) + ", " + bitrateEstimate + "]");
    }

    @Override
    public void onDroppedFrames(int count, long elapsed) {
        Log.d(TAG, "droppedFrames [" + getSessionTimeString() + ", " + count + "]");
    }

    @Override
    public void onLoadStarted(int sourceId, long length, int type, int trigger, Format format,
                              long mediaStartTimeMs, long mediaEndTimeMs) {
        loadStartTimeMs[sourceId] = SystemClock.elapsedRealtime();
        if (VerboseLogUtil.isTagEnabled(TAG)) {
            Log.v(TAG, "loadStart [" + getSessionTimeString() + ", " + sourceId + ", " + type
                    + ", " + mediaStartTimeMs + ", " + mediaEndTimeMs + "]");
        }
    }

    @Override
    public void onLoadCompleted(int sourceId, long bytesLoaded, int type, int trigger, Format format,
                                long mediaStartTimeMs, long mediaEndTimeMs, long elapsedRealtimeMs, long loadDurationMs) {
        if (VerboseLogUtil.isTagEnabled(TAG)) {
            long downloadTime = SystemClock.elapsedRealtime() - loadStartTimeMs[sourceId];
            Log.v(TAG, "loadEnd [" + getSessionTimeString() + ", " + sourceId + ", " + downloadTime
                    + "]");
        }
    }

    @Override
    public void onVideoFormatEnabled(Format format, int trigger, long mediaTimeMs) {
        Log.d(TAG, "videoFormat [" + getSessionTimeString() + ", " + format.id + ", "
                + Integer.toString(trigger) + "]");
    }

    @Override
    public void onAudioFormatEnabled(Format format, int trigger, long mediaTimeMs) {
        Log.d(TAG, "audioFormat [" + getSessionTimeString() + ", " + format.id + ", "
                + Integer.toString(trigger) + "]");
    }

    // CinemanaPlayer.InternalErrorListener

    @Override
    public void onLoadError(int sourceId, IOException e) {
        printInternalError("loadError", e);
    }

    @Override
    public void onRendererInitializationError(Exception e) {
        printInternalError("rendererInitError", e);
    }

    @Override
    public void onDrmSessionManagerError(Exception e) {
        printInternalError("drmSessionManagerError", e);
    }

    @Override
    public void onDecoderInitializationError(MediaCodecTrackRenderer.DecoderInitializationException e) {
        printInternalError("decoderInitializationError", e);
    }

    @Override
    public void onAudioTrackInitializationError(AudioTrack.InitializationException e) {
        printInternalError("audioTrackInitializationError", e);
    }

    @Override
    public void onAudioTrackWriteError(AudioTrack.WriteException e) {
        printInternalError("audioTrackWriteError", e);
    }

//    @Override
//    public void onAudioTrackUnderrun(int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs) {
//        printInternalError("audioTrackUnderrun [" + bufferSize + ", " + bufferSizeMs + ", "
//                + elapsedSinceLastFeedMs + "]", null);
//    }

    @Override
    public void onCryptoError(MediaCodec.CryptoException e) {
        printInternalError("cryptoError", e);
    }

    @Override
    public void onDecoderInitialized(String decoderName, long elapsedRealtimeMs,
                                     long initializationDurationMs) {
        Log.d(TAG, "decoderInitialized [" + getSessionTimeString() + ", " + decoderName + "]");
    }

    @Override
    public void onAvailableRangeChanged(TimeRange availableRange) {
        availableRangeValuesUs = availableRange.getCurrentBoundsUs(availableRangeValuesUs);
        Log.d(TAG, "availableRange [" + availableRange.isStatic() + ", " + availableRangeValuesUs[0]
                + ", " + availableRangeValuesUs[1] + "]");
    }


    private void printInternalError(String type, Exception e) {
        Log.e(TAG, "internalError [" + getSessionTimeString() + ", " + type + "]", e);
    }

    private String getStateString(int state) {
        switch (state) {
            case CinemanaPlayer.STATE_BUFFERING:
                return "B";
            case CinemanaPlayer.STATE_ENDED:
                return "E";
            case CinemanaPlayer.STATE_IDLE:
                return "I";
            case CinemanaPlayer.STATE_PREPARING:
                return "P";
            case CinemanaPlayer.STATE_READY:
                return "R";
            default:
                return "?";
        }
    }

    private String getSessionTimeString() {
        return getTimeString(SystemClock.elapsedRealtime() - sessionStartTimeMs);
    }

    private String getTimeString(long timeMs) {
        return TIME_FORMAT.format((timeMs) / 1000f);
    }

}
