package org.earthlink.cinemana.player;

import java.io.Serializable;
import java.util.TreeMap;

/**
 * Created by bluemix on 1/26/16.
 */
public class VideoFile implements Serializable {

    public static final int QUALITY_720P = -3;
    public static final int QUALITY_480P = -2;
    public static final int QUALITY_360P = -1;
    public static final int QUALITY_240P = 0;

    public static final int DASH = 0;
    public static final int SS = 1;
    public static final int HLS = 2;
    public static final int OTHER = 3;  // e.g., MP4 or WebM


    public int videoType = OTHER;

    public String title;
    public String id;
    public TreeMap<Integer, String> resolutions = new TreeMap<>();  // <quality, url>; e.g, <QUALITY_720P, "http://example.com/video720p.mp4"
    public int wantedResolution = QUALITY_360P;

    public String arTranslationFilePath;


    public static String getQualityString(int quality) {

        switch (quality) {
            case QUALITY_720P:
                return "720p";
            case QUALITY_480P:
                return "480p";
            case QUALITY_360P:
                return "360p";
            case QUALITY_240P:
                return "240p";
            default:
                return String.valueOf(QUALITY_240P);
        }

    }

    public static int getQualityIndex(String quality) {
        switch (quality) {
            case "720p":
                return QUALITY_720P;
            case "480p":
                return QUALITY_480P;
            case "360p":
                return QUALITY_360P;
            case "240p":
                return QUALITY_240P;
            default:
                return QUALITY_240P;
        }
    }
}
