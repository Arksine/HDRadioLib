package com.arksine.hdradiolib;

import com.arksine.hdradiolib.enums.RadioBand;
import com.arksine.hdradiolib.enums.RadioConstant;

/**
 * Class containing data necessary to build a seek packet.  These objects are immutable.
 */

public class SeekData {
    private final RadioConstant direction;
    private final RadioBand band;
    private final boolean seekAll;

    public SeekData(RadioConstant dir, RadioBand bnd, boolean seek) {
        this.direction = dir;
        this.band = bnd;
        this.seekAll = seek;
    }

    public RadioConstant getDirection() {
        return direction;
    }

    public RadioBand getBand() {
        return band;
    }

    public boolean isSeekAll() {
        return seekAll;
    }
}
