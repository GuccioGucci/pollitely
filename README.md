# pollitely

This is a light-weight approach for making long-running HTTP request not to incur in time-out.
We've been using this in [GuccioGucci](https://github.com/GuccioGucci) for internal backoffice tools, on processes that takes up to
few hours. Please, consider this just as a first attempt before setting up a proper distributed and/or 
scheduled architecture for task execution.

This is also sort of a spike with `Kotlin` & `Ktor`, while we're learning the stack. Unfortunately, we 
could not find any existing library for such a purpose, so we ended up writing our own! 

## Usage

The underlying idea is providing a standard (while simple) protocol for starting and waiting for completion
for long-running tasks. The protocol itself is the provided as a twofold component:
* a backed `Ktor` facility, for configuring Routes
* a frontend `ReactJS` interceptor, for waiting for task completion

**[TBC: protocol, as image]**

### Backend

`pollitely` is provided (at the moment) as a standard Maven artifact, available here on Github Package Repository.
In order to use it, you should define both a maven repository and an implementation dependency, on your 
build.gradle file (or equivalent).

**[TBC: Gradle sample]**

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
