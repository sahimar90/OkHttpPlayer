package org.cinemana.player.app.demo;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.NavUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
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

    private CinemanaVideoPlayer cinemanaVideoPlayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_player);

        mainLL = (LinearLayout) findViewById(R.id.mainLL);
        getTranscodedFiles("10847");

    }



    private void startCinemanaPlayer(FrameLayout playerFL, VideoFile videoFile) {
        cinemanaVideoPlayer = new CinemanaVideoPlayer(this, playerFL, videoFile);

        cinemanaVideoPlayer.addViewControl(getFullscreenIV());

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

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            FrameLayout root = (FrameLayout) findViewById(R.id.root_view);
                            startCinemanaPlayer(root, videoFile);
                        }
                    });

                } catch (JSONException e) {
                    e.printStackTrace();

                    arTranslationFilePath = "";
                }



            }
        };

        getResponse(allVideoInfo_URL + videoId, callback);

    }



    private ImageView getFullscreenIV() {
        LayoutInflater inflater;

        inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        ImageView imageView = (ImageView) inflater.inflate(R.layout.fullscreen_imageview, null);


        imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE)
                    switchPortrait();
                else switchLandscape();
            }
        });

        return imageView;
    }

    private void switchLandscape() {
        Log.i(TAG, "switching to landscape mode");
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

//        videoInfoLL.setVisibility(View.GONE);
        mainLL.setWeightSum(1);

        //set icon is full screen
    }

    private void switchPortrait() {
        Log.i(TAG, "switching to portrait mode");

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

//        videoInfoLL.setVisibility(View.VISIBLE);
        mainLL.setWeightSum(2);

    }


    private void getTranscodedFiles(final String videoId) {

        Callback callback = new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {

            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                JSONArray transcodedFiles = null;
                if (this != null) {
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

    //Call the three lifecycle methods

    @Override
    protected void onPause() {
        super.onPause();
        if (cinemanaVideoPlayer != null) cinemanaVideoPlayer.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cinemanaVideoPlayer != null) cinemanaVideoPlayer.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (cinemanaVideoPlayer != null) cinemanaVideoPlayer.onResume();
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
