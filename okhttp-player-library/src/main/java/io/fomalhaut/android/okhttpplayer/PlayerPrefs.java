package io.fomalhaut.android.okhttpplayer;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import io.paperdb.Paper;

import static io.paperdb.Paper.book;

/**
 * Created by bluemix on 12/19/16.
 */

public class PlayerPrefs {


    private static PlayerPrefs singleton;
    Context context;
    private SharedPreferences sharedPref;
    SharedPreferences.Editor sharedPrefsEditor;


    private PlayerPrefs(Context context) {
        this.context = context;
        sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        sharedPrefsEditor = sharedPref.edit();
    }

    public static PlayerPrefs with(Context context) {

        if (singleton == null) {
            singleton = new PlayerPrefs(context);
        }
        return singleton;
    }

    public boolean showSubtitles() {
        return sharedPref.getBoolean(context.getString(R.string.key_show_subtitles), true);
    }

    public void setShowSubtitle(Boolean showSubtitle) {
        sharedPrefsEditor.putBoolean(context.getString(R.string.key_show_subtitles), showSubtitle);
        sharedPrefsEditor.apply();
    }

    public float getSubtitleFontSize() {
        return sharedPref.getFloat(context.getString(R.string.key_subtitle_font_size), 2f);
    }

    public void setSubtitleFontSize(float size) {
        sharedPrefsEditor.putFloat(context.getString(R.string.key_subtitle_font_size), size);
        sharedPrefsEditor.apply();
    }

    public Quality getLastSelectedQuality() {
        return Paper.book().read(context.getString(R.string.key_last_quality));
    }

    public void setLastSelectedQuality(Quality quality) {
        book().write(context.getString(R.string.key_last_quality), quality);
    }


    public long getLastSeekPosition(String videoId) {
        return sharedPref.getLong("seek_" + videoId + "_" + context.getString(R.string.key_seek_position), 0L);

    }

    public void setLastSeekPosition(long position, String videoId) {
        sharedPrefsEditor.putLong("seek_" + videoId + "_" + context.getString(R.string.key_seek_position), position);
        sharedPrefsEditor.apply();
    }



}
