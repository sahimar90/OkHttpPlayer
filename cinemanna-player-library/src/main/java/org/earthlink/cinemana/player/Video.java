package org.earthlink.cinemana.player;

/**
 * Created by cklar on 22.09.15.
 */
public class Video {

    public static final int DASH = 0;
    public static final int SS = 1;
    public static final int HLS = 2;
    public static final int OTHER = 3;

    /**
     * The URL pointing to the video.
     */
    private final String url;

    /**
     * The video format of the video.
     */
    private final int videoType;

    /**
     * @param url The URL pointing to the video.
     * @param videoType The video format of the video.
     */
    //public Video(String url, @VideoType int videoType) {
    public Video(String url, int videoType) {
        this.url = url;
        this.videoType = videoType;
    }

    /**
     * Returns the URL pointing to the video.
     */
    public String getUrl() {
        return url;
    }

    /**
     * Returns the video format of the video.
     */
    public int getVideoType() {
        return videoType;
    }
}