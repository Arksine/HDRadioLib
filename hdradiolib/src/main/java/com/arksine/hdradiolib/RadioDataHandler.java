package com.arksine.hdradiolib;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.util.Log;

import com.arksine.hdradiolib.enums.RadioBand;
import com.arksine.hdradiolib.enums.RadioCommand;
import com.arksine.hdradiolib.enums.RadioOperation;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Receives bytes of data from the HD Radio and parses it.
 */

public class RadioDataHandler extends Handler {
    private static final String TAG = RadioDataHandler.class.getSimpleName();
    private static final boolean DEBUG = HDRadio.DEBUG;


    // Packet parsing vars
    private ByteBuffer mDataBuffer = ByteBuffer.allocate(256);
    private int mPacketLength = 0;
    private int mPacketCheckSum = 0;
    private boolean mIsEscaped = false;
    private boolean mIsLengthByte = false;
    private boolean mPacketStarted = false;


    private EventHandler mEventHandler;
    private RadioValues mRadioValues;

    /**
     * The interface below is a callback for the main HDRadio class, notifying it when
     * a power on reply was recieved, and also when the radio has been tuned.
     */
    interface DataHandlerEvents {
        void onPowerOnReceived();
        void onTuneReceived();
        void onInitComplete();
    }

    private DataHandlerEvents mDataHandlerEvents;


    RadioDataHandler(@NonNull Looper looper, @NonNull EventHandler eventHandler,
                     @NonNull DataHandlerEvents handlerEvents, RadioValues values) {
        super(looper);
        this.mEventHandler = eventHandler;
        this.mDataHandlerEvents = handlerEvents;
        this.mRadioValues = values;
    }

    @Override
    public void handleMessage(Message msg) {
        parseIncomingBytes((byte[]) msg.obj);
    }

    private void parseIncomingBytes(byte[] incomingBytes) {
        if (DEBUG)
            Log.v(TAG, "Incoming Radio Bytes:\n" + RadioPacketBuilder.bytesToHexString(incomingBytes));

        /**
         * The following is known the following about radio packets:
         * 1st byte is 0xA4, which is the header.
         * 2nd byte is length
         * 0x1B is escape byte, when active 0x1B is escaped as 1B, 0x48 is escaped as 0xA4.  I'm
         * operating under the assumption that all bytes outside of the header are escaped, including
         * the length and checksum
         *
         * The checksum is calculated as the sum of all bytes received (outside of the checksum itself) mod 256
         */

        for (byte b : incomingBytes) {
            if ((b == (byte) 0xA4)) {
                // Header received, start new packet

                if (this.mPacketStarted) {
                    Log.v(TAG, "New header received during previous packet, discarding current packet");
                }

                // Start byte is received and it isn't the length byte or the checksum
                this.mPacketLength = 0;
                this.mDataBuffer.clear();
                this.mPacketStarted = true;
                this.mIsLengthByte = true;
                this.mPacketCheckSum = (b & 0xFF);
                this.mIsEscaped = false;   // just in case a header is read directly after escape byte
            } else if (!this.mPacketStarted) {
                Log.v(TAG, "Byte received without a start header, discarding");
            } else if (b == (byte) 0x1B && !this.mIsEscaped) {
                // Escape byte received
                this.mIsEscaped = true;
            } else {

                if (this.mIsEscaped) {
                    if (DEBUG)
                        Log.v(TAG, "Escaped char: " + String.format("%02X", b));

                    if (b == (byte) 0x48) {
                        // 0x48 is escaped as 0xA4
                        b = (byte) 0xA4;
                    }
                    this.mIsEscaped = false;
                    // Note: 0x1B is escaped as 0x1B, so we don't need to reset the current byte
                }

                if (this.mIsLengthByte) {
                    // Length Byte received
                    this.mIsLengthByte = false;
                    this.mPacketLength = (b & 0xFF);
                    this.mPacketCheckSum += mPacketLength;

                    if (this.mPacketLength == 0) {
                        // Received a header with an empty packet, not sure what to do
                        Log.wtf(TAG, "Packet length received is zero, discard packet");
                        this.mPacketStarted = false;
                    }
                } else if (this.mDataBuffer.position() == this.mPacketLength) {
                    // Checksum byte received

                    if ((this.mPacketCheckSum % 256) == (b & 0xFF)) {
                        // Checksum is valid

                        byte[] data = new byte[this.mPacketLength];
                        this.mDataBuffer.flip();
                        this.mDataBuffer.get(data);
                        this.processRadioPacket(data);
                    } else {
                        Log.v(TAG, "Invalid checksum, discarding packet");
                    }

                    // set packet to false so stray bytes that are no 0xA4 are discarded
                    this.mPacketStarted = false;

                } else {
                    // Byte is part of the data packet
                    this.mDataBuffer.put(b);
                    this.mPacketCheckSum += (b & 0xFF);
                }
            }
        }
    }

    private void processRadioPacket(byte[] radioPacket) {
        /**
         *  Radio Packet Structure:
         * - Bytes 0 and 1 are the message command(IE: tune, power, etc)
         * - Bytes 2 and 3 are the message operation (get, set, reply)
         * - Packets received from the radio should always be replies
         */

        if (DEBUG)
            Log.v(TAG, "Data packet hex:\n" + RadioPacketBuilder.bytesToHexString(radioPacket));

        ByteBuffer msgBuf = ByteBuffer.wrap(radioPacket);
        msgBuf.order(ByteOrder.LITTLE_ENDIAN);
        int messageCmd = msgBuf.getShort();
        int messageOp = msgBuf.getShort();

        if (messageOp != RadioOperation.REPLY.getByteValueAsInt()) {
            Log.i(TAG, "Message is not a reply, discarding");
            return;
        }

        RadioCommand command = RadioCommand.getCommandFromValue(messageCmd);
        if (command == null) {
            Log.w(TAG, "Unknown command, cannot process packet");
            return;
        }

        if (msgBuf.remaining() < 4) {
            Log.e(TAG, "Error, not enough bytes in buffer");
            return;
        }

        if (DEBUG)
            Log.d(TAG, "Received Command: " + command.toString());

        switch (command) {
            case POWER: {
                Boolean power = this.parseBoolean(msgBuf);
                if (power != null) {
                    // If power was off and it is now on, send a power on event
                    if (!this.mRadioValues.mPower.get() && power) {
                        this.mDataHandlerEvents.onPowerOnReceived();
                    }

                    this.mRadioValues.mPower.set(power);
                }
                break;
            }
            case MUTE: {
                Boolean mute = this.parseBoolean(msgBuf);
                if (mute != null) {
                    // Store the variable
                    this.mRadioValues.mMute.set(mute);
                    this.mEventHandler.handleMuteEvent(mute);
                }
                break;
            }
            case SIGNAL_STRENGTH: {
                int signal = this.parseInteger(msgBuf);
                this.mRadioValues.mSignalStrength.set(signal);
                this.mEventHandler.handleSignalStrengthEvent(signal);
                break;
            }
            case TUNE: {
                TuneInfo info = this.parseTuneInfo(msgBuf);
                this.mRadioValues.setTune(info);
                this.mEventHandler.handleTuneEvent(info);
                this.mDataHandlerEvents.onTuneReceived();
                break;
            }
            case SEEK: {
                TuneInfo info = this.parseTuneInfo(msgBuf);
                this.mEventHandler.handleSeekEvent(info);
                break;
            }
            case HD_ACTIVE: {
                Boolean hdactive = this.parseBoolean(msgBuf);
                if (hdactive != null) {
                    this.mRadioValues.mHdActive.set(hdactive);
                    this.mEventHandler.handleHdActiveEvent(hdactive);
                }
                break;
            }
            case HD_STREAM_LOCK: {
                Boolean hdStreamLock = this.parseBoolean(msgBuf);
                if (hdStreamLock != null) {
                    this.mRadioValues.mHdStreamLock.set(hdStreamLock);
                    this.mEventHandler.handleHdStreamLockEvent(hdStreamLock);
                }
                break;
            }
            case HD_SIGNAL_STRENGTH: {
                int hdSignal = this.parseInteger(msgBuf);
                this.mRadioValues.mHdSignalStrength.set(hdSignal);
                this.mEventHandler.handleHdSignalStrengthEvent(hdSignal);
                break;
            }
            case HD_SUBCHANNEL: {
                int subchannel = this.parseInteger(msgBuf);
                this.mRadioValues.setHdSubchannel(subchannel);
                this.mEventHandler.handleHdSubchannelEvent(subchannel);
                break;
            }
            case HD_SUBCHANNEL_COUNT: {
                int count = this.parseInteger(msgBuf);
                this.mRadioValues.mHdSubchannelCount.set(count);
                this.mEventHandler.handleHdSubchannelCountEvent(count);
                break;
            }
            case HD_ENABLE_HD_TUNER: {
                /* TODO: I'm not sure what this does, and it doesn't return a boolean value
                    despite what the HDPCR app shows.  Need to debug the radio to find out. For
                    the time being  I wont  implement
                 */
                int buf = this.parseInteger(msgBuf);
                break;
            }
            case HD_TITLE: {
                HDSongInfo title = this.parseHdSongInfo(msgBuf);
                this.mRadioValues.setHdTitle(title);
                this.mEventHandler.handleHdTitleEvent(title);
                break;
            }
            case HD_ARTIST: {
                HDSongInfo artist = this.parseHdSongInfo(msgBuf);
                this.mRadioValues.setHdArtist(artist);
                this.mEventHandler.handleHdArtistEvent(artist);
                break;
            }
            case HD_CALLSIGN: {
                String callsign = this.parseString(msgBuf);
                this.mRadioValues.mHdCallsign.set(callsign);
                this.mEventHandler.handleHdCallsignEvent(callsign);
                break;
            }
            case HD_STATION_NAME: {
                String stationName = this.parseString(msgBuf);
                this.mRadioValues.mHdStationName.set(stationName);
                this.mEventHandler.handleHdStationNameEvent(stationName);
                break;
            }
            case HD_UNIQUE_ID: {
                String uniqueId = this.parseString(msgBuf);
                this.mRadioValues.mUniqueId.set(uniqueId);
                break;
            }
            case HD_API_VERSION: {
                String apiVersion = this.parseString(msgBuf);
                this.mRadioValues.mApiVersion.set(apiVersion);

                // This is the last variable requested during initialization, so execute onInit Event
                this.mDataHandlerEvents.onInitComplete();
                break;
            }
            case HD_HW_VERSION: {
                String hwVersion = this.parseString(msgBuf);
                this.mRadioValues.mHwVersion.set(hwVersion);
                break;
            }
            case RDS_ENABLED: {
                Boolean rdsEnable = this.parseBoolean(msgBuf);
                if (rdsEnable != null) {
                    this.mRadioValues.mRdsEnabled.set(rdsEnable);
                    this.mEventHandler.handleRdsEnabledEvent(rdsEnable);
                }
                break;
            }
            case RDS_GENRE: {
                String rdsGenre = this.parseString(msgBuf);
                this.mRadioValues.mRdsGenre.set(rdsGenre);
                this.mEventHandler.handleRdsGenreEvent(rdsGenre);
                break;
            }
            case RDS_PROGRAM_SERVICE: {
                String rdsProgramService = this.parseString(msgBuf);
                this.mRadioValues.mRdsProgramService.set(rdsProgramService);
                this.mEventHandler.handleRdsProgramServiceEvent(rdsProgramService);
                break;
            }
            case RDS_RADIO_TEXT: {
                String rdsRadioText = this.parseString(msgBuf);
                this.mRadioValues.mRdsRadioText.set(rdsRadioText);
                this.mEventHandler.handleRdsRadioTextEvent(rdsRadioText);
                break;
            }
            case VOLUME: {
                int volume = this.parseInteger(msgBuf);
                this.mRadioValues.mVolume.set(volume);
                this.mEventHandler.handleVolumeEvent(volume);
                break;
            }
            case BASS: {
                int bass = this.parseInteger(msgBuf);
                this.mRadioValues.mBass.set(bass);
                this.mEventHandler.handleBassEvent(bass);
                break;
            }
            case TREBLE: {
                int treble = this.parseInteger(msgBuf);
                this.mRadioValues.mTreble.set(treble);
                this.mEventHandler.handleTrebleEvent(treble);
                break;
            }
            case COMPRESSION: {
                int compression = this.parseInteger(msgBuf);
                this.mRadioValues.mCompression.set(compression);
                this.mEventHandler.handleCompressionEvent(compression);
                break;
            }
            default:
                Log.i(TAG, "Invalid Command");
        }

        if (msgBuf.remaining() > 0) {
            Log.w(TAG, "Remaining bytes in Data packet after parsing");
        }

    }

