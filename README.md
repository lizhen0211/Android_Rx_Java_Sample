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

英文原版博客地址：
http://blog.danlew.net/2014/09/15/grokking-rxjava-part-1/
http://blog.danlew.net/2014/09/22/grokking-rxjava-part-2/
http://blog.danlew.net/2014/10/08/grokking-rxjava-part-3/
http://blog.danlew.net/2014/10/08/grokking-rxjava-part-4/

转自：https://asce1885.gitbooks.io/android-rd-senior-advanced/content/che_di_le_jie_rxjava_ff08_yi_ff09_ji_chu_zhi_shi.html
彻底了解RxJava（一）基础知识
原文链接：http://blog.danlew.net/2014/09/15/grokking-rxjava-part-1/

RxJava是目前在Android开发者中新兴热门的函数库。唯一的问题是刚开始接触时会感到较难理解。函数响应式编程对于“外面世界”来的开发人员而言是很难理解的，但一旦理解了它，你会感觉真是太棒了。

我将介绍RxJava的一些知识，这系列文章（四部分）的目标是把你领进RxJava的大门。我不会解释所有相关的知识点（我也做不到），我只想引起你对RxJava的兴趣并知道它是如何工作的。

基础知识

响应式代码的基本组成部分是Observables和Subscribers（事实上Observer才是最小的构建块，但实践中使用最多的是Subscriber，因为Subscriber才是和Observables的对应的。）。Observable发送消息，而Subscriber则用于消费消息。

消息的发送是有固定模式的。Observable可以发送任意数量的消息（包括空消息），当消息被成功处理或者出错时，流程结束。Observable会调用它的每个Subscriber的Subscriber.onNext()函数，并最终以Subscriber.onComplete()或者Subscriber.onError()结束。

这看起来像标准的观察者模式， 但不同的一个关键点是：Observables一般只有等到有Subscriber订阅它，才会开始发送消息（术语上讲就是热启动Observable和冷启动Observable。热启动Observable任何时候都会发送消息，即使没有任何观察者监听它。冷启动Observable只有在至少有一个订阅者的时候才会发送消息（我的例子中都是只有一个订阅者）。这个区别对于开始学习RxJava来说并不重要。）。换句话说，如果没有订阅者观察它，那么将不会起什么作用。

Hello, World!

让我们以一个具体例子来实际看看这个框架。首先，我们创建一个基本的Observable：

Observable<String> myObservable = Observable.create(
    new Observable.OnSubscribe<String>() {
        @Override
        public void call(Subscriber<? super String> sub) {
            sub.onNext("Hello, world!");
            sub.onCompleted();
        }
    }
);
我们的Observable发送“Hello,world!”消息然后完成。现在让我们创建Subscriber来消费这个数据：

Subscriber<String> mySubscriber = new Subscriber<String>() {
    @Override
    public void onNext(String s) { System.out.println(s); }

    @Override
    public void onCompleted() { }

    @Override
    public void onError(Throwable e) { }
};
上面代码所做的工作就是打印由Observable发送的字符串。现在我们有了myObservable和mySubscriber，就可以通过subscribe()函数把两者关联起来：

myObservable.subscribe(mySubscriber);
// Outputs "Hello, world!"
当订阅完成，myObservable将调用subscriber的onNext()和onComplete()函数，最终mySubscriber打印“Hello, world!”然后终止。

更简洁的代码

上面为了打印“Hello, world!”写了大量的样板代码，目的是为了让你详细了解到底发生了什么。RxJava提供了很多快捷方式来使编码更简单。

首先让我们简化Observable，RxJava为常见任务提供了很多内建的Observable创建函数。在以下这个例子中，Observable.just()发送一个消息然后完成，功能类似上面的代码（严格来说，Observable.just()函数跟我们原来的代码并不完全一致，但在本系列第三部分之前我不会说明原因）：

Observable<String> myObservable =
    Observable.just("Hello, world!");
接下来，让我们处理Subscriber不必要的样板代码。如果我们不关心onCompleted()或者onError()的话，那么可以使用一个更简单的类来定义onNext()期间要完成什么功能：

Action1<String> onNextAction = new Action1<String>() {
    @Override
    public void call(String s) {
        System.out.println(s);
    }
};
Actions可以定义Subscriber的每一个部分，Observable.subscribe()函数能够处理一个，两个或者三个Action参数，分别表示onNext()，onError()和onComplete()函数。上面的Subscriber现在如下所示：

myObservable.subscribe(onNextAction, onErrorAction, onCompleteAction);
然而，现在我们不需要onError()和onComplete()函数，因此只需要第一个参数：

myObservable.subscribe(onNextAction);
// Outputs "Hello, world!"
现在，让我们把上面的函数调用链接起来从而去掉临时变量：

Observable.just("Hello, world!")
    .subscribe(new Action1<String>() {
        @Override
        public void call(String s) {
              System.out.println(s);
        }
    });
