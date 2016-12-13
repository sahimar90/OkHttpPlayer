package org.cinemana.player.app.demo;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.NavUtils;
import android.util.Log;
import android.view.MenuItem;
import android.widget.LinearLayout;

import org.earthlink.cinemana.player.CinemanaVideoPlayer;
import org.earthlink.cinemana.player.VideoFile;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;


public class PlayerActivity extends FragmentActivity {

    private static final String TAG = PlayerActivity.class.getSimpleName();
    String transcodedFilesPath_url = "http://cinemana.earthlinktele.com/api/android/transcoddedFiles/id/";
    String allVideoInfo_URL = "http://cinemana.earthlinktele.com/api/android/allVideoInfo/id/";
    VideoFile videoFile = new VideoFile();

    LinearLayout mainLL;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_player);

        mainLL = (LinearLayout) findViewById(R.id.mainLL);
        getTranscodedFiles("4100");
    }



    private void startCinemanaPlayer(VideoFile videoFile) {
        Intent i = new Intent(this, CinemanaVideoPlayer.class);
        i.putExtra(CinemanaVideoPlayer.KEY_VIDEO_FILE, videoFile);
        i.putExtra(CinemanaVideoPlayer.KEY_START_POSITION, 0);

        startActivity(i);
    }


    private void getSrtLink(String videoId) {

        Callback callback = new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {

            }

            @Override
            public void onResponse(Call call, Response _response) throws IOException {
                JSONObject response = null;
                String arTranslationFilePath = "";
                try {
                    response = new JSONObject(_response.body().string());

                    arTranslationFilePath = (String) response.opt("arTranslationFilePath");
                    Log.i(TAG, "arTranslationFilePath: " + arTranslationFilePath);

                    videoFile.arTranslationFilePath = arTranslationFilePath;

                    videoFile.title = response.optString("en_title");
                    videoFile.id = response.optString("nb");

                    videoFile.wantedResolution = VideoFile.QUALITY_720P;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            startCinemanaPlayer(videoFile);
                        }
                    });

                } catch (JSONException e) {
                    e.printStackTrace();
                }

            }
        };

        getResponse(allVideoInfo_URL + videoId, callback);
    }




    private void getTranscodedFiles(final String videoId) {

        Callback callback = new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {

            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                JSONArray transcodedFiles;
                try {
                    transcodedFiles = new JSONArray(response.body().string());
                    for (int i = 0; i < transcodedFiles.length(); i++) {
                        JSONObject transcodedFileObj = transcodedFiles.getJSONObject(i);

                        if (transcodedFileObj.optString("container").contains("mp4")) {
                            switch (transcodedFileObj.optString("resolution")) {
                                case "240p":
                                    videoFile.resolutions.put(VideoFile.QUALITY_240P ,transcodedFileObj.optString("videoUrl"));
                                    break;
                                case "360p":
                                    videoFile.resolutions.put(VideoFile.QUALITY_360P ,transcodedFileObj.optString("videoUrl"));
                                    break;
                                case "480p":
                                    videoFile.resolutions.put(VideoFile.QUALITY_480P ,transcodedFileObj.optString("videoUrl"));
                                    break;
                                case "720p":
                                    videoFile.resolutions.put(VideoFile.QUALITY_720P ,transcodedFileObj.optString("videoUrl"));
                                    break;
                            }
                        }
                    }   // finished setting transcoded files


                    getSrtLink(videoId);


                } catch (JSONException e) {
                    e.printStackTrace();
                }


            }
        };

        getResponse(transcodedFilesPath_url + videoId, callback);
    }



    public void getResponse(String url, Callback callback) {
        Request request = new Request.Builder()
                .url(url)
                .build();

        OkHttpClient client = new OkHttpClient();
        client.newCall(request).enqueue(callback);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // Respond to the action bar's Up/Home button
            case android.R.id.home:
                NavUtils.navigateUpFromSameTask(this);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