    private int parseInteger(ByteBuffer msgBuffer) {
        int value = msgBuffer.getInt();

        if (DEBUG)
            Log.d(TAG, "Value: " + value);
        return value;
    }

    private Boolean parseBoolean(ByteBuffer msgBuffer) {
        // Boolean's are received as 4 bytes, 0 is false 1 is true.
        int boolValue = msgBuffer.getInt();
        boolean status;
        if (boolValue == 1) {
            status = true;
        } else if (boolValue == 0) {
            status = false;
        } else {
            Log.i(TAG, "Invalid boolean value: " + boolValue);
            return null;
        }

        if (DEBUG)
            Log.d(TAG, "Value: " + status);

        return status;
    }

    private String parseString(ByteBuffer msgBuffer) {
        // Get length of the string
        int strLength = msgBuffer.getInt();

        if (strLength != msgBuffer.remaining()) {
            Log.i(TAG, "String Length received does not match remaining bytes in buffer");
            strLength = msgBuffer.remaining();
        }

        String strMsg;
        byte[] stringBytes;
        if (strLength == 0) {
            strMsg = "";
        } else {
            stringBytes = new byte[strLength];
            msgBuffer.get(stringBytes);
            strMsg = new String(stringBytes);
        }

        if (DEBUG)
            Log.d(TAG, "Length: " + strLength + "\nConverted String: \n" + strMsg);

        return strMsg;
    }

