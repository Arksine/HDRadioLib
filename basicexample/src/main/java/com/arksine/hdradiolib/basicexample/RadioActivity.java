package com.arksine.hdradiolib.basicexample;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.arksine.hdradiolib.*;
import com.arksine.hdradiolib.BuildConfig;
import com.arksine.hdradiolib.enums.RadioBand;
import com.arksine.hdradiolib.enums.RadioCommand;
import com.arksine.hdradiolib.enums.RadioError;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import timber.log.Timber;

public class RadioActivity extends AppCompatActivity {

    // Change this to test different drivers
    private static final HDRadio.DriverType DRIVER = HDRadio.DriverType.ARDUINO_DRIVER;

    // Radio Library Members
    private HDRadio mHdRadio;
    private volatile RadioController mController = null;
    private Handler mUiHandler;

    // Shared Preferences to persist values
    private SharedPreferences mRadioActivityPrefs;

    // Executor Service to manage threads
    private ExecutorService EXECUTOR = Executors.newCachedThreadPool(new BackgroundThreadFactory());

    // Local tracking vars.  These can be obtained by getters in the RadioController, but its
    // faster/easier to keep a copy of frequently accessed variables in the activity.
    private AtomicBoolean mIsConnected = new AtomicBoolean(false);
    private AtomicBoolean mIsPoweredOn = new AtomicBoolean(false);
    private AtomicBoolean mHdActive = new AtomicBoolean(false);
    private AtomicBoolean mIsExiting = new AtomicBoolean(false);
    private volatile int mCurrentFrequency = 879;
    private volatile RadioBand mCurrentBand = RadioBand.FM;

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

        if (BuildConfig.DEBUG) {
            Timber.plant(new Timber.DebugTree());
        }

        buildRadioInstance();
        mUiHandler = new Handler(Looper.getMainLooper());
        mRadioActivityPrefs = this.getSharedPreferences("radio_activity_preferences",
                Context.MODE_PRIVATE);

