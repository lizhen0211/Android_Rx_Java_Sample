package com.example.lz.android_rx_java_sample;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void onHelloWorldClick(View view) {
        Intent intent = new Intent(this, HelloWorldActivity.class);
        startActivity(intent);
    }

    public void onOpertaorsClick(View view) {
        Intent intent = new Intent(this, OpertaorsActivity.class);
        startActivity(intent);
    }
}
