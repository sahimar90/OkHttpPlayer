package io.fomalhaut.player.app.demo;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.NavUtils;
import android.util.Log;
import android.view.MenuItem;
import android.widget.LinearLayout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.Serializable;

import io.fomalhaut.android.okhttpplayer.OkHttpVideoPlayer;
import io.fomalhaut.android.okhttpplayer.Quality;
import io.fomalhaut.android.okhttpplayer.VideoFile;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import static io.fomalhaut.android.okhttpplayer.VideoFile.Resolution.RESOLUTION_480P;
import static io.fomalhaut.android.okhttpplayer.VideoFile.Resolution.RESOLUTION_720P;


public class PlayerActivity extends FragmentActivity implements Serializable {

    private static final String TAG = PlayerActivity.class.getSimpleName();
    String transcodedFilesPath_url = "YOUT_TRANSCODDED_PATHS";
    String allVideoInfo_URL = "YOUR_SINGLE_VIDEO_INFO";
    VideoFile videoFile = new VideoFile();

    LinearLayout mainLL;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_player);

        Log.i(TAG, "onCreate...");
        mainLL = (LinearLayout) findViewById(R.id.mainLL);
        getTranscodedFiles("61832");
    }

    private void startCinemanaPlayer(VideoFile videoFile) {

        Log.i(TAG, "will startCinemanaPlayer...");
        Intent i = new Intent(this, OkHttpVideoPlayer.class);
        i.putExtra(OkHttpVideoPlayer.KEY_VIDEO_FILE, videoFile);
        i.putExtra(OkHttpVideoPlayer.KEY_START_POSITION, 0);

        startActivity(i);
    }

    private void getSrtLink(String videoId) {
        Log.i(TAG, "will getSrtLink...");


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

                    videoFile.wantedResolution = RESOLUTION_480P;
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

        Log.i(TAG, "will getTranscodedFiles...");


        Callback callback = new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {

            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                JSONArray transcodedFiles;
                String responseString = response.body().string();
                try {
                    transcodedFiles = new JSONArray(responseString);
                    for (int i = 0; i < transcodedFiles.length(); i++) {
                        JSONObject transcodedFileObj = transcodedFiles.getJSONObject(i);

                        if (transcodedFileObj.optString("container").contains("mp4")) {
                            String resolutionName = transcodedFileObj.optString("resolution");
                            switch (resolutionName) {
                                case "240p":
                                    Quality quality_240p = new Quality(
                                                    VideoFile.Resolution.RESOLUTION_240P,
                                                    resolutionName,
                                                    transcodedFileObj.optString("videoUrl")
                                                    );
                                    videoFile.qualities.put(VideoFile.Resolution.RESOLUTION_240P, quality_240p);
                                    break;
                                case "360p":
                                    Quality quality_360p = new Quality(
                                            VideoFile.Resolution.RESOLUTION_360P,
                                            resolutionName,
                                            transcodedFileObj.optString("videoUrl")
                                    );
                                    videoFile.qualities.put(VideoFile.Resolution.RESOLUTION_360P, quality_360p);
                                    break;
                                case "480p":
                                    Quality quality_480p = new Quality(
                                            RESOLUTION_480P,
                                            resolutionName,
                                            transcodedFileObj.optString("videoUrl")
                                    );
                                    videoFile.qualities.put(RESOLUTION_480P, quality_480p);

                                case "720p":
                                    Quality quality_720p = new Quality(
                                            RESOLUTION_720P,
                                            resolutionName,
                                            transcodedFileObj.optString("videoUrl")
                                    );
                                    videoFile.qualities.put(RESOLUTION_720P, quality_720p);
                                    break;
                            }
                        }
                    }   // finished setting transcoded files

                    Log.i(TAG, "will getSrtLink...");

                    getSrtLink(videoId);

                } catch (JSONException e) {
                    e.printStackTrace();
                    Log.i(TAG, "responseString: " + responseString);
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
