# The new pygmy.com

## What's this?
**pygmy.com** is a distributed book store that I developed as part of the [Distributed Systems](https://marcoserafini.github.io/teaching/distributed-os/spring20/index.html) course at UMass.
For some details about **pygmy.com**, such as the architecture, design decisions and other requirements, please see [here](https://marcoserafini.github.io/teaching/distributed-os/spring20/labs/lab2.html) and [here](https://marcoserafini.github.io/teaching/distributed-os/spring20/labs/lab3.html).

**pygmy.com** demonstrates how to implement some features that are common in distributed-systems these days, such as:
* Load-balancing
* Fault-tolerance
* State machine replication
* Caching
* Microservices coupled using RESTful APIs
* Dockerized containers

## More details
Read more about the architecture and implementation details in the [Design Document](https://github.com/sg1993/pygmy.com/blob/master/docs/DesignDoc.pdf)
* [Spark](https://github.com/perwendel/spark) to implement the RESTful APIs
* [cache2k library for java](https://github.com/cache2k/cache2k) for caching. See [here](https://github.com/sg1993/pygmy.com/blob/27b211849b8985940d810bf0e3d1b64d0af3d39f/src/pygmy/com/ui/FrontEndCacheManager.java#L16)
* Round-robin load-balancer that distributes the incoming requests between the two OrderServer replicas and the two CatalogServer replicas. See [here](https://github.com/sg1993/pygmy.com/blob/27b211849b8985940d810bf0e3d1b64d0af3d39f/src/pygmy/com/scheduler/RoundRobinLoadBalancer.java#L10)
* See [here](https://github.com/sg1993/pygmy.com/blob/27b211849b8985940d810bf0e3d1b64d0af3d39f/src/pygmy/com/scheduler/HeartbeatMonitor.java#L15) for implementation of Fault-detection and job-migration
* I used a basic token-ring algorithm to ensure replication consistency between the two CatalogServer replicas.
* Dockerized containers of the microservices in pygmy.com available @ [Docker hub](https://hub.docker.com/r/shibingeorge/pygmy.com)

## How to run?
Please see the "How to run?" section of the [Design Document](https://github.com/sg1993/pygmy.com/blob/master/docs/DesignDoc.pdf) for detailed instructions on how to deploy the servers, how to run the client-side command-line based program, and how to run the experiments (cache-consistency, fault-tolerance/recovery), and how to run the dockerized pygmy.com !
