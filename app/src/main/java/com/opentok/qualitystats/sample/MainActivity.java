package com.opentok.qualitystats.sample;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import com.opentok.android.OpentokError;
import com.opentok.android.Publisher;
import com.opentok.android.PublisherKit;
import com.opentok.android.Session;
import com.opentok.android.Stream;
import com.opentok.android.Subscriber;
import com.opentok.android.SubscriberKit;
import com.opentok.android.SubscriberKit.VideoStatsListener;
import com.opentok.android.AudioDeviceManager;
import com.opentok.qualitystats.sample.audio.CustomAudioDevice;

public class MainActivity extends Activity implements Session.SessionListener, PublisherKit.PublisherListener, SubscriberKit.SubscriberListener {

    private static final String LOGTAG = "quality-stats-demo";

    private static final String SESSION_ID = "1_MX40NTc1NzQ2Mn5-MTQ4NzQwNjMwNTg2NX5uTWtyMEJDZVFiMWdWS00zSDRtK0RhOGV-fg";
    private static final String TOKEN = "T1==cGFydG5lcl9pZD00NTc1NzQ2MiZzaWc9NWIzMzRkNmUzYzYyN2M3MjU4NDE5YTBlZWZhN2VhY2E2MjA1NTVhNjpzZXNzaW9uX2lkPTFfTVg0ME5UYzFOelEyTW41LU1UUTROelF3TmpNd05UZzJOWDV1VFd0eU1FSkRaVkZpTVdkV1MwMHpTRFJ0SzBSaE9HVi1mZyZjcmVhdGVfdGltZT0xNDg3NDA2MzU4Jm5vbmNlPTAuMTc3ODgyMDY5ODQ5MTA1NDgmcm9sZT1wdWJsaXNoZXImZXhwaXJlX3RpbWU9MTQ4NzQwOTk1MyZjb25uZWN0aW9uX2RhdGE9dG9rZW4lMjBmb3IlMjBleGFtcGxlJTIwcHJvZ3JhbQ==";
    private static final String APIKEY = "45757462";
    //public static final String SESSION_ID = "2_MX40NTc1NzQ2Mn5-MTQ4NzE1NDAxNDgwNH4yWkRRbHZWS3BEUnZmSzYrWUhMRG4vdTB-fg";
    // Replace with a generated token (from the dashboard or using an OpenTok server SDK)
   // public static final String TOKEN = "T1==cGFydG5lcl9pZD00NTc1NzQ2MiZzaWc9NDM1MjFiYjI0YjM2YTVmZTFhOGEzYzI0Y2E2OWMwOTBkNTQxNTI5NTpzZXNzaW9uX2lkPTJfTVg0ME5UYzFOelEyTW41LU1UUTROekUxTkRBeE5EZ3dOSDR5V2tSUmJIWldTM0JFVW5abVN6WXJXVWhNUkc0dmRUQi1mZyZjcmVhdGVfdGltZT0xNDg3MTU0MDc0Jm5vbmNlPTAuODMwNDg5NjAxNTcwODE3NiZyb2xlPXB1Ymxpc2hlciZleHBpcmVfdGltZT0xNDg3MjQwNDY4JmNvbm5lY3Rpb25fZGF0YT10b2tlbiUyMGZvciUyMHRva2JveA==";
    // Replace with your OpenTok API key
   // public static final String API_KEY = "45757462";
    private static final int TEST_DURATION = 20; //test quality duration in seconds
    private static final int TIME_WINDOW = 3; //3 seconds
    private static final int TIME_VIDEO_TEST = 15; //time interval to check the video quality in seconds


    private Session mSession;
    private Publisher mPublisher;
    private Subscriber mSubscriber;

    private double mVideoPLRatio = 0.0;
    private long mVideoBw = 0;

    private double mAudioPLRatio = 0.0;
    private long mAudioBw = 0;

    private long mPrevVideoPacketsLost = 0;
    private long mPrevVideoPacketsRcvd = 0;
    private double mPrevVideoTimestamp = 0;
    private long mPrevVideoBytes = 0;

    private long mPrevAudioPacketsLost = 0;
    private long mPrevAudioPacketsRcvd = 0;
    private double mPrevAudioTimestamp = 0;
    private long mPrevAudioBytes = 0;

    private long mStartTestTime = 0;

    private boolean audioOnly = false;

    private Handler mHandler = new Handler();

    private ProgressDialog mProgressDialog;
    private AlertDialog dialog;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sessionConnect();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();