最后，我们使用Java 8的lambdas表达式来去掉丑陋的Action1代码：

Observable.just("Hello, world!")
    .subscribe(s -> System.out.println(s));
如果你在Android平台上（因此不能使用Java 8），那么我严重推荐使用retrolambda，它将极大的减少代码的冗余度。

变换

让我们来点刺激的。假如我想把我的签名拼接到“Hello, world!的输出中。一个可行的办法是改变Observable：

Observable.just("Hello, world! -Dan")
    .subscribe(s -> System.out.println(s));
当你有权限控制Observable时这么做是可行的，但不能保证每次都可以这样，如果你使用的是别人的函数库呢？另外一个可能的问题是：如果项目中在多个地方使用Observable，但只在某个地方需要增加签名，这时怎么办？

我们尝试修改Subscriber如何呢？

Observable.just("Hello, world!")
    .subscribe(s -> System.out.println(s + " -Dan"));
这个解决方案也不尽如人意，不过原因不同于上面：我想Subscribers尽可能的轻量级，因为我可能要在主线程中运行它。从更概念化的层面上讲，Subscribers是用于被动响应的，而不是主动发送消息使其他对象发生变化。 如果可以通过某些中间步骤来对上面的“Hello, world!”进行转换，那岂不是很酷？

Operators简介

接下来我们将介绍如何解决消息转换的难题：使用Operators。Operators在消息发送者Observable和消息消费者Subscriber之间起到操纵消息的作用。RxJava拥有大量的opetators，但刚开始最好还是从一小部分开始熟悉。 这种情况下，map() operator可以被用于将已被发送的消息转换成另外一种形式：

Observable.just("Hello, world!")
    .map(new Func1<String, String>() {
        @Override
        public String call(String s) {
            return s + " -Dan";
        }
    })
    .subscribe(s -> System.out.println(s));
同样的，我们可以使用Java 8的lambdas表达式来简化代码：

Observable.just("Hello, world!")
    .map(s -> s + " -Dan")
    .subscribe(s -> System.out.println(s));
很酷吧？我们的map() operator本质上是一个用于转换消息对象的Observable。我们可以级联调用任意多个的map()函数，一层一层地将初始消息转换成Subscriber需要的数据形式。

map()更多的解释

map()函数有趣的一点是：它不需要发送和原始的Observable一样的数据类型。假如我的Subscriber不想直接输出原始的字符串，而是想输出原始字符串的hash值：

Observable.just("Hello, world!")
    .map(new Func1<String, Integer>() {
        @Override
        public Integer call(String s) {
            return s.hashCode();
        }
    })
    .subscribe(i -> System.out.println(Integer.toString(i)));
有趣的是，我们的原始输入是字符串，但Subscriber最终收到的是Integer类型。同样的，我们可以使用lambdas简化代码如下：

Observable.just("Hello, world!")
    .map(s -> s.hashCode())
    .subscribe(i -> System.out.println(Integer.toString(i)));
正如我前面说过的，我希望Subscriber做尽量少的工作，我们可以把hash值转换成字符串的操作移动到一个新的map()函数中：

Observable.just("Hello, world!")
    .map(s -> s.hashCode())
    .map(i -> Integer.toString(i))
    .subscribe(s -> System.out.println(s));
你不想瞧瞧这个吗？我们的Observable和Subscriber变回了原来的代码。我们只是在中间添加了一些变换的步骤，我甚至可以把我的签名转换添加回去：

Observable.just("Hello, world!")
    .map(s -> s + " -Dan")
    .map(s -> s.hashCode())
    .map(i -> Integer.toString(i))
    .subscribe(s -> System.out.println(s));
那又怎样？

到这里你可能会想“为了得到简单的代码有很多聪明的花招”。确实，这是一个简单的例子，但有两个概念你需要理解：

关键概念＃1：Observable和Subscriber能完成任何事情。

放飞你的想象，任何事情都是可能的。 你的Observable可以是一个数据库查询，Subscriber获得查询结果然后将其显示在屏幕上。你的Observable可以是屏幕上的一个点击，Subscriber响应该事件。你的Observable可以从网络上读取一个字节流，Subscriber将其写入本地磁盘中。 这是一个可以处理任何事情的通用框架。

关键概念＃2：Observable和Subscriber与它们之间的一系列转换步骤是相互独立的。

我们可以在消息发送者Observable和消息消费者Subscriber之间加入任意多个想要的map()函数。这个系统是高度可组合的：它很容易对数据进行操纵。只要operators符合输入输出的数据类型，那么我可以得到一个无穷尽的调用链（好吧，并不是无穷尽的，因为总会到达物理机器的极限的，但你知道我想表达的意思）。

结合两个关键概念，你可以看到一个有极大潜能的系统。然而到这里我们只介绍了一个operator：map()，这严重地限制了我们的可能性。在本系列的第二部分，我们将详细介绍RxJava中大量可用的operators。