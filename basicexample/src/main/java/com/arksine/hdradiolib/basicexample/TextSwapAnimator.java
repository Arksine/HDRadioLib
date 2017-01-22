package com.arksine.hdradiolib.basicexample;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.graphics.Paint;
import android.os.Handler;
import android.util.Log;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.TextView;

import com.arksine.hdradiolib.enums.RadioCommand;

// TODO: Have two modes, one for HD Radio/Regular RDS, and one for Streaming RDS.  Wouldn't be
//       bad if the streaming RDS version parsed words between current and previous

/**
 * Animates (Swaps) between a list of strings in a textview.  If the string is longer than the container,
 * it will scroll as long as the textview is contained in a scrollview
 */

public class TextSwapAnimator {
    private static final String TAG = TextSwapAnimator.class.getSimpleName();

    private Handler mAnimationHandler;
    private final SparseArray<String> mInfoItems = new SparseArray<>(4);
    private TextView mTextView;
    private String mCurrentString;
    private int mScrollViewWidth;

    private int mIndex = 0;
    private int mCapacity = 1;          // should always have atleast
    private int mFadeDuration;
    private int mDelayDuration;
    private int mDisplayDuration;
    private int mScrollDuration;
    private boolean mAnimationStarted = false;
    private boolean mWillScroll = false;

    private ObjectAnimator mFadeInAnimation;
    private ObjectAnimator mFadeOutAnimation;
    private ObjectAnimator mTextScrollAnimation;

    public TextSwapAnimator(TextView infoTextView) {
        this(infoTextView, 2000, 1000, 3000);

    }

    public TextSwapAnimator(TextView infoTextView, int fadeDuration, int delayBetweenItems,
                            int displayDuration) {

        mAnimationHandler = new Handler();
        mTextView = infoTextView;
        mFadeDuration = fadeDuration;
        mDelayDuration = delayBetweenItems;
        mDisplayDuration = displayDuration;
        mScrollDuration = 5000;  // This should be set programatically
        mScrollViewWidth = 1000;  // just a default width that should change

        initializeArray();
        initAnimation();
    }

    public void setScrollViewWidth(int width) {
        mScrollViewWidth = width;
    }

    private void initializeArray() {
        mInfoItems.put(0, "87.9 FM");
        mInfoItems.put(1, "");
        mInfoItems.put(2, "");
        mInfoItems.put(3, "");
    }