        initViews();
        mTextSwapAnimator = new TextSwapAnimator(mRadioInfoText);
        mClearViewsRunnable.run();

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
        exit();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mHdRadio.open();
    }

    @Override
    protected void onStop() {
        super.onStop();
        exit();
    }

    private void exit() {
        mIsExiting.set(true);
        if (mHdRadio != null && mHdRadio.isOpen()) {
            mHdRadio.close();
        }
    }

    /**
     * Setup HdRadio instance with the appropriate Callbacks
     */
    private void buildRadioInstance() {
        HDRadioEvents callbacks = new HDRadioEvents() {
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
                    // Check the serial number
                    String id = mHdRadio.getDeviceId();
                    Timber.i("Radio Opened successfully, device id: %s", id);

                    // Get the controller interface
                    mController = controller;
                    mIsConnected.set(true);

                    // enable the power button
                    togglePowerButton(true);

                    // set seekall button
                    RadioActivity.this.mUiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mSeekAllButton.setChecked(mController.getSeekAll());
                        }
                    });
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
                togglePowerButton(true);
            }

            @Override
            public void onRadioPowerOff() {
                mIsPoweredOn.set(false);

                // enable the power button
                togglePowerButton(true);
                runOnUiThread(mClearViewsRunnable);
            }

            @Override
            public void onRadioMute(final boolean muteStatus) {
                RadioActivity.this.mUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mMuteButton.setChecked(muteStatus);
                    }
                });

            }

            @Override
            public void onRadioSignalStrength(final int signalStrength) {
                // Update Signal strength meter
            }

            @Override
            public void onRadioTune(final TuneInfo tuneInfo) {
                mCurrentFrequency = tuneInfo.getFrequency();
                mCurrentBand = tuneInfo.getBand();
                mHdActive.set(false);

                // Format the string depending on FM or AM
                final String tuneStr = (mCurrentBand == RadioBand.FM) ?
                        String.format(Locale.US, "%1$.1f FM", (float) mCurrentFrequency / 10) :
                        String.format(Locale.US, "%1$d AM", mCurrentFrequency);
                final boolean bandStatus = (mCurrentBand == RadioBand.FM);

                RadioActivity.this.mUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mBandButton.setChecked(bandStatus);
                        mTextSwapAnimator.setTextItem(RadioCommand.TUNE, tuneStr);
                        mTextSwapAnimator.resetAnimator();
                        mRadioStatusText.setText("");
                        mRadioFreqText.setText(tuneStr);
                    }
                });

            }

            @Override
            public void onRadioSeek(final TuneInfo seekInfo) {

                // Format the string depending on FM or AM
                final String seekStr = (seekInfo.getBand() == RadioBand.FM) ?
                        String.format(Locale.US, "%1$.1f FM", seekInfo.getFrequency() / 10f) :
                        String.format(Locale.US, "%1$d AM", seekInfo.getFrequency());
                RadioActivity.this.mUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mRadioFreqText.setText(seekStr);
                    }
                });
            }

            @Override
            public void onRadioHdActive(final boolean hdActive) {
                // TODO: Show HD Icon if true, hide if false, the textview below is temporary
                mHdActive.set(hdActive);

                RadioActivity.this.mUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (hdActive) {
                            mRadioStatusText.setText("HD");
                        } else {
                            mRadioStatusText.setText("");
                        }
                    }
                });

            }

            @Override
            public void onRadioHdStreamLock(final boolean hdStreamLock) {
                // Probably don't need to do anything here
            }

            @Override
            public void onRadioHdSignalStrength(final int hdSignalStrength) {
                // Update signal bar
            }

            @Override
            public void onRadioHdSubchannel(final int subchannel) {
                if (mHdActive.get() && subchannel > 0) {
                    // HD is active and the subchannel is valid

                    // Format the HD string
                    final String hdStr = (mCurrentBand == RadioBand.FM) ?
                            String.format(Locale.US, "%1$.1f FM HD%2$d",
                                    (float)mCurrentFrequency/10, subchannel) :
                            String.format(Locale.US, "%1$d AM HD%2$d",
                                    mCurrentFrequency, subchannel);

                    RadioActivity.this.mUiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mRadioFreqText.setText(hdStr);
                            mTextSwapAnimator.setTextItem(RadioCommand.TUNE, hdStr);

                            // Update info text with artist and title from current subchannel
                            mTextSwapAnimator.setTextItem(RadioCommand.HD_TITLE,
                                    mController.getHdTitle());
                            mTextSwapAnimator.setTextItem(RadioCommand.HD_ARTIST,
                                    mController.getHdArtist());
                        }
                    });


                } else {
                    // HD is not active, or the subchannel is not valid

                    // Format standard string
                    final String stdStr = (mCurrentBand == RadioBand.FM) ?
                            String.format(Locale.US, "%1$.1f FM", (float) mCurrentFrequency / 10) :
                            String.format(Locale.US, "%1$d AM", mCurrentFrequency);

                    RadioActivity.this.mUiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mRadioFreqText.setText(stdStr);
                            mTextSwapAnimator.setTextItem(RadioCommand.TUNE, stdStr);
                        }
                    });
                }
            }

            @Override
            public void onRadioHdSubchannelCount(final int subchannelCount) {
                // Dont need to do anything here
            }

            @Override
            public void onRadioHdTitle(final HDSongInfo hdTitle) {
                //  request current subchannel if we don't have a subchannel set
                if (mController.getHdSubchannel() < 1) {
                    mController.requestUpdate(RadioCommand.HD_SUBCHANNEL);
                }

                RadioActivity.this.mUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mTextSwapAnimator.setTextItem(RadioCommand.HD_TITLE, hdTitle.getInfo());
                    }
                });

            }

            @Override
            public void onRadioHdArtist(final HDSongInfo hdArtist) {
                //  request current subchannel if we don't have a subchannel set
                if (mController.getHdSubchannel() < 1) {
                    mController.requestUpdate(RadioCommand.HD_SUBCHANNEL);
                }

                RadioActivity.this.mUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mTextSwapAnimator.setTextItem(RadioCommand.HD_ARTIST, hdArtist.getInfo());
                    }
                });
            }

            @Override
            public void onRadioHdCallsign(final String hdCallsign) {
                RadioActivity.this.mUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mTextSwapAnimator.setTextItem(RadioCommand.HD_CALLSIGN, hdCallsign);
                    }
                });

            }

            @Override
            public void onRadioHdStationName(final String hdStationName) {
                // TODO: this is a String, I should probably do something with this
            }

            @Override
            public void onRadioRdsEnabled(final boolean rdsEnabled) {
                RadioActivity.this.mUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (rdsEnabled) {
                            mRadioStatusText.setText("RDS");
                        } else {
                            mRadioStatusText.setText("");
                        }
                    }
                });

            }

            @Override
            public void onRadioRdsGenre(final String rdsGenre) {
                RadioActivity.this.mUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mTextSwapAnimator.setTextItem(RadioCommand.RDS_GENRE, rdsGenre);
                    }
                });
            }

            @Override
            public void onRadioRdsProgramService(final String rdsProgramService) {
                // TODO: should probably stream this
            }

            @Override
            public void onRadioRdsRadioText(final String rdsRadioText) {
                RadioActivity.this.mUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mTextSwapAnimator.setTextItem(RadioCommand.RDS_RADIO_TEXT, rdsRadioText);
                    }
                });
            }

            @Override
            public void onRadioVolume(final int volume) {
                RadioActivity.this.mUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mVolumeSeekbar.setProgress(volume);
                    }
                });
            }

            @Override
            public void onRadioBass(final int bass) {
                // update volume seekbar
            }

            @Override
            public void onRadioTreble(final int treble) {
                // update treble seekbar
            }

            @Override
            public void onRadioCompression(final int compression) {
                // update compression
            }
        };

        mHdRadio = new HDRadio(this, callbacks, DRIVER);
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

                    Timber.d("Set power: %b", status);
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

                    Timber.d("Set mute:  %b", status);
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
                    Timber.d("Switch Band to: %s", band.toString());

                    if (band == RadioBand.FM) {
                        // FM

                        // Get persisted previous FM channel
                        final int frequency = mRadioActivityPrefs.getInt("pref_key_stored_fm_freq", 879);
                        final int subChannel = mRadioActivityPrefs.getInt("pref_key_stored_fm_subch", 0);

                        // Persist current AM channel
                        int prevFreq = mController.getTune().getFrequency();
                        int prevSubCh = mHdActive.get() ? mController.getHdSubchannel() : 0;
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
                        int prevFreq = mController.getTune().getFrequency();
                        int prevSubCh = mHdActive.get() ? mController.getHdSubchannel() : 0;

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
                            int curSc = mController.getHdSubchannel();
                            int count = mController.getHdSubchannelCount();

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

                            int curSc = mController.getHdSubchannel();
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
                    Timber.v("View Tree Observer set scrollview width to : %d",
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
            Timber.v("Display Metrics set width to:  %d", scrollViewWidth);
        }
    }

    // Allows the power button to be toggled outside of the UI thread
    private void togglePowerButton(final boolean status) {
        this.mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                mPowerButton.setEnabled(status);
                mPowerButton.setClickable(status);
            }
        });
    }

    // Runnables

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
            mHdActive.set(false);
        }
    };



}
