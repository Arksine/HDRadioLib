package com.arksine.hdradiolib;

import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;

import com.arksine.hdradiolib.enums.RadioError;

/**
 * Handles Events received from the radio.  Pertinent data is stored to the RadioValues members,
 * then the appopriate callback is posted to a handler/message queue.
 */

class EventHandler extends  Handler {

    private HDRadioEvents mCallbacks;

    EventHandler(@NonNull HDRadioEvents callbacks, @NonNull Looper looper) {
        super(looper);
        this.mCallbacks = callbacks;
    }

    void handleOpenedEvent(final boolean success, final RadioController controller) {
        this.post(new Runnable() {
            @Override
            public void run() {
                EventHandler.this.mCallbacks.onOpened(success, controller);
            }
        });
    }

    void handleClosedEvent() {
        this.post(new Runnable() {
            @Override
            public void run() {
                EventHandler.this.mCallbacks.onClosed();
            }
        });
    }

    void handleDeviceErrorEvent(final RadioError error) {
        this.post(new Runnable() {
            @Override
            public void run() {
                EventHandler.this.mCallbacks.onDeviceError(error);
            }
        });
    }

    // Hard power on
    void handlePowerOnEvent() {
        this.post(new Runnable() {
            @Override
            public void run() {
                EventHandler.this.mCallbacks.onRadioPowerOn();
            }
        });
    }

    // Hard power off
    void handlePowerOffEvent() {
        this.post(new Runnable() {
            @Override
            public void run() {
                EventHandler.this.mCallbacks.onRadioPowerOff();
            }
        });
    }

    void handleMuteEvent(final boolean status) {
        this.post(new Runnable() {
            @Override
            public void run() {
                EventHandler.this.mCallbacks.onRadioMute(status);
            }
        });
    }

    void handleSignalStrengthEvent(final int signal) {
        this.post(new Runnable() {
            @Override
            public void run() {
                EventHandler.this.mCallbacks.onRadioSignalStrength(signal);
            }
        });
    }

    void handleTuneEvent(final TuneInfo tuneInfo) {
        this.post(new Runnable() {
            @Override
            public void run() {
                EventHandler.this.mCallbacks.onRadioTune(tuneInfo);
            }
        });
    }

    void handleSeekEvent(final TuneInfo seekInfo) {
        this.post(new Runnable() {
            @Override
            public void run() {
                EventHandler.this.mCallbacks.onRadioSeek(seekInfo);
            }
        });
    }

    void handleHdActiveEvent(final boolean hdActive) {
        this.post(new Runnable() {
            @Override
            public void run() {
                EventHandler.this.mCallbacks.onRadioHdActive(hdActive);
            }
        });
    }

    void handleHdStreamLockEvent(final boolean hdStreamLock) {
        this.post(new Runnable() {
            @Override
            public void run() {
                EventHandler.this.mCallbacks.onRadioHdStreamLock(hdStreamLock);
            }
        });
    }

    void handleHdSignalStrengthEvent(final int hdSignal) {
        this.post(new Runnable() {
            @Override
            public void run() {
                EventHandler.this.mCallbacks.onRadioHdSignalStrength(hdSignal);
            }
        });
    }

    void handleHdSubchannelEvent(final int subchannel) {
        this.post(new Runnable() {
            @Override
            public void run() {
                EventHandler.this.mCallbacks.onRadioHdSubchannel(subchannel);
            }
        });
    }

    void handleHdSubchannelCountEvent(final int count) {
        this.post(new Runnable() {
            @Override
            public void run() {
                EventHandler.this.mCallbacks.onRadioHdSubchannelCount(count);
            }
        });
    }

    void handleHdEnableTunerEvent(final boolean enabled) {
        // Since we dont know what this does, it no callback is executed.
    }


    void handleHdTitleEvent(final HDSongInfo hdTitle) {
        this.post(new Runnable() {
            @Override
            public void run() {
                EventHandler.this.mCallbacks.onRadioHdTitle(hdTitle);
            }
        });
    }

    void handleHdArtistEvent(final HDSongInfo hdArtist) {
        this.post(new Runnable() {
            @Override
            public void run() {
                EventHandler.this.mCallbacks.onRadioHdArtist(hdArtist);
            }
        });
    }

    void handleHdCallsignEvent(final String callsign) {
        this.post(new Runnable() {
            @Override
            public void run() {
                EventHandler.this.mCallbacks.onRadioHdCallsign(callsign);
            }
        });
    }

    void handleHdStationNameEvent(final String stationName) {
        this.post(new Runnable() {
            @Override
            public void run() {
                EventHandler.this.mCallbacks.onRadioHdStationName(stationName);
            }
        });
    }

    void handleRdsEnabledEvent(final boolean rdsEnabled) {
        this.post(new Runnable() {
            @Override
            public void run() {
                EventHandler.this.mCallbacks.onRadioRdsEnabled(rdsEnabled);
            }
        });
    }

    void handleRdsGenreEvent(final String rdsGenre) {
        this.post(new Runnable() {
            @Override
            public void run() {
                EventHandler.this.mCallbacks.onRadioRdsGenre(rdsGenre);
            }
        });
    }

    void handleRdsProgramServiceEvent(final String rdsProgram) {
        this.post(new Runnable() {
            @Override
            public void run() {
                EventHandler.this.mCallbacks.onRadioRdsProgramService(rdsProgram);
            }
        });
    }

    void handleRdsRadioTextEvent(final String rdsRadioText) {
        this.post(new Runnable() {
            @Override
            public void run() {
                EventHandler.this.mCallbacks.onRadioRdsRadioText(rdsRadioText);
            }
        });
    }

    void handleVolumeEvent(final int volume) {
        this.post(new Runnable() {
            @Override
            public void run() {
                EventHandler.this.mCallbacks.onRadioVolume(volume);
            }
        });
    }

    void handleBassEvent(final int bass) {
        this.post(new Runnable() {
            @Override
            public void run() {
                EventHandler.this.mCallbacks.onRadioBass(bass);
            }
        });
    }

    void handleTrebleEvent(final int treble) {
        this.post(new Runnable() {
            @Override
            public void run() {
                EventHandler.this.mCallbacks.onRadioTreble(treble);
            }
        });
    }

    void handleCompressionEvent(final int compression) {
        this.post(new Runnable() {
            @Override
            public void run() {
                EventHandler.this.mCallbacks.onRadioCompression(compression);
            }
        });
    }
}
