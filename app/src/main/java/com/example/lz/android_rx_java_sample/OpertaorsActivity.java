package com.example.lz.android_rx_java_sample;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import rx.Observable;
import rx.functions.Action1;
import rx.functions.Func1;

public class OpertaorsActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_opertaors);
    }

    public void onMapClick(View view) {
        Observable.just(("Hello, world!"))
                .map(new Func1<String, String>() {

                    @Override
                    public String call(String s) {
                        return s + " -Dan";
                    }
                }).subscribe(new Action1<String>() {
            @Override
            public void call(String s) {
                Log.v("onMapClick", s);
            }
        });
    }

    public void moreOnMapClick(View view) {
        Observable.just("(Hello, world!")
                .map(new Func1<String, Integer>() {
                    @Override
                    public Integer call(String s) {
                        return s.hashCode();
                    }
                }).map(new Func1<Integer, String>() {
            @Override
            public String call(Integer integer) {
                return String.valueOf(integer);
            }
        }).subscribe(new Action1<String>() {
            @Override
            public void call(String s) {
                Log.v("moreOnMapClick", s);
            }
        });
    }


}
