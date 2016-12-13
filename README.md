Features
==
<li> OkHttpDataSource
<li> Subtitles (.srt files, e.g., Arabic subtitles)


API
==
```CinemanaVideoPlayer  cinemanaVideoPlayer = new CinemanaVideoPlayer(this, playerFL, videoFile);```


where:
<li> **playerFL**: A `FrameLayout` to be usesd as the container of the videoFile player.
<li> **videoFile**: A data structure that has a `HashMap` containing a resolution and its url link.
arTranslationFilePath is the url link of the subtitle as an `srt` format.

The *VideoFile* data structure is shown below:

```
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
    public HashMap<Integer, String> resolutions = new HashMap<>();  // <quality, url>; e.g, <QUALITY_720P,     "http://example.com/video720p.mp4"
    public int selectedResolutioin;

    public String arTranslationFilePath;
```


Subtitles
==

The subtitles style can be configured from the library in the file [`CinemanaVideoPlayer.java`](https://github.com/bluemix/CinemanaPlayer/blob/master/cinemanna-player-library/src/main/java/org/earthlink/cinemana/player/CinemanaVideoPlayer.java):


```
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
```


Screenshots
==
Here, it is showin an Arabic subtitle of srt type, from the Titanic movie:
<p align="center">
![Arabic subtitle ExoPlayer](art/sc01.png "Arabic subtitle ExoPlayer")
</p>
<p align="center">
![Arabic subtitle ExoPlayer](art/sc04.png "Arabic subtitle ExoPlayer")
</p>
<p align="center">
![Arabic subtitle ExoPlayer](art/sc03.png "Arabic subtitle ExoPlayer")
</p>





Credits
==
* How to use FrameLayout into an Activity: [ExoPlayerWrapper](https://github.com/cklar/ExoPlayerWrapper)
