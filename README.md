# pollitely

This is a library providing a light-weight approach for executing long-running tasks as simple HTTP request, 
making them not to incur in HTTP time-out. In other words, given web applications that run task 
such as **batch processes**, `politelly` can support making those tasks taking longer that usual HTTP timeout 
(typically few minutes). Reference tech stack is `Kotlin` / `Ktor`.

## Motivation
We've been using this in [GuccioGucci](https://github.com/GuccioGucci) for internal backoffice tools, 
on processes that takes up to few hours, polling for completion from an SPA, while letting user doing something else. 

Please, consider this just as a first attempt before setting up a proper 
distributed and/or scheduled architecture for task execution. Provided implementation is fully *in-process* and *in-memory*,
not intended (yet) to be scaled!

This is also sort of a spike with `Kotlin` & `Ktor`, while we're learning the stack. Unfortunately, we 
could not find any existing library for such a purpose, so we ended up writing our own!

## Origin
Wondering what the name comes from? Well, it's "polling", for something executed "lately": so "poll"-"lately", 
but being *polite*. Then `pollitely` :smile:

## Usage

The underlying idea is providing a standard (while simple) protocol for starting and waiting for completion
for long-running tasks. Here it comes:
```
> POST /api/executions HTTP/1.1
< HTTP/1.1 202 Accepted
< Location: /resources/1

> GET /resources/1 HTTP/1.1
< HTTP/1.1 204 No Content
< Retry-After: 5

... few seconds later ...

> GET /resources/1 HTTP/1.1
< HTTP/1.1 200 OK
This is the original task result body

> GET /resources/1 HTTP/1.1
< HTTP/1.1 404 OK
```

The protocol itself is then provided as a twofold component:
* a backend `Ktor` facility, for configuring `Routes`
* a frontend `ReactJS` interceptor, for waiting for task completion

### Backend

`pollitely` is provided (at the moment) as a standard Maven artifact, available here on Github Package Repository.
In order to use it, you should define both a maven repository and an implementation dependency, on your 
build.gradle file (or equivalent).

Here's a sample Gradle build file:

```
repositories {
    ...
    repositories {
        maven {
            name = "GitHubPackages"
            url = "https://maven.pkg.github.com/GuccioGucci/pollitely"
            credentials {
                username = System.getenv("GITHUB_PACKAGES_USERNAME")
                password = System.getenv("GITHUB_PACKAGES_PASSWORD")
            }
        }
    }
}

dependencies {
    ...
    implementation "com.gucci:pollitely-lib:$VERSION" // choose you preferred version!
}    
```

Please, don't forget configuring `DoubleReceive` feature (see [Caveats](#caveats) section):

```
install(DoubleReceive) {
    receiveEntireContent = true
}
```

Then, you can use [`LongRunning`](pollitely-lib/src/com/gucci/pollitely/LongRunning.kt) for configuring `Routes` on your application. 
Here's an example (see [here](/pollitely-sample/src/Application.kt)):

```
routing {
    route("/api/executions", LongRunning(ids = Ids.Sequential(), every = 5).with({
        delay(10000)
        val name: Any = it.call().request.queryParameters["name"] ?: "Bob"
        return@with "Hello, $name"
    }))
}
```

More examples are available in [`LongRunningTest`](pollitely-lib/test/com/gucci/pollitely/LongRunningTest.kt).

### Frontend

Here's a sample snippet for using resources adhering to the protocol:

```
import axios from 'axios';

const delay = (ms, action) => {
    return new Promise(function (resolve) {
        setTimeout(resolve.bind(null, action), ms)
    });
}

const pollEvery = async (ms, location) => {
    console.log("Waiting " + ms + " ms");
    await delay(ms);
    const response = await axios.get(location);

    if (response.status !== 200 && response.status !== 404) {
        const delayInSeconds = response.headers['retry-after'] ?? 5;
        console.log("Polling, status: " + response.status);
        return await pollEvery(delayInSeconds * 1000, location);
    }

    console.log("Done, status: " + response.status);
    return response;
}

const API_CLIENT = axios.create();

API_CLIENT.interceptors.response.use(async (response) => {
    if (response.status === 202) {
            logger.debug("Interceptor started, location: " + response.headers.location);
            return await pollEvery(1000, response.headers.location);
        }
        
        logger.debug("Interceptor skipped, status: " + response.status)
        return response;
});
```

## Caveats

### DoubleReceive Feature
As we said, this is mostly our internal attempt for supporting long-running tasks execution. What we found is that, in
order to make it *work*, we need to tweak a little `Ktor` pipeline execution. Specifically, we need to ensure that 
`applicationCall` is not yet consumed while processing postponed tasks. In order to do so, we're:
* warming-up the `applicationCall`, forcing request is consumed **before** later task execution
* configuring `DoubleReceive` feature for the application, ensuring request is **still** available to later task execution

### Incomplete Tests
One last thing related to automatic tests. We're still not able to fully test the protocol, in particular the asynchronous
execution (in other words, testing the intermediate `204 No Content` responses). This is probably due to a limitation in 
the withTestApplication facility from Ktor **server test** library. We'd be more that happy to learn how to do it!

## License

Copyright 2021 Gucci.

Licensed under the [GNU Lesser General Public License, Version 3.0](http://www.gnu.org/licenses/lgpl.txt)
