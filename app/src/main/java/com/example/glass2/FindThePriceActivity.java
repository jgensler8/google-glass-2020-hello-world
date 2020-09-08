package com.example.glass2;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.FileObserver;
import android.provider.MediaStore;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.TextView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.glass.content.Intents;
import com.google.android.glass.touchpad.Gesture;
import com.google.android.glass.touchpad.GestureDetector;
import com.google.android.glass.touchpad.GestureDetector.BaseListener;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;

// https://developers.google.com/glass/develop/gdk/camera
public class FindThePriceActivity extends Activity implements BaseListener {

    private static final int TAKE_PICTURE_REQUEST = 1;

    private GestureDetector mDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.card);
        mDetector = new GestureDetector(this)
                .setBaseListener(this);
        updatePrice("tap to take picture");
        getPriceOnEbay("asdf");
    }

    @Override
    public boolean onGesture(Gesture gesture) {
        Log.i("FindThePriceActivity", "onGesture");
        switch (gesture) {
            case TAP:
//                updatePrice("test");
                takePicture();
            default:
                return false;
        }
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        return mDetector.onMotionEvent(event);
    }

    private void takePicture() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        startActivityForResult(intent, TAKE_PICTURE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == TAKE_PICTURE_REQUEST && resultCode == RESULT_OK) {
            String thumbnailPath = data.getStringExtra(Intents.EXTRA_THUMBNAIL_FILE_PATH);
            String picturePath = data.getStringExtra(Intents.EXTRA_PICTURE_FILE_PATH);

            waitFileFinishedWriting(picturePath);
            updatePrice("Waiting For Image to Save");
            // TODO: Show the thumbnail to the user while the full picture is being
            // processed.
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    private void waitFileFinishedWriting(final String picturePath) {
        final File pictureFile = new File(picturePath);

        if (pictureFile.exists()) {
            handleFileDone(pictureFile);
        } else {
            // The file does not exist yet. Before starting the file observer, you
            // can update your UI to let the user know that the application is
            // waiting for the picture (for example, by displaying the thumbnail
            // image and a progress indicator).

            final File parentDirectory = pictureFile.getParentFile();
            FileObserver observer = new FileObserver(parentDirectory.getPath(),
                    FileObserver.CLOSE_WRITE | FileObserver.MOVED_TO) {
                @Override
                public void onEvent(final int event, String path) {
                    Log.i("FileObserver", "event: " + event + " path: " + path);

                    // for some reason I am getting 32768 event
                    File affectedFile = new File(parentDirectory, path);

                    if (event == FileObserver.MOVED_TO && affectedFile.equals(pictureFile)) {
                        stopWatching();

                        // Now that the file is ready, recursively call
                        // processPictureWhenReady again (on the UI thread).
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Log.i("runOnUiThread", "recursion!");
                                waitFileFinishedWriting(picturePath);
                            }
                        });
                    }
                }
            };
            observer.startWatching();
        }
    }

    private void handleFileDone(File pictureFile) {
        // The picture is ready; process it.
        Log.i("FindThePriceActivity", "picture exists");
        try {
            Log.i("FindThePriceActivity", "getting file contents");
            String fileContents = getStringFromFile(pictureFile.getPath());
            Log.i("FindThePriceActivity", "getting price on EBay");
            String price = getPriceOnEbay(fileContents);
            Log.i("FindThePriceActivity", "updating UI");
            updatePrice(price);
        } catch (Exception e) {
            Log.i("processPictureWhenReady", e.getLocalizedMessage());
        }
    }

    // https://stackoverflow.com/a/13357785
    private static String convertStreamToString(InputStream is) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line = null;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        reader.close();
        return sb.toString();
    }

    private static String getStringFromFile(String filePath) throws Exception {
        File fl = new File(filePath);
        FileInputStream fin = new FileInputStream(fl);
        String ret = convertStreamToString(fin);
        //Make sure you close all streams.
        fin.close();
        return ret;
    }

    /*
    Get price from picture
     */
    private String getPriceOnEbay(String picture) {
        Log.i("getPriceOnEbay",  "len: " + picture.length());
        // turn string to base64

        // send request to ebay
        try {
            RequestQueue queue = Volley.newRequestQueue(this);
            String url ="https://www.google.com";

            // Request a string response from the provided URL.
            StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                    new Response.Listener<String>() {
                        @Override
                        public void onResponse(String response) {
                            // Display the first 500 characters of the response string.
                            Log.i("Volley", "Response is: "+ response.substring(0,500));
                        }
                    }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.i("Volley", error.getMessage());
                }
            });

            // Add the request to the RequestQueue.
            queue.add(stringRequest);
            return "added to queue";
        } catch (Exception e) {
            Log.e("getPriceOnEbay", e.getLocalizedMessage());
            e.printStackTrace();
            return "error";
        }
//        return "" + picture.length();
    }

    /*
    Show price on screen
     */
    private void updatePrice(String price) {
        TextView textView = (TextView) findViewById(R.id.textView);
        textView.setText("Price: " + price);
    }
}
