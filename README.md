# pollitely

This is a library providing a light-weight approach for executing long-running tasks as simple HTTP request, 
making them not to incur in HTTP time-out. In other words, given web applications that run task 
such as **batch processes**, `politelly` can support making those tasks taking longer that usual HTTP timeout 
(typically few minutes). Reference tech stack is `Kotlin` / `Ktor`.

## Motivation
We've been using this in [GuccioGucci](https://github.com/GuccioGucci) for internal backoffice tools, 
on processes that takes up to few hours. Please, consider this just as a first attempt before setting up a proper 
distributed and/or scheduled architecture for task execution.

This is also sort of a spike with `Kotlin` & `Ktor`, while we're learning the stack. Unfortunately, we 
could not find any existing library for such a purpose, so we ended up writing our own!

## Origin
Wondering what the name comes from? Well, it's "polling", for something executed "lately": so "poll"-"lately", 
then `pollitely` :smile:

## Usage

The underlying idea is providing a standard (while simple) protocol for starting and waiting for completion
for long-running tasks. Here it comes:
```
> POST /resource HTTP/1.1
< HTTP/1.1 202 Accepted
< Location: /resource/1

> GET /resource/1 HTTP/1.1
< HTTP/1.1 204 No Content
< Retry-After: 5

... few seconds later ...

> GET /resource/1 HTTP/1.1
< HTTP/1.1 200 OK
This is the original task result
```

The protocol itself is then provided as a twofold component:
* a backed `Ktor` facility, for configuring Routes
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
    implementation "com.gucci:pollitely:0.1.1"
}    
```

Then, you can use LongRunning for configuring Routes on your application. Here's an example (see [here](/sample/src/Application.kt)):

```
routing {
    route("/api/executions", LongRunning(Ids.Sequential()).with({
        val name: Any = it.call().request.queryParameters["name"] ?: "Bob"
        return@with "Hello, $name"
    }))
}
```

More examples are available in [LongRunningTest.kt](lib/test/com/gucci/polling/LongRunningTest.kt).

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

## License

Copyright 2021 Gucci.

Licensed under the [GNU Lesser General Public License, Version 3.0](http://www.gnu.org/licenses/lgpl.txt)
