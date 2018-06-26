package com.star.learningrxjavaerrorretry;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "RxJava";

    private static final String BASE_URL = "https://api.github.com";
    public static final String PATH = "/repos/{owner}/{repo}/contributors";

    private static final String OWNER = "square";
    private static final String REPO = "retrofit";

    private int maxConnectCount = 10;
    private int currentRetryCount = 0;
    private int waitRetryTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Create a very simple REST adapter which points the GitHub API.
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .build();

        // Create an instance of our GitHub API interface.
        GitHub github = retrofit.create(GitHub.class);

        // Create an observable instance for looking up Retrofit contributors.
        Observable<List<Contributor>> observable = github.contributors(OWNER, REPO);

        observable
                .retryWhen(throwableObservable ->
                        throwableObservable.flatMap(
                                (Function<Throwable, ObservableSource<?>>) throwable -> {

                            Log.d(TAG, "发生异常 = "+ throwable.toString());

                            if (throwable instanceof IOException) {

                                Log.d(TAG, "属于IO异常，需重试");

                                if (currentRetryCount < maxConnectCount) {

                                    currentRetryCount++;
                                    Log.d(TAG, "重试次数 = " + currentRetryCount);

                                    waitRetryTime = 1 + currentRetryCount;

                                    Log.d(TAG, "等待时间 = " + waitRetryTime);

                                    return Observable.just(1).delay(waitRetryTime, TimeUnit.SECONDS);
                                } else {

                                    return Observable.error(
                                            new Throwable("重试次数已超过设置次数 = " +
                                                    currentRetryCount  + "，即不再重试"));
                                }
                            } else{

                                return Observable.error(new Throwable("发生了非网络异常（非I/O异常）"));
                            }
                        }))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<List<Contributor>>() {

                    @Override
                    public void onSubscribe(Disposable d) {
                        Log.d(TAG, "开始采用subscribe连接");
                    }

                    @Override
                    public void onNext(List<Contributor> contributors) {

                        Log.d(TAG, "第 " + currentRetryCount + " 次重试");

                        for (Contributor contributor : contributors) {
                            Log.d(TAG, contributor.login + " (" + contributor.contributions + ")");
                        }
                    }

                    @Override
                    public void onError(Throwable e) {
                        Log.d(TAG, "对Error事件作出响应 " + e.toString());
                    }

                    @Override
                    public void onComplete() {
                        Log.d(TAG, "对Complete事件作出响应");
                    }
                });
    }
}
