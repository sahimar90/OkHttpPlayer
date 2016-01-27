package org.earthlink.cinemana.player;

import java.util.HashMap;

/**
 * Created by bluemix on 1/26/16.
 */
public class VideoFile {

    public static int QUALITY_720P = 3;
    public static int QUALITY_480P = 2;
    public static int QUALITY_360P = 1;
    public static int QUALITY_240P = 0;

    public static final int DASH = 0;
    public static final int SS = 1;
    public static final int HLS = 2;
    public static final int OTHER = 3;  // MP4 or WebM


    public int videoType = OTHER;

    public String title;
    public HashMap<Integer, String> resolutions = new HashMap<>();  // <quality, url>; e.g, <QUALITY_720P, "http://example.com/video720p.mp4"
    public int selectedResolutioin;

    public String arTranslationFilePath;
}
