package org.earthlink.cinemana.player;

import java.io.Serializable;
import java.util.TreeMap;

/**
 * Created by bluemix on 1/26/16.
 */
public class VideoFile implements Serializable {

    public enum Resolution {
        RESOLUTION_720P,
        RESOLUTION_480P,
        RESOLUTION_360P,
        RESOLUTION_240P
    }

//    public static final int RESOLUTION_720P = -3;
//    public static final int RESOLUTION_480P = -2;
//    public static final int RESOLUTION_360P = -1;
//    public static final int RESOLUTION_240P = 0;

    public String title;
    public String id;

//  <quality, url>; e.g, <RESOLUTION_720P, "http://example.com/video720p.mp4"
//  public TreeMap<Integer, String> qualities = new TreeMap<>();
//  public List<Quality> qualities = new ArrayList<>();
//  public int wantedResolution = RESOLUTION_360P;

    public TreeMap<Resolution, Quality> qualities = new TreeMap<>();
    public Resolution wantedResolution = Resolution.RESOLUTION_240P;

    public String arTranslationFilePath;

//    public static String getQualityString(int quality) {
//        switch (quality) {
//            case RESOLUTION_720P:
//                return "720p";
//            case RESOLUTION_480P:
//                return "480p";
//            case RESOLUTION_360P:
//                return "360p";
//            case RESOLUTION_240P:
//                return "240p";
//            default:
//                return String.valueOf(RESOLUTION_240P);
//        }
//    }
//
//    public static int getQualityIndex(String quality) {
//        switch (quality) {
//            case "720p":
//                return RESOLUTION_720P;
//            case "480p":
//                return RESOLUTION_480P;
//            case "360p":
//                return RESOLUTION_360P;
//            case "240p":
//                return RESOLUTION_240P;
//            default:
//                return RESOLUTION_240P;
//        }
//    }

}
