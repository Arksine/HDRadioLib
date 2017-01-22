package com.arksine.hdradiolib.basicexample;

import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.arksine.hdradiolib.HDRadio;
import com.arksine.hdradiolib.HDRadioCallbacks;
import com.arksine.hdradiolib.RadioController;
import com.arksine.hdradiolib.enums.RadioBand;
import com.arksine.hdradiolib.enums.RadioCommand;
import com.arksine.hdradiolib.enums.RadioError;

public class RadioActivity extends AppCompatActivity {

    // Radio Library Members
    private HDRadio mHdRadio;
    private RadioController mController = null;
    private Handler mUiHandler;

    // Local tracking vars
    private boolean mIsConnected = false;
    private boolean mIsPoweredOn = false;
    private int mCurrentFrequency = 879;
    private RadioBand mCurrentBand = RadioBand.FM;

    // Content Views
    private TextSwapAnimator mTextSwapAnimator;
    private TextView mHdStatusText;
    private TextView mRadioFreqText;
    private TextView mRadioBandText;
    private TextView mRadioInfoText;
    private TextView mStreamingInfoText;
    private ToggleButton mPowerButton;
    private ToggleButton mMuteButton;
    private ToggleButton mSeekAllButton;
    private ToggleButton mBandButton;
    private SeekBar mVolumeSeekbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_radio);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }



    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    /**
     * Setup HdRadio instance with the appropriate Callbacks
     */
    private void buildRadioInstance() {
        // TODO: implement callbacks
        HDRadioCallbacks callbacks = new HDRadioCallbacks() {
            @Override
            public void onOpened(boolean openSuccess, RadioController controller) {
                if (!openSuccess) {
                    //send a toast that there was an error opening the device
                    Toast.makeText(RadioActivity.this, "Error opening Radio Device",
                            Toast.LENGTH_SHORT).show();
                    mIsConnected = false;
                } else {
                    // Get the controller interface
                    mController = controller;
                    mIsConnected = true;
                    // TODO: enable power button
                }

            }

            @Override
            public void onClosed() {
                mController = null;
                mIsConnected = false;
                if (mIsPoweredOn) {
                    // TODO: clear UI vars
                }
            }

            @Override
            public void onDeviceError(RadioError error) {
                mController = null;
                mIsConnected = false;
                if (mIsPoweredOn) {
                    // TODO: clear UI vars
                }

                Toast.makeText(RadioActivity.this, "Device Error: " + error.toString(),
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onRadioPowerOn() {
                mIsPoweredOn = true;

                // TODO: set initial radio variables (bass, treble, volume, tune)

                // TODO: Launch thread that requests signal status
            }

            @Override
            public void onRadioPowerOff() {
                // TODO: clear UI vars
            }

            @Override
            public void onRadioDataReceived(RadioCommand key, Object value) {
                // TODO: Store value in HDRadioValues, then send key/value pair
                // to the UI handler
            }
        };

        mHdRadio = new HDRadio(this, callbacks);
    }

    private void initViews() {

    }
 }