    private TuneInfo parseTuneInfo(ByteBuffer msgBuffer) {
        RadioBand band;
        int bandValue = msgBuffer.getInt();     // Get band bytes
        if (bandValue == 0) {
            band = RadioBand.AM;
        } else if (bandValue == 1) {
            band = RadioBand.FM;
        } else {
            Log.wtf(TAG, "Invalid value recieved for band: " + bandValue);
            return null;
        }

        int freqency = msgBuffer.getInt();    // Get frequency bytes

        if (DEBUG)
            Log.d(TAG, "Value: " + freqency + " " + band.toString());

        return new TuneInfo(band, freqency, 0);
    }

    private HDSongInfo parseHdSongInfo(ByteBuffer msgBuffer) {
        int subch = msgBuffer.getInt();
        int infoLength = msgBuffer.getInt();

        if (infoLength != msgBuffer.remaining()) {
            Log.w(TAG, "String Length received does not match remaining bytes in buffer");
            infoLength = msgBuffer.remaining();
        }

        String songInfo;
        byte[] stringBytes;
        if (infoLength == 0) {
            songInfo = "";
        } else {
            stringBytes = new byte[infoLength];
            msgBuffer.get(stringBytes);
            songInfo = new String(stringBytes);
        }

        if (DEBUG)
            Log.d(TAG, "Subchannel: " + subch + " String: " + songInfo);

        return new HDSongInfo(songInfo, subch);
    }

}