        if (mSession != null) {
            mSession.disconnect();
        }
    }

    @Override
    public void onPause(){
        super.onPause();

        if(dialog!= null && dialog.isShowing()){
            dialog.dismiss();
        }
    }

    public void sessionConnect() {
        Log.i(LOGTAG, "Connecting session");
        if (mSession == null) {
            // Add a custom audio device before session initialization
            CustomAudioDevice customAudioDevice = new CustomAudioDevice(
                    MainActivity.this);
            customAudioDevice.setRendererMute(true);
            AudioDeviceManager.setAudioDevice(customAudioDevice);

            mSession = new Session(this, APIKEY, SESSION_ID);
            mSession.setSessionListener(this);

            mProgressDialog = ProgressDialog.show(this, "Checking your available bandwidth", "Please wait");
            mSession.connect(TOKEN);
        }
    }

    @Override
    public void onConnected(Session session) {
        Log.i(LOGTAG, "Session is connected");

        mPublisher = new Publisher(this);
        mPublisher.setPublisherListener(this);
        mPublisher.setAudioFallbackEnabled(false);
        mSession.publish(mPublisher);
    }

    @Override
    public void onDisconnected(Session session) {
        Log.i(LOGTAG, "Session is disconnected");

        mPublisher = null;
        mSubscriber = null;
        mSession = null;

        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            mProgressDialog.dismiss();
        }
    }

    @Override
    public void onError(Session session, OpentokError opentokError) {
        Log.i(LOGTAG, "Session error: " + opentokError.getMessage());
        showAlert("Error", "Session error: " + opentokError.getMessage());
    }

    @Override
    public void onStreamDropped(Session session, Stream stream) {
        Log.i(LOGTAG, "Session onStreamDropped");
    }

    @Override
    public void onStreamReceived(Session session, Stream stream) {
        Log.i(LOGTAG, "Session onStreamReceived");
    }

    @Override
    public void onStreamCreated(PublisherKit publisherKit, Stream stream) {
        Log.i(LOGTAG, "Publisher onStreamCreated");
        if (mSubscriber == null) {
            subscribeToStream(stream);
        }
    }

    @Override
    public void onStreamDestroyed(PublisherKit publisherKit, Stream stream) {
        Log.i(LOGTAG, "Publisher onStreamDestroyed");
        if (mSubscriber == null) {
            unsubscribeFromStream(stream);
        }
    }

    @Override
    public void onError(PublisherKit publisherKit, OpentokError opentokError) {
        Log.i(LOGTAG, "Publisher error: " + opentokError.getMessage());
        showAlert("Error", "Publisher error: " + opentokError.getMessage());
    }

    @Override
    public void onConnected(SubscriberKit subscriberKit) {
        Log.i(LOGTAG, "Subscriber onConnected");
        mHandler.postDelayed(statsRunnable, TEST_DURATION * 1000);
    }

    @Override
    public void onDisconnected(SubscriberKit subscriberKit) {
        Log.i(LOGTAG, "Subscriber onDisconnected");
    }

    @Override
    public void onError(SubscriberKit subscriberKit, OpentokError opentokError) {
        Log.i(LOGTAG, "Subscriber error: " + opentokError.getMessage());
        showAlert("Error", "Subscriber error: " + opentokError.getMessage());
    }

    private void subscribeToStream(Stream stream) {
        mSubscriber = new Subscriber(MainActivity.this, stream);

        mSubscriber.setSubscriberListener(this);
        mSession.subscribe(mSubscriber);
        mSubscriber.setVideoStatsListener(new VideoStatsListener() {

            @Override
            public void onVideoStats(SubscriberKit subscriber,
                                     SubscriberKit.SubscriberVideoStats stats) {

                if (mStartTestTime == 0) {
                    mStartTestTime = System.currentTimeMillis() / 1000;
                }
                checkVideoStats(stats);

                //check quality of the video call after TIME_VIDEO_TEST seconds
                if (((System.currentTimeMillis() / 1000 - mStartTestTime) > TIME_VIDEO_TEST) && !audioOnly) {
                    checkVideoQuality();
                }
            }

        });

        mSubscriber.setAudioStatsListener(new SubscriberKit.AudioStatsListener() {
            @Override
            public void onAudioStats(SubscriberKit subscriber, SubscriberKit.SubscriberAudioStats stats) {

                checkAudioStats(stats);

            }
        });
    }

    private void unsubscribeFromStream(Stream stream) {
        if (mSubscriber.getStream().equals(stream)) {
            mSubscriber = null;
        }
    }

    private void checkVideoStats(SubscriberKit.SubscriberVideoStats stats) {
        double videoTimestamp = stats.timeStamp / 1000;

        //initialize values
        if (mPrevVideoTimestamp == 0) {
            mPrevVideoTimestamp = videoTimestamp;
            mPrevVideoBytes = stats.videoBytesReceived;
        }

        if (videoTimestamp - mPrevVideoTimestamp >= TIME_WINDOW) {
            //calculate video packets lost ratio
            if (mPrevVideoPacketsRcvd != 0) {
                long pl = stats.videoPacketsLost - mPrevVideoPacketsLost;
                long pr = stats.videoPacketsReceived - mPrevVideoPacketsRcvd;
                long pt = pl + pr;

                if (pt > 0) {
                    mVideoPLRatio = (double) pl / (double) pt;
                }
            }

            mPrevVideoPacketsLost = stats.videoPacketsLost;
            mPrevVideoPacketsRcvd = stats.videoPacketsReceived;

            //calculate video bandwidth
            mVideoBw = (long) ((8 * (stats.videoBytesReceived - mPrevVideoBytes)) / (videoTimestamp - mPrevVideoTimestamp));

            mPrevVideoTimestamp = videoTimestamp;
            mPrevVideoBytes = stats.videoBytesReceived;

            Log.i(LOGTAG, "Video bandwidth (bps): " + mVideoBw + " Video Bytes received: " + stats.videoBytesReceived + " Video packet lost: " + stats.videoPacketsLost + " Video packet loss ratio: " + mVideoPLRatio);

        }
    }

    private void checkAudioStats(SubscriberKit.SubscriberAudioStats stats) {
        double audioTimestamp = stats.timeStamp / 1000;

        //initialize values
        if (mPrevAudioTimestamp == 0) {
            mPrevAudioTimestamp = audioTimestamp;
            mPrevAudioBytes = stats.audioBytesReceived;
        }

        if (audioTimestamp - mPrevAudioTimestamp >= TIME_WINDOW) {
            //calculate audio packets lost ratio
            if (mPrevAudioPacketsRcvd != 0) {
                long pl = stats.audioPacketsLost - mPrevAudioPacketsLost;
                long pr = stats.audioPacketsReceived - mPrevAudioPacketsRcvd;
                long pt = pl + pr;

                if (pt > 0) {
                    mAudioPLRatio = (double) pl / (double) pt;
                }
            }
            mPrevAudioPacketsLost = stats.audioPacketsLost;
            mPrevAudioPacketsRcvd = stats.audioPacketsReceived;

            //calculate audio bandwidth
            mAudioBw = (long) ((8 * (stats.audioBytesReceived - mPrevAudioBytes)) / (audioTimestamp - mPrevAudioTimestamp));

            mPrevAudioTimestamp = audioTimestamp;
            mPrevAudioBytes = stats.audioBytesReceived;

            Log.i(LOGTAG, "Audio bandwidth (bps): " + mAudioBw + " Audio Bytes received: " + stats.audioBytesReceived + " Audio packet lost: " + stats.audioPacketsLost + " Audio packet loss ratio: " + mAudioPLRatio);

        }

   }

    private void checkVideoQuality() {
        if (mSession != null) {
            Log.i(LOGTAG, "Check video quality stats data");
            if (mVideoBw < 150000 || mVideoPLRatio > 0.03) {
                //go to audio call to check the quality with video disabled
                showAlert("Voice-only", "Your bandwidth is too low for video");
                mProgressDialog = ProgressDialog.show(this, "Checking your available bandwidth for voice only", "Please wait");
                mPublisher.setPublishVideo(false);
                mSubscriber.setSubscribeToVideo(false);
                mSubscriber.setVideoStatsListener(null);
                audioOnly = true;
            } else {
                //quality is good for video call
                mSession.disconnect();
                showAlert("All good", "You're all set!");
            }
        }
    }

    private void checkAudioQuality() {
        if (mSession != null) {
            Log.i(LOGTAG, "Check audio quality stats data");
            if (mAudioBw < 25000 || mAudioPLRatio > 0.05) {
                showAlert("Not good", "You can't connect successfully");
            } else {
                showAlert("Voice-only", "Your bandwidth is too low for video");
            }
        }
    }

    private void showAlert(String title, String Message) {
        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            mProgressDialog.dismiss();
        }
        dialog = new AlertDialog.Builder(MainActivity.this)
                .setTitle(title)
                .setMessage(Message)
                .setPositiveButton(android.R.string.yes,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,
                                                int which) {
                                try {
                                    finish();
                                } catch (Throwable throwable) {
                                    throwable.printStackTrace();
                                }
                            }
                        }).setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private Runnable statsRunnable = new Runnable() {

        @Override
        public void run() {
            if (mSession != null) {
                checkAudioQuality();
                mSession.disconnect();
            }
        }
    };

}
