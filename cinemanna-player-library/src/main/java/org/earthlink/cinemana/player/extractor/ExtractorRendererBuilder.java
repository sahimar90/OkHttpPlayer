/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.earthlink.cinemana.player.extractor;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaCodec;
import android.net.Uri;

import com.google.android.exoplayer.MediaCodecAudioTrackRenderer;
import com.google.android.exoplayer.MediaCodecSelector;
import com.google.android.exoplayer.MediaCodecVideoTrackRenderer;
import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.SingleSampleSource;
import com.google.android.exoplayer.TrackRenderer;
import com.google.android.exoplayer.audio.AudioCapabilities;
import com.google.android.exoplayer.extractor.Extractor;
import com.google.android.exoplayer.extractor.ExtractorSampleSource;
import com.google.android.exoplayer.text.TextTrackRenderer;
import com.google.android.exoplayer.upstream.Allocator;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DefaultAllocator;
import com.google.android.exoplayer.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer.upstream.DefaultUriDataSource;
import com.google.android.exoplayer.util.MimeTypes;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

/**
 * A {@link CinemanaPlayer.RendererBuilder} for streams that can be read using an {@link Extractor}.
 */
public class ExtractorRendererBuilder implements CinemanaPlayer.RendererBuilder {

    private static final int BUFFER_SEGMENT_SIZE = 64 * 1024;
    private static final int BUFFER_SEGMENT_COUNT = 256;

    private final Context context;
    private final String userAgent;
    private final Uri uri;
    private final Uri subTitleUrl;

    public ExtractorRendererBuilder(Context context, String userAgent, Uri uri, Uri suburi) {
        this.context = context;
        this.userAgent = userAgent;
        this.uri = uri;
        this.subTitleUrl = suburi;
    }

    @Override
    public void buildRenderers(CinemanaPlayer player) {
        Allocator allocator = new DefaultAllocator(BUFFER_SEGMENT_SIZE);

        // Build the video and audio renderers.
        DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter(player.getMainHandler(),
                null);
        DataSource dataSource = new DefaultUriDataSource(context, bandwidthMeter,
                new OkHttpDataSource(getDefaultOkHttpClient(true), userAgent, null, bandwidthMeter));
        ExtractorSampleSource sampleSource = new ExtractorSampleSource(uri, dataSource, allocator,
                BUFFER_SEGMENT_COUNT * BUFFER_SEGMENT_SIZE);


        MediaCodecVideoTrackRenderer videoRenderer = new MediaCodecVideoTrackRenderer(context,
                sampleSource, MediaCodecSelector.DEFAULT, MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT, 5000,
                player.getMainHandler(), player, 50);
        MediaCodecAudioTrackRenderer audioRenderer = new MediaCodecAudioTrackRenderer(sampleSource,
                MediaCodecSelector.DEFAULT, null, true, player.getMainHandler(), player,
                AudioCapabilities.getCapabilities(context), AudioManager.STREAM_MUSIC);



        DataSource textDataSource = new DefaultUriDataSource(context, bandwidthMeter, userAgent);
        SingleSampleSource textSampleSource = new SingleSampleSource(subTitleUrl, textDataSource,
                MediaFormat.createTextFormat(String.valueOf(MediaFormat.NO_VALUE), MimeTypes.APPLICATION_SUBRIP,
                        MediaFormat.NO_VALUE, TrackRenderer.MATCH_LONGEST_US, null));
        TrackRenderer textRenderer = new TextTrackRenderer(textSampleSource, player,
                player.getMainHandler().getLooper());

        // Invoke the callback.
        TrackRenderer[] renderers = new TrackRenderer[CinemanaPlayer.RENDERER_COUNT];
        renderers[CinemanaPlayer.TYPE_VIDEO] = videoRenderer;
        renderers[CinemanaPlayer.TYPE_AUDIO] = audioRenderer;
        renderers[CinemanaPlayer.TYPE_TEXT] = textRenderer;
        player.onRenderers(renderers, bandwidthMeter);
        //text renderer is disabled by default initially. So, enabling it here.
        player.setSelectedTrack(CinemanaPlayer.TYPE_TEXT, CinemanaPlayer.TRACK_DEFAULT);
    }


    private static OkHttpClient getDefaultOkHttpClient(boolean allowCrossProtocolRedirects) {

//    OkHttpClient.Builder clientBuilder =
//            new OkHttpClient.Builder().followRedirects(true).followSslRedirects(allowCrossProtocolRedirects)
//            .readTimeout(DefaultHttpDataSource.DEFAULT_READ_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
//            .connectTimeout(DefaultHttpDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);


        OkHttpClient okHttpClient = new OkHttpClient();
        okHttpClient.newBuilder().followRedirects(true).followSslRedirects(allowCrossProtocolRedirects)
                .readTimeout(DefaultHttpDataSource.DEFAULT_READ_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                .connectTimeout(DefaultHttpDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        return okHttpClient;
    }


    @Override
    public void cancel() {
        // Do nothing.
    }

}
