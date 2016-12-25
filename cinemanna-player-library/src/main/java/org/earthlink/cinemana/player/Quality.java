package org.earthlink.cinemana.player;

import java.io.Serializable;

/**
 * Created by bluemix on 12/21/16.
 */

public class Quality implements Serializable {
    public VideoFile.Resolution resolution;
    public String name;
    public String url;
    public int windowIndex;

    public Quality(VideoFile.Resolution resolution, String name, String url) {
        this.resolution = resolution;
        this.name = name;
        this.url = url;
    }
}
