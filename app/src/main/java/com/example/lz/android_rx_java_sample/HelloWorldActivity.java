package com.example.lz.android_rx_java_sample;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import rx.Observable;
import rx.Subscriber;
import rx.functions.Action1;

public class HelloWorldActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hello_world);
    }

    public void onSayHelloClick(View view) {
        Observable<String> myObservable = Observable.create(
                new Observable.OnSubscribe<String>() {
                    @Override
                    public void call(Subscriber<? super String> sub) {
                        sub.onNext("Hello, world!");
                        sub.onCompleted();
                    }
                }
        );

        Subscriber<String> mySubscriber = new Subscriber<String>() {
            @Override
            public void onNext(String s) {
                Log.v("SayHello", s);
            }

            @Override
            public void onCompleted() {
            }

            @Override
            public void onError(Throwable e) {
            }
        };

        myObservable.subscribe(mySubscriber);
    }

    public void onSimpleSayHelloClick(View view) {
        Observable<String> myObservable = Observable.just("Hello, world!");
        Action1<String> onNextAction = new Action1<String>() {
            @Override
            public void call(String s) {
                Log.v("SimpleSayHello", s);
            }
        };
        myObservable.subscribe(onNextAction);


//        Observable.just("Hello, world!")
//                .subscribe(new Action1<String>() {
//                    @Override
//                    public void call(String s) {
//                        System.out.println(s);
//                    }
//                });
    }
}
