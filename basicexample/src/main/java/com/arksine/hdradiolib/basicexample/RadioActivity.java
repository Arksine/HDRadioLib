package com.arksine.hdradiolib.basicexample;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

public class RadioActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_radio);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
