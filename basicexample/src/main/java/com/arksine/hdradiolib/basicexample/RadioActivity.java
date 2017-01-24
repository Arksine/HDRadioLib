package com.arksine.hdradiolib.basicexample;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.arksine.hdradiolib.BackgroundThreadFactory;
import com.arksine.hdradiolib.HDRadio;
import com.arksine.hdradiolib.HDRadioCallbacks;
import com.arksine.hdradiolib.HDSongInfo;
import com.arksine.hdradiolib.RadioController;
import com.arksine.hdradiolib.TuneInfo;
import com.arksine.hdradiolib.enums.RadioBand;
import com.arksine.hdradiolib.enums.RadioCommand;
import com.arksine.hdradiolib.enums.RadioError;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class RadioActivity extends AppCompatActivity {
    private static final String TAG = RadioActivity.class.getSimpleName();

    // Radio Library Members
    private HDRadio mHdRadio;
    private volatile RadioController mController = null;
    private Handler mUiHandler;

    // Shared Preferences to persist values
    private SharedPreferences mRadioActivityPrefs;

    // Executor Service to manage threads
    private ExecutorService EXECUTOR = Executors.newCachedThreadPool(new BackgroundThreadFactory());

    // Local tracking vars.
    private AtomicBoolean mIsConnected = new AtomicBoolean(false);
    private AtomicBoolean mIsPoweredOn = new AtomicBoolean(false);
    private AtomicBoolean mHdActive = new AtomicBoolean(false);
    private AtomicBoolean mRdsActive = new AtomicBoolean(false);
    private AtomicBoolean mIsRequestingSignal = new AtomicBoolean(false);
    private AtomicBoolean mIsExiting = new AtomicBoolean(false);

    private int mCurrentFrequency = 879;
    private RadioBand mCurrentBand = RadioBand.FM;

    private HDRadioValues mRadioValues;

    // Content Views
    private TextSwapAnimator mTextSwapAnimator;
    private TextView mRadioStatusText;
    private TextView mRadioFreqText;
    private TextView mRadioInfoText;
    private ToggleButton mPowerButton;
    private ToggleButton mMuteButton;
    private ToggleButton mSeekAllButton;
    private ToggleButton mBandButton;
    private SeekBar mVolumeSeekbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_radio);

        buildRadioInstance();
        mRadioValues = new HDRadioValues(this);
        mUiHandler = new Handler(Looper.getMainLooper(), mUiHandlerCallback);
        mRadioActivityPrefs = this.getSharedPreferences("radio_activity_preferences",
                Context.MODE_PRIVATE);

        initViews();
        mTextSwapAnimator = new TextSwapAnimator(mRadioInfoText);
        mClearViewsRunnable.run();

        mHdRadio.open();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Call this to adjust the scrollview size if the window changes
        setupScrollView();
        mTextSwapAnimator.startAnimation();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mTextSwapAnimator.stopAnimation();
    }

    @Override
    protected void onStop() {
        super.onStop();
        exit();
    }

    private void exit() {
        mIsExiting.set(true);
        mRadioValues.savePersistentPrefs(this);
        mIsRequestingSignal.set(false);
        if (mIsConnected.get()) {
            mHdRadio.close();
        }
    }


    /**
     * Setup HdRadio instance with the appropriate Callbacks
     */
    private void buildRadioInstance() {
        HDRadioCallbacks callbacks = new HDRadioCallbacks() {
            @Override
            public void onOpened(boolean openSuccess, RadioController controller) {
                if (!openSuccess) {
                    //send a toast that there was an error opening the device
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(RadioActivity.this, "Error opening Radio Device",
                                    Toast.LENGTH_SHORT).show();
                        }
                    });

                    mIsConnected.set(false);
                } else {
                    // Get the controller interface
                    mController = controller;
                    mIsConnected.set(true);

                    // enable the power button
                    mPowerButton.setEnabled(true);
                    mPowerButton.setClickable(true);
                }

            }

            @Override
            public void onClosed() {
                mIsConnected.set(false);

                if (mIsExiting.get()) {
                    // Close the activity if the user exits
                    RadioActivity.this.finish();
                    return;
                }

                // if powered on, set the power status false and clear UI variables
                if (mIsPoweredOn.compareAndSet(true, false)) {
                    runOnUiThread(mClearViewsRunnable);
                }

                mController = null;
            }

            @Override
            public void onDeviceError(final RadioError error) {

                mIsConnected.set(false);

                // if powered on, set the power status false and clear UI variables
                if (mIsPoweredOn.compareAndSet(true, false)) {
                    runOnUiThread(mClearViewsRunnable);
                }

                // Send a toast
                runOnUiThread(new Runnable() {
                      @Override
                      public void run() {
                          Toast.makeText(RadioActivity.this, "Device Error: " + error.toString(),
                                  Toast.LENGTH_SHORT).show();
                      }
                  });


                mController = null;
            }

            @Override
            public void onRadioPowerOn() {
                mIsPoweredOn.set(true);

                // enable the power button
                mPowerButton.setEnabled(true);
                mPowerButton.setClickable(true);

                runOnUiThread(mInitializeRadio);
            }

            @Override
            public void onRadioPowerOff() {
                mIsPoweredOn.set(false);

                // enable the power button
                mPowerButton.setEnabled(true);
                mPowerButton.setClickable(true);
                runOnUiThread(mClearViewsRunnable);
            }

            @Override
            public void onRadioDataReceived(RadioCommand key, Object value) {
                mRadioValues.setHdValue(key, value);

                // Obtain a message setting the what member as the command's ordinal, and
                // the obj member to the value received
                Message msg = mUiHandler.obtainMessage(key.ordinal(), value);
                mUiHandler.sendMessage(msg);
            }
        };

        mHdRadio = new HDRadio(this, callbacks);
    }

    private void initViews() {
        mRadioStatusText = (TextView)findViewById(R.id.txt_radio_info_status);
        mRadioFreqText = (TextView)findViewById(R.id.txt_radio_frequency);
        mRadioInfoText = (TextView)findViewById(R.id.txt_radio_info);
        mPowerButton = (ToggleButton)findViewById(R.id.btn_radio_power);
        mMuteButton = (ToggleButton)findViewById(R.id.btn_radio_mute);
        mSeekAllButton = (ToggleButton)findViewById(R.id.btn_radio_seekall);
        mBandButton = (ToggleButton)findViewById(R.id.btn_radio_band);
        Button tuneUpButton = (Button) findViewById(R.id.btn_tune_up);
        Button tuneDownButton = (Button) findViewById(R.id.btn_tune_down);
        Button seekUpButton = (Button) findViewById(R.id.btn_seek_up);
        Button seekDownButton = (Button) findViewById(R.id.btn_seek_down);

        mVolumeSeekbar = (SeekBar) findViewById(R.id.seekbar_volume);

        // Set button listeners
        mPowerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if (mController != null) {
                    // Disable the button until the power callback returns
                    mPowerButton.setEnabled(false);
                    mPowerButton.setClickable(false);

                    final boolean status = ((ToggleButton)view).isChecked();

                    Log.d(TAG, "Set power: " + status);
                    if (status) {
                        mController.powerOn();
                    } else {
                        mController.powerOff();
                    }
                }
            }
        });

        mMuteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mController != null) {
                    final boolean status = ((ToggleButton)view).isChecked();

                    Log.d(TAG, "Set mute: " + status);
                    if (status) {
                        mController.muteOn();
                    } else {
                        mController.muteOff();
                    }
                }

            }
        });

        mSeekAllButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mController != null) {
                    final boolean status = ((ToggleButton) view).isChecked();
                    mController.setSeekAll(status);
                }
            }
        });

        mBandButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mController != null) {
                    // If toggle is checked we switch to FM, otherwise AM
                    RadioBand band = ((ToggleButton) view).isChecked() ?
                            RadioBand.FM : RadioBand.AM;
                    Log.d(TAG, "Switch Band to: " + band.toString());

                    if (band == RadioBand.FM) {
                        // FM

                        // Get persisted previous FM channel
                        final int frequency = mRadioActivityPrefs.getInt("pref_key_stored_fm_freq", 879);
                        final int subChannel = mRadioActivityPrefs.getInt("pref_key_stored_fm_subch", 0);

                        // Persist current AM channel
                        int prevFreq = ((TuneInfo)mRadioValues
                                .getHdValue(RadioCommand.TUNE)).getFrequency();
                        int prevSubCh = mHdActive.get() ? (int)mRadioValues
                                .getHdValue(RadioCommand.HD_SUBCHANNEL) : 0;
                        mRadioActivityPrefs.edit().putInt("pref_key_stored_am_freq", prevFreq)
                                .putInt("pref_key_stored_am_subch", prevSubCh)
                                .apply();

                        TuneInfo tuneInfo = new TuneInfo(RadioBand.FM, frequency, subChannel);

                        mController.tune(tuneInfo);
                    } else {
                        // AM

                        // Get persisted previous AM channel
                        final int frequency = mRadioActivityPrefs.getInt("pref_key_stored_am_freq", 900);
                        final int subChannel = mRadioActivityPrefs.getInt("pref_key_stored_am_subch", 0);

                        // Persist current FM channel
                        int prevFreq = ((TuneInfo)mRadioValues
                                .getHdValue(RadioCommand.TUNE)).getFrequency();
                        int prevSubCh = mHdActive.get() ? (int)mRadioValues
                                .getHdValue(RadioCommand.HD_SUBCHANNEL) : 0;

                        mRadioActivityPrefs.edit().putInt("pref_key_stored_fm_freq", prevFreq)
                                .putInt("pref_key_stored_fm_subch", prevSubCh)
                                .apply();

                        TuneInfo tuneInfo = new TuneInfo(RadioBand.AM, frequency, subChannel);
                        mController.tune(tuneInfo);
                    }
                }


            }
        });

        tuneUpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mController != null) {

                    EXECUTOR.execute(new Runnable() {
                        @Override
                        public void run() {
                            int curSc = (int)mRadioValues.getHdValue(RadioCommand.HD_SUBCHANNEL);
                            int count = (int)mRadioValues.getHdValue(RadioCommand.HD_SUBCHANNEL);

                            if (!mHdActive.get() || curSc >= count) {
                                // If not currently tuned to an HD Channel, or the channel is
                                // already at the maximum listed HD Channel, regular tune up
                                mController.tuneUp();
                            } else {
                                curSc++;
                                mController.setHdSubChannel(curSc);
                            }
                        }
                    });
                }
            }
        });

        tuneDownButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mController != null) {
                    EXECUTOR.execute(new Runnable() {
                        @Override
                        public void run() {

                            int curSc = (int)mRadioValues.getHdValue(RadioCommand.HD_SUBCHANNEL);
                            if (curSc < 2) {
                                // tune down to the next channel, as we either arent on an HD channel
                                // or are on channel 1
                                mController.tuneDown();
                            } else {
                                curSc--;
                                mController.setHdSubChannel(curSc);
                            }

                        }
                    });

                }
            }
        });

        seekUpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mController != null) {
                    mController.seekUp();
                }
            }
        });

        seekDownButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mController != null) {
                    mController.seekDown();
                }
            }
        });

        mVolumeSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {}

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (mController != null) {
                    final int vol = seekBar.getProgress();
                    mController.setVolume(vol);
                }
            }
        });
    }

    // Set the scrollview width containing the textswapanimator
    private void setupScrollView() {
        final ScrollView infoScrollView = (ScrollView)mRadioInfoText.getParent();
        ViewTreeObserver viewTreeObserver = infoScrollView.getViewTreeObserver();
        if (viewTreeObserver.isAlive()) {

            viewTreeObserver.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    infoScrollView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    mTextSwapAnimator.setScrollViewWidth(infoScrollView.getWidth());
                    Log.v(TAG, "View Tree Observer set scrollview width to : " +
                            infoScrollView.getWidth());
                }
            });
        } else {
            // use display metrics
            DisplayMetrics displayMetrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
            int padding = Math.round(2 * getResources().getDimension(R.dimen.activity_horizontal_margin));
            int scrollViewWidth = displayMetrics.widthPixels - padding;
            mTextSwapAnimator.setScrollViewWidth(scrollViewWidth);
            Log.v(TAG, "Display Metrics set width to:  " + scrollViewWidth);
        }
    }

    /**
     * UI Handler Callback
     *
     * TODO: In the future I'll probably create individual callbacks for each individual command, so
     * there will be no use a handler callback with a huge switch statement.
     */
    private Handler.Callback mUiHandlerCallback = new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            RadioCommand cmd = RadioCommand.getCommandFromOrdinal(msg.what);
            if (cmd == null) {
                Log.i(TAG, "Invalid command");
                return false;
            }
            Object value = msg.obj;

            switch (cmd) {
                case POWER:
                    // this variable isn't consistent. It is properly received when the radio is
                    // powered on, but no reply is received when the radio is powered off, even when
                    // its powered off by software

                    // TODO: I could attempt to power off without lowering DTR
                    break;
                case MUTE:
                    mMuteButton.setChecked((boolean)value);
                    break;
                case VOLUME:
                    mVolumeSeekbar.setProgress((int)value);
                    break;
                case BASS:
                    // Placeholder
                    break;
                case TREBLE:
                    // Placeholder
                    break;
                case HD_SUBCHANNEL: {
                    String newFreq;
                    if (mHdActive.get() && (int)value > 0) {
                        // HD is active and the subchannel is valid

                        if (mCurrentBand == RadioBand.FM) {
                            // Formats the string: Frequency Band HDx
                            newFreq = String.format(Locale.US, "%1$.1f FM HD%2$d",
                                    (float)mCurrentFrequency/10, (int)value);
                        } else if (mCurrentBand == RadioBand.AM) {
                            newFreq = String.format(Locale.US, "%1$d AM HD%2$d",
                                    mCurrentFrequency, (int)value);
                        } else {
                            // Unknown Band
                            break;
                        }

                        mRadioFreqText.setText(newFreq);
                        mTextSwapAnimator.setTextItem(RadioCommand.TUNE, newFreq);

                        // Update info text with artist and title from current subchannel
                        mTextSwapAnimator.setTextItem(RadioCommand.HD_TITLE,
                                (String)mRadioValues.getHdValue(RadioCommand.HD_TITLE));
                        mTextSwapAnimator.setTextItem(RadioCommand.HD_ARTIST,
                                (String)mRadioValues.getHdValue(RadioCommand.HD_ARTIST));

                    } else {
                        // HD is not active, or the subchannel is not valid
                        if (mCurrentBand == RadioBand.FM) {
                            newFreq = String.format(Locale.US, "%1$.1f FM",
                                    (float) mCurrentFrequency / 10);
                        } else if (mCurrentBand == RadioBand.AM){
                            newFreq = String.format(Locale.US, "%1$d AM", mCurrentFrequency);
                        } else {
                            // unknown band
                            break;
                        }
                        mRadioFreqText.setText(newFreq);
                        mTextSwapAnimator.setTextItem(RadioCommand.TUNE, newFreq);

                    }
                    break;
                }
                case TUNE: {
                    String newFreq;
                    TuneInfo info = (TuneInfo) value;
                    mCurrentFrequency = info.getFrequency();
                    mCurrentBand = info.getBand();
                    if (mCurrentBand == RadioBand.FM) {

                        newFreq = String.format(Locale.US, "%1$.1f FM",
                                (float) mCurrentFrequency / 10);
                        mBandButton.setChecked(true);
                    } else {

                        newFreq = String.format(Locale.US, "%1$d AM", mCurrentFrequency);
                        mBandButton.setChecked(false);
                    }

                    mTextSwapAnimator.setTextItem(RadioCommand.TUNE, newFreq);
                    mTextSwapAnimator.resetAnimator();
                    mRdsActive.set(false);
                    mHdActive.set(false);

                    mRadioStatusText.setText("");
                    mRadioFreqText.setText(newFreq);
                    break;
                }
                case SEEK: {
                    String tmpFreq;
                    TuneInfo seekInfo = (TuneInfo) value;
                    if (seekInfo.getBand() == RadioBand.FM) {
                        tmpFreq = String.format(Locale.US, "%1$.1f FM",
                                seekInfo.getFrequency() / 10f);
                    } else {
                        tmpFreq = String.format(Locale.US, "%1$d AM", mCurrentFrequency);
                    }
                    mRadioFreqText.setText(tmpFreq);
                }
                    break;
                case HD_ACTIVE: {
                    // TODO: Show HD Icon if true, hide if false, the textview below is temporary
                    mHdActive.set((boolean)value);
                    if (mHdActive.get()) {
                        mRadioStatusText.setText("HD");
                    } else {
                        mRadioStatusText.setText("");
                    }
                    break;
                }
                case HD_STREAM_LOCK:
                    // TODO: I should do something with this
                    break;
                case HD_TITLE:
                case HD_ARTIST: {
                    //  request current subchannel if we don't have a subchannel set
                    if ((int) mRadioValues.getHdValue(RadioCommand.HD_SUBCHANNEL) < 1) {
                        mController.requestUpdate(RadioCommand.HD_SUBCHANNEL);
                    }

                    HDSongInfo songInfo = (HDSongInfo)value;
                    mTextSwapAnimator.setTextItem(cmd, songInfo.getInfo());
                    break;
                }
                case HD_CALLSIGN:
                case RDS_RADIO_TEXT:
                case RDS_GENRE:
                    mTextSwapAnimator.setTextItem(cmd, (String)value);
                    break;
                case RDS_PROGRAM_SERVICE:
                    // TODO: should do something with this
                    break;
                case RDS_ENABLED:
                    //TODO: show rds icon if true, hide if false
                    mRdsActive.set((boolean)value);
                    if (mRdsActive.get()) {
                        mRadioStatusText.setText("RDS");
                    } else {
                        mRadioStatusText.setText("");
                    }
                    break;
                case SIGNAL_STRENGTH:

                    break;
                case HD_SIGNAL_STRENGTH:

                    break;
                default:
            }

            return true;
        }
    };


    // Runnables

    /**
     * Sets initial radio vars.  Should only be called after the radio has been
     * powered on.
     *
     * TODO: this functionality should move to the libarary along with HDRadioValues
     */
    private Runnable mInitializeRadio = new Runnable() {
        @Override
        public void run() {
            if (mIsPoweredOn.get() && mController != null) {
                boolean sa = mRadioValues.getSeekAll();
                mController.setSeekAll(sa);
                mSeekAllButton.setChecked(sa);

                mController.setVolume(mVolumeSeekbar.getProgress());

                TuneInfo info = (TuneInfo) mRadioValues.getHdValue(RadioCommand.TUNE);
                mController.tune(info);

                if (!mIsRequestingSignal.get()) {
                    EXECUTOR.execute(mRequestSignalRunnable);
                }
            }
        }
    };

    /**
     * Resets views to default
     */
    private Runnable mClearViewsRunnable = new Runnable() {
        @Override
        public void run() {
            // Clear text views, set infoview frequency to PowerOff
            mTextSwapAnimator.setTextItem(RadioCommand.TUNE, "Power Off");
            mTextSwapAnimator.resetAnimator();
            mRadioStatusText.setText("");
            mRadioFreqText.setText("");
            mRdsActive.set(false);
            mHdActive.set(false);
            mIsRequestingSignal.set(false);
        }
    };

    /**
     * Loops in another thread, requesting signal status every 10 seconds
     */
    private Runnable mRequestSignalRunnable = new Runnable() {
        @Override
        public void run() {
            if (mIsRequestingSignal.get()){
                // Don't execute new runnable if already requesting signal
                return;
            }

            mIsRequestingSignal.set(true);
            while (mController != null && mIsPoweredOn.get() && mIsRequestingSignal.get()) {
                if (mHdActive.get()) {
                    mController.requestUpdate(RadioCommand.HD_SIGNAL_STRENGTH);
                } else {
                    mController.requestUpdate(RadioCommand.SIGNAL_STRENGTH);
                }

                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            mIsRequestingSignal.set(false);
        }
    };

}
