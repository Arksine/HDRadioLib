package com.arksine.hdradiolib.basicexample;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.graphics.Paint;
import android.os.Handler;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.TextView;

import com.arksine.hdradiolib.enums.RadioCommand;

import timber.log.Timber;

// TODO: Have two modes, one for HD Radio/Regular RDS, and one for Streaming RDS.  Wouldn't be
//       bad if the streaming RDS version parsed words between current and previous

/**
 * Animates (Swaps) between a list of strings in a textview.  If the string is longer than the container,
 * it will scroll as long as the textview is contained in a scrollview
 */

public class TextSwapAnimator {

    private static final int DEFAULT_FADE_DURATION = 2000;  // 2 seconds
    private static final int DEFAULT_DELAY_AFTER_FADE = 0;
    private static final int DEFAULT_DISPLAY_DURATON = 3000;
    private static final int DEFAULT_SCROLL_DURATION = 5000;
    private static final int DEFAULT_MAX_WIDTH_MULTIPLIER = 2;
    private static final int MAX_CAPACITY = 4;

    private Handler mAnimationHandler;
    private String[] mInfoItems = new String[MAX_CAPACITY];

    private TextView mTextView;
    private String mCurrentString;
    private int mScrollViewWidth;

    private int mIndex = 0;
    private int mCapacity = 1;
    private int mFadeDuration;
    private int mDelayAfterFade;
    private int mDisplayDuration;
    private int mScrollDuration;
    private boolean mAnimationStarted = false;
    private boolean mWillScroll = false;

    private ObjectAnimator mFadeInAnimation;
    private ObjectAnimator mFadeOutAnimation;
    private ObjectAnimator mTextScrollAnimation;

    public TextSwapAnimator(TextView infoTextView) {
        this(infoTextView, DEFAULT_FADE_DURATION, DEFAULT_DELAY_AFTER_FADE,
                DEFAULT_DISPLAY_DURATON, DEFAULT_SCROLL_DURATION);

    }

    public TextSwapAnimator(TextView infoTextView, int fadeDuration, int delayAfterFade,
                            int displayDuration, int scrollDuration) {

        mAnimationHandler = new Handler();
        mTextView = infoTextView;
        mFadeDuration = fadeDuration;
        mDelayAfterFade = delayAfterFade;
        mDisplayDuration = displayDuration;
        mScrollDuration = scrollDuration;
        mScrollViewWidth = 1000;  // just a default width that should change

        initializeArray();
        initAnimation();
    }

    public void setScrollViewWidth(int width) {
        mScrollViewWidth = width;

        // update the text view
        ViewGroup.LayoutParams params = mTextView.getLayoutParams();
        params.width = mScrollViewWidth;
        mTextView.setLayoutParams(params);
    }

    private void initializeArray() {
        mInfoItems[0] = "87.9 FM";
        mInfoItems[1] = "";
        mInfoItems[2] = "";
        mInfoItems[3] = "";
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

                    while (mIndex < MAX_CAPACITY) {
                        if (!(mInfoItems[mIndex].equals(""))) {
                            break;
                        }
                        mIndex++;
                    }

                    // If we iterate to the end of the list, reset the index to 0 (which is never null)
                    if (mIndex >= MAX_CAPACITY) {
                        mIndex = 0;
                    }

                    mCurrentString = mInfoItems[mIndex];
                    mTextView.setText(mCurrentString);
                    checkViewWidth();

                    if (mDelayAfterFade > 0) {
                        mAnimationHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                mFadeInAnimation.start();
                            }
                        }, mDelayAfterFade);
                    } else {
                        mFadeInAnimation.start();
                    }
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

    private void checkViewWidth() {
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

            if (textWidth >= DEFAULT_MAX_WIDTH_MULTIPLIER * mScrollViewWidth) {
                // chop off 1/4 of the current item's string
            }

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
                item = mInfoItems[idx] + item;  // we append the item to the current string for program service
                break;
            default:
                Timber.i("Invalid Command Key");
                return;
        }

        if (mInfoItems[idx].equals("") && !item.equals("")) {
            mCapacity++;
            Timber.d("Current Animator Capacity: %d", mCapacity);

        }

        mInfoItems[idx] = item;

        if (mCapacity > 1 && !mAnimationStarted) {
            startAnimation();
        }
    }

    public void clearTextItem(RadioCommand command) {

        int idx;

        switch (command) {
            case TUNE:
                Timber.i("Frequency should not be cleared");
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
                Timber.i("Invalid Command Key");
                return;
        }

        if (!(mInfoItems[idx].equals(""))) {
            mInfoItems[idx] = "";
            mCapacity--;

            Timber.d("Current Animator Capacity: %d", mCapacity);


            if (mCapacity <= 1) {
                stopAnimation();
            }
        }
    }

    public void startAnimation() {
        if (mCapacity > 1) {
            mAnimationStarted = true;
            mCurrentString = mInfoItems[mIndex];
            mTextView.setText(mCurrentString);
            mFadeOutAnimation.start();
        } else {
            mCurrentString = mInfoItems[0];
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
        mInfoItems[1] = "";
        mInfoItems[2] = "";
        mInfoItems[3] = "";
        mCurrentString = mInfoItems[0];
        mTextView.setText(mCurrentString);
        ViewGroup.LayoutParams params = mTextView.getLayoutParams();
        params.width = mScrollViewWidth;
        mTextView.setLayoutParams(params);
    }
}
