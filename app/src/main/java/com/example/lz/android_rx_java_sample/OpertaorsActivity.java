package com.example.lz.android_rx_java_sample;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import java.util.Arrays;
import java.util.List;

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
                Log.v("moreOnMap", s);
            }
        });
    }

    public void onFlatMapClick(View view) {
        //Observable.flatMap()接收一个Observable的输出作为输入，同时输出另外一个Observable。
        query("Hello, world!")
                .flatMap(new Func1<List<String>, Observable<String>>() {
                    @Override
                    public Observable<String> call(List<String> urls) {
                        return Observable.from(urls);
                    }
                })
                .subscribe(new Action1<String>() {
                    @Override
                    public void call(String s) {
                        Log.v("FlatMap", s);
                    }
                });

    }

    public void onFlatMapClick2(View view) {
        query("Hello, world!")
                .flatMap(new Func1<List<String>, Observable<String>>() {
                    @Override
                    public Observable<String> call(List<String> urls) {
                        return Observable.from(urls);
                    }
                }).flatMap(new Func1<String, Observable<String>>() {
            @Override
            public Observable<String> call(String url) {
                return getHost(url);
            }
        }).subscribe(new Action1<String>() {
            @Override
            public void call(String s) {
                Log.v("flat map 2", s);
            }
        });
    }


    public void onFilterClick(View view) {
        query("Hello, world!")
                .flatMap(new Func1<List<String>, Observable<String>>() {
                    @Override
                    public Observable<String> call(List<String> urls) {
                        return Observable.from(urls);
                    }
                })
                .flatMap(new Func1<String, Observable<String>>() {
                    @Override
                    public Observable<String> call(String url) {
                        return getHost(url);
                    }
                })
                .filter(new Func1<String, Boolean>() {
                    @Override
                    public Boolean call(String host) {
                        return host != null;
                    }
                }).take(2)//take() emits, at most, the number of items specified. (If there are fewer than 5 titles it'll just stop early.)
                .subscribe(new Action1<String>() {
                    @Override
                    public void call(String s) {
                        Log.v("filter()", s);
                    }
                });
    }


    public void onDoNextClick(View view) {
        query("Hello, world!")
                .flatMap(new Func1<List<String>, Observable<String>>() {
                    @Override
                    public Observable<String> call(List<String> urls) {
                        return Observable.from(urls);
                    }
                })
                .flatMap(new Func1<String, Observable<String>>() {
                    @Override
                    public Observable<String> call(String url) {
                        return getHost(url);
                    }
                })
                .filter(new Func1<String, Boolean>() {
                    @Override
                    public Boolean call(String host) {
                        return host != null;
                    }
                }).take(2)
                .doOnNext(new Action1<String>() {
                    @Override
                    public void call(String host) {
                        saveHostToFile(host);
                    }
                })
                .subscribe(new Action1<String>() {
                    @Override
                    public void call(String s) {
                        Log.v("doNext()", s);
                    }
                });
    }

    private Observable<String> getHost(String url) {
        return Observable.just(Uri.parse(url).getHost());
    }

    private Observable<List<String>> query(String param) {
        Observable<String> w = Observable.from(Arrays.asList("https://www.baidu.com", "https://m.baidu.com", "https://www.yahoo.com", null));
        Observable<List<String>> observable = w.toList();
        return observable;
    }

    private void saveHostToFile(String host) {
        //save host
    }
}