    private void initAnimation() {
        mTextView.setGravity(Gravity.CENTER);

        mFadeInAnimation = ObjectAnimator.ofFloat(mTextView, "alpha", 0f, 1f);
        mFadeInAnimation.setDuration(mFadeDuration);
        mFadeOutAnimation = ObjectAnimator.ofFloat(mTextView, "alpha", 1f, 0f);
        mFadeOutAnimation.setDuration(mFadeDuration);
        mTextScrollAnimation = ObjectAnimator.ofFloat(mTextView, "translationX", 0f, 0f);
        mTextScrollAnimation.setInterpolator(new LinearInterpolator());
        mTextScrollAnimation.setDuration(mScrollDuration);

        mFadeInAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                super.onAnimationStart(animation);
                mTextView.setTranslationX(0f);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                if (mAnimationStarted) {
                    if (mWillScroll) {
                        mTextScrollAnimation.start();
                    } else {
                        // Keep the text view visible for the display duration
                        mAnimationHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                mFadeOutAnimation.start();
                            }
                        }, mDisplayDuration);

                    }
                }
            }
        });

        mFadeOutAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                super.onAnimationStart(animation);

            }

            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                if (mAnimationStarted) {
                    mIndex++;

                    while (mIndex < mCapacity) {
                        if (!(mInfoItems.get(mIndex).equals(""))) {
                            break;
                        }
                        mIndex++;
                    }

                    // If we iterate to the end of the list, reset the index to 0 (which is never null)
                    if (mIndex >= mCapacity) {
                        mIndex = 0;
                    }

                    mCurrentString = mInfoItems.get(mIndex);
                    mTextView.setText(mCurrentString);
                    setupScrollAnimation();

                    mFadeInAnimation.start();
                }
            }
        });

        mTextScrollAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                super.onAnimationStart(animation);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                if (mAnimationStarted) {
                    mAnimationHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            mFadeOutAnimation.start();
                        }
                    }, mDisplayDuration);
                }
            }
        });
    }

    private void setupScrollAnimation() {
        Paint textPaint = mTextView.getPaint();
        String text = mTextView.getText().toString();
        int textWidth = Math.round(textPaint.measureText(text));
        ViewGroup.LayoutParams params = mTextView.getLayoutParams();

        if (mScrollViewWidth >= textWidth) {
            // text width is smaller than the view size, no need to animate
            params.width = mScrollViewWidth;
            mTextView.setLayoutParams(params);
            mWillScroll = false;
        } else {

            params.width = textWidth;
            mTextView.setLayoutParams(params);
            mWillScroll = true;
            float xTranslate = textWidth - mScrollViewWidth;
            mTextScrollAnimation.setFloatValues(0f, -xTranslate);
            mTextScrollAnimation.setDuration((int) xTranslate * 5);   // 5 ms per pixel

        }
    }

    public void setTextItem(RadioCommand command, String item) {
        if (item == null) {
            return;
        }

        int idx;
        switch (command) {
            case TUNE:
                idx = 0;
                break;
            case HD_TITLE:
                idx = 1;
                break;
            case HD_ARTIST:
                idx = 2;
                break;
            case HD_CALLSIGN:
                idx = 3;
                break;
            case RDS_RADIO_TEXT:
                idx = 1;
                break;
            case RDS_GENRE:
                idx = 2;
                break;
            case RDS_PROGRAM_SERVICE:
                idx = 3;
                // TODO: This is too much text.  We need to remove this and rethink how to display RDS text
                item = mInfoItems.get(idx) + item;  // we append the item to the current string for program service
                break;
            default:
                Log.i(TAG, "Invalid Command Key");
                return;
        }

        if (mInfoItems.get(idx, "").equals("") && !item.equals("")) {
            mCapacity++;
        }

        mInfoItems.put(idx, item);

        if (mCapacity > 1 && !mAnimationStarted) {
            startAnimation();
        }
    }

    public void clearTextItem(RadioCommand command) {

        int idx;

        switch (command) {
            case TUNE:
                Log.i(TAG, "Frequency should not be cleared");
                return;
            case HD_TITLE:
                idx = 1;
                break;
            case HD_ARTIST:
                idx = 2;
                break;
            case HD_CALLSIGN:
                idx = 3;
                break;
            case RDS_RADIO_TEXT:
                idx = 1;
                break;
            case RDS_GENRE:
                idx = 2;
                break;
            case RDS_PROGRAM_SERVICE:
                idx = 3;
                break;
            default:
                Log.i(TAG, "Invalid Command Key");
                return;
        }

        if (!(mInfoItems.get(idx).equals(""))) {
            mInfoItems.put(idx, "");
            mCapacity--;

            if (mCapacity <= 1) {
                stopAnimation();
            }
        }
    }

    public void startAnimation() {
        if (mCapacity > 1) {
            mAnimationStarted = true;
            mCurrentString = mInfoItems.get(mIndex);
            mTextView.setText(mCurrentString);
            mFadeOutAnimation.start();
        } else {
            mCurrentString = mInfoItems.get(0);
            mTextView.setText(mCurrentString);
        }
    }

    public void stopAnimation() {
        mAnimationHandler.removeCallbacksAndMessages(null);
        mAnimationStarted = false;
        mFadeOutAnimation.cancel();
        mFadeInAnimation.cancel();
        mTextScrollAnimation.cancel();
        mTextView.setAlpha(1f);
        mTextView.setTranslationX(0f);
    }

    public void resetAnimator() {
        stopAnimation();
        mIndex = 0;
        mCapacity = 1;
        mInfoItems.put(1, "");
        mInfoItems.put(2, "");
        mInfoItems.put(3, "");
        mCurrentString = mInfoItems.get(0);
        mTextView.setText(mCurrentString);
        ViewGroup.LayoutParams params = mTextView.getLayoutParams();
        params.width = mScrollViewWidth;
        mTextView.setLayoutParams(params);
    }
}
