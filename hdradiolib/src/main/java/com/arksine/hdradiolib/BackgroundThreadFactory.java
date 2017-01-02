package com.arksine.hdradiolib;

import android.os.Process;

import java.util.concurrent.ThreadFactory;

/**
 * Thread Factory that simply sets the thread priority to background
 */

public class BackgroundThreadFactory implements ThreadFactory {
    public Thread newThread(Runnable r) {
        Thread thread = new Thread(r);
        thread.setPriority(Process.THREAD_PRIORITY_BACKGROUND);
        return thread;
    }
}
