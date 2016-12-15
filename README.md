转自：https://asce1885.gitbooks.io/android-rd-senior-advanced/content/

什么是函数响应式编程（Java&Android版本）
原文链接：http://www.bignerdranch.com/blog/what-is-functional-reactive-programming/

函数响应式编程（FRP）为解决现代编程问题提供了全新的视角。一旦理解它，可以极大地简化你的项目，特别是处理嵌套回调的异步事件，复杂的列表过滤和变换，或者时间相关问题。

我将尽量跳过对函数响应式编程学院式的解释（网络上已经有很多），并重点从实用的角度帮你理解什么是函数响应式编程，以及工作中怎么应用它。本文将围绕函数响应式编程的一个具体实现RxJava， 它可用于Java和Android。

开始

我们以一个真实的例子来开始讲解函数响应式编程怎么提高我们代码的可读性。我们的任务是通过查询GitHub的API， 首先获取用户列表，然后请求每个用户的详细信息。这个过程包括两个web 服务端点： https://api.github.com/users - 获取用户列表； https://api.github.com/users/{username} －获取特定用户的详细信息，例如https://api.github.com/users/mutexkid。

旧的风格

下面例子你可能已经很熟悉了：它调用web service，使用回调接口将成功的结果传递给下一个web service请求，同时定义另一个成功回调，然后发起下一个web service请求。你可以看到，这会导致两层嵌套的回调：

//The "Nested Callbacks" Way
    public void fetchUserDetails() {
        //first, request the users...
        mService.requestUsers(new Callback<GithubUsersResponse>() {
            @Override
            public void success(final GithubUsersResponse githubUsersResponse,
                                final Response response) {
                Timber.i(TAG, "Request Users request completed");
                final synchronized List<GithubUserDetail> githubUserDetails = new ArrayList<GithubUserDetail>();
                //next, loop over each item in the response
                for (GithubUserDetail githubUserDetail : githubUsersResponse) {
                    //request a detail object for that user
                    mService.requestUserDetails(githubUserDetail.mLogin,
                                                new Callback<GithubUserDetail>() {
                        @Override
                        public void success(GithubUserDetail githubUserDetail,
                                            Response response) {
                            Log.i("User Detail request completed for user : " + githubUserDetail.mLogin);
                            githubUserDetails.add(githubUserDetail);
                            if (githubUserDetails.size() == githubUsersResponse.mGithubUsers.size()) {
                                //we've downloaded'em all - notify all who are interested!
                                mBus.post(new UserDetailsLoadedCompleteEvent(githubUserDetails));
                            }
                        }

                        @Override
                        public void failure(RetrofitError error) {
                            Log.e(TAG, "Request User Detail Failed!!!!", error);
                        }
                    });
                }
            }

            @Override
            public void failure(RetrofitError error) {
                Log.e(TAG, "Request User Failed!!!!", error);
            }
        });
    }
尽管这不是最差的代码－至少它是异步的，因此在等待每个请求完成的时候不会阻塞－但由于代码复杂（增加更多层次的回调代码复杂度将呈指数级增长）因此远非理想的代码。当我们不可避免要修改代码时（在前面的web service调用中，我们依赖前一次的回调状态，因此它不适用于模块化或者修改要传递给下一个回调的数据）也远非容易的工作。我们亲切的称这种情况为“回调地狱”。

RxJava的方式

下面让我们看看使用RxJava如何实现相同的功能：

public void rxFetchUserDetails() {
        //request the users
        mService.rxRequestUsers().concatMap(Observable::from)
        .concatMap((GithubUser githubUser) ->
                        //request the details for each user
                        mService.rxRequestUserDetails(githubUser.mLogin)
        )
        //accumulate them as a list
        .toList()
        //define which threads information will be passed on
        .subscribeOn(Schedulers.newThread())
        .observeOn(AndroidSchedulers.mainThread())
        //post them on an eventbus
        .subscribe(githubUserDetails -> {
            EventBus.getDefault().post(new UserDetailsLoadedCompleteEvent(githubUserDetails));
        });
    }
如你所见，使用函数响应式编程模型我们完全摆脱了回调，并最终得到了更短小的程序。让我们从函数响应式编程的基本定义开始慢慢解释到底发生了什么，并逐渐理解上面的代码，这些代码托管在GitHub上面。

从根本上讲，函数响应式编程是在观察者模式的基础上，增加对Observables发送的数据流进行操纵和变换的功能。在上面的例子中，Observables是我们的数据流通所在的管道。

回顾一下，观察者模式包含两个角色：一个Observable和一个或者多个Observers。Observable发送事件，而Observer订阅和接收这些事件。在上面的例子中，.subscribe()函数用于给Observable添加一个Observer，并创建一个请求。

构建一个Observable管道

对Observable管道的每个操作都将返回一个新的Observable，这个新的Observable的内容要么和操作前相同，要么就是经过转换的。这种方式使得我们可以对任务进行分解，并把事件流分解成小的操作，接着把这些Observables拼接起来从而完成更复杂的行为或者重用管道中的每个独立的单元。我们对Observable的每个方法调用会被加入到总的管道中以便我们的数据在其中流动。

下面首先让我们从搭建一个Observable开始，来看一个具体的例子：

Observable<String> sentenceObservable = Observable.from(“this”, “is”, “a”, “sentence”);
这样我们就定义好了管道的第一个部分：Observable。在其中流通的数据是一个字符串序列。首先要认识到的是这是没有实现任何功能的非阻塞代码，仅仅定义了我们想要完成什么事情。Observable只有在我们“订阅”它之后才会开始工作，也就是说给它注册一个Observer之后。

Observable.subscribe(new Action1<String>() {
          @Override
          public void call(String s) {
                System.out.println(s);
          }
 });
到这一步Observable才会发送由每个独立的Observable的from()函数添加的数据块。管道会持续发送Observables直到所有Observables都被处理完成。

变换数据流

现在我们得到正在发送的字符串流，我们可以按照需要对这些数据流进行变换，并建立更复杂的行为。

Observable<String> sentenceObservable = Observable.from(“this”, “is”, “a”, “sentence”);

sentenceObservable.map(new Func1<String, String>() {
            @Override
            public String call(String s) {
                return s.toUpperCase() + " ";
            }
        })
.toList()
.map(new Func1<List<String>, String>() {
            @Override
            public String call(List<String> strings) {
                Collections.reverse(strings);
                return strings.toString();
            }
        })
//subscribe to the stream of Observables
.subscribe(new Action1<String>() {
            @Override
            public void call(String s) {
                System.out.println(s);
            }
        });
一旦Observable被订阅了，我们会得到“SENTENCE A IS THIS”。上面调用的.map函数接受Func1类的对象，该类有两个范型类型参数：一个是输入类型（前一个Observable的内容），另一个是输出类型（在这个例子中，是一个经过大写转换，格式化并用一个新的Observable实例包装的字符串，最终被传递给下一个函数）。如你所见，我们通过可重用的管道组合实现更复杂的功能。

上面的例子中，我们还可以使用Java8的lambda表达式来进一步简化代码：

Observable.just("this", "is", "a", "sentence").map(s -> s.toUpperCase() + " ").toList().map(strings -> {
            Collections.reverse(strings);
            return strings.toString();
        });
在subscribe函数中，我们传递Action1类对象作为参数，并以String类型作为范型参数。这定义了订阅者的行为，当被观察者发送最后一个事件后，处理后的字符串就被接收到了。这是.subscribe()函数最简单的重载形式（参见https://github.com/ReactiveX/RxJava/wiki/Observable#establishing-subscribers 可以看到更复杂的函数重载签名）。

这个例子展示了变换函数.map()和聚合函数.toList()，在操纵数据流的能力方面这仅仅是冰山一角（所有可用的数据流操作函数可见https://github.com/ReactiveX/RxJava/wiki）， 但它显示了基本概念：在函数响应式编程中，我们可以通过实现了数据转换或者数据操作功能的管道中独立的单元来转换数据流。根据需要我们可以在其他由Observables组成的管道复用这些独立的单元。通过把这些Observable单元拼接在一起，我们可以组成更复杂的特性，但同时保持它们作为易于理解和可修改的可组合逻辑小单元。

使用Scheduler管理线程

在web service例子中，我们展示了如何使用RxJava发起网络请求。我们谈及转换，聚合和订阅Observable数据流，但我们没有谈及Observable数据流的web请求是怎样实现异步的。

这就属于FRP编程模型如何调用Scheduler的范畴了－该策略定义了Observable流事件在哪个线程中发生，以及订阅者在哪个线程消费Observable的处理结果。在web service例子中，我们希望请求在后台线程中进行，而订阅行为发生在主线程中，因此我们如下定义：

.subscribeOn(Schedulers.newThread())
        .observeOn(AndroidSchedulers.mainThread())
        //post them on an eventbus
        .subscribe(githubUserDetails -> {
            EventBus.getDefault().post(new UserDetailsLoadedCompleteEvent(githubUserDetails));
        });
Observable.subscribeOn(Scheduler scheduler)函数指定Observable的工作需要在指定的Scheduler线程中执行。Observable.observeOn(Scheduler scheduler)指定Observable在哪个Scheduler线程触发订阅者们的onNext()，onCompleted()，和onError()函数，并调用Observable的observeOn()函数，传递正确的Scheduler给它。

下面是可能会用到Scheduler：

Schedulers.computation()：用于计算型工作例如事件循环和回调处理，不要在I/O中使用这个函数（应该使用Schedulers.io()函数）；

Schedulers.from(executor)：使用指定的Executor作为Scheduler；

Schedulers.immediate()：在当前线程中立即开始执行任务；

Schedulers.io()：用于I/O密集型工作例如阻塞I/O的异步操作，这个调度器由一个会随需增长的线程池支持；对于一般的计算工作，使用Schedulers.computation()；

Schedulers.newThread()：为每个工作单元创建一个新的线程；

Schedulers.test()：用于测试目的，支持单元测试的高级事件；

Schedulers.trampoline()：在当前线程中的工作放入队列中排队，并依次操作。

通过设置observeOn和subscribeOn调度器，我们定义了网络请求使用哪个线程（Schedulers.newThread()）。

下一步

我们已经在本文中涵盖了很多基础内容，到这里你应该对函数响应式编程如何工作有了很好的认识。请查看并理解本文介绍的工程，它托管在GitHub上面，阅读RxJava文档并检出rxjava-koans工程，以测试驱动的方式掌握函数响应式编程范型。