# `freemarket`

A producer/consumer library for Clojure.

## Artifacts

`freemarket` artifacts are [released to Clojars](https://clojars.org/freemarket/freemarket).

If you are using Maven, add the following repository definition to your `pom.xml`:

``` xml
<repository>
  <id>clojars.org</id>
  <url>http://clojars.org/repo</url>
</repository>
```

### The Most Recent Release

With Leiningen:

``` clj
[freemarket "0.1.1"]
```

With Maven:

``` xml
<dependency>
  <groupId>freemarket</groupId>
  <artifactId>freemarket</artifactId>
  <version>0.1.1</version>
</dependency>
```

## Bugs and Enhancements

Please open issues against the [freemarket repo on Github](https://github.com/cprice-puppet/freemarket/issues).

## Motivation

I was looking for a simple way to manage producer-consumer tasks while retaining a good deal of control over the number of producer threads, number of consumer threads, and maximum amount of work to queue up.  In addition, I wanted something that would work well in situations where there was a finite amount of work to be done.  I wanted a system that could recognize when all of the work was completed and automatically shut things down without the need for me to do extensive cleanup.

Clojure's concurrency tools (agents, pmap, etc.) make it extremely easy to write concurrent code if your specific problem space is compatible with the assumptions that clojure makes about thread pool sizes, but upon encountering a task that did not fit well with those constraints, I had trouble finding anything that was simple and also flexible enough to meet my needs.

 `freemarket` aims to fill that gap by providing a minimal API for spawning a specific number of producer and consumer threads.  It's built around the concept of producer and consumer "work functions", which are called repeatedly to produce / consume work items.  When there is no more work to do, the work function is expected to return nil; when this happens, and the consumer work queue empties out, the system will automatically shut itself down.
 
This library should definitely be considered "alpha", and there's a good chance the API will change a bit before I get comfortable with it.  It's also in desperate need of better error-handling hooks; in the current implementation, if any exception occurs in any of your workers (producer or consumer), the whole system will shut itself down without giving you any opportunity to handle the exception.

## Usage

TODO: better docs here

Create a producer:

``` clojure
(def myproducer (producer producer-work-fn num-workers max-work))
```

`producer-work-fn` should be a function that returns one work item per call, and returns `nil` when there is no more work to do.  (This indicates to `freemarket` that it's ok to shut down the producer side of the system.)  The function will be called repeatedly by the producer worker threads, and each item returned by `work-fn` will be enqueued for consumer workers to process later.

`num-workers` is an int which limits the number of threads that will be used to produce work.

`max-work` is optional int which, if provided, will block producer threads from producing more work until the consumers have caught up and brought the queue size back down below the specified value.  This can be very useful for limiting memory usage.

 The function returns a `Producer` record, which is used to create the consumer workers in the next step.

 Create a consumer:

 ``` clojure
 (def myconsumer (consumer myproducer consumer-work-fn num-workers max-results))
 ```

 `myproducer` is a producer that you created in an earlier call to `producer`.

 `consumer-work-fn` is a function that accepts a single argument whose type is compatible with the output of the `producer-work-fn` that you used when creating your producer.  It will be called by the consumer worker threads, one time for each work item that is produced by the producer.  The result will then be enqueued in the consumer's result queue, which can be accessed as a clojure `seq` as we'll see in a moment.

 `num-workers` is an int which limits the number of threads that will be used to consume work from the producer queue.

 `max-results` is an optional int which, if provided, it will block consumer threads from producing more work until the consumers have caught up and brought the consumer result queue size back down below the specified value.  This can be very useful for limiting memory usage.

Access the results from the consumer workers:

 ``` clojure
 (doseq [result (work-queue->seq (:result-queue myconsumer))]
    (println result))
 ```

 NOTE: this is the part of the API that is most likely to change, because it's kind of gross right now :)

 `(:result-queue myconsumer)` gives you access to the queue of results generated by the consumer workers.  `work-queue->seq` converts that queue into a clojure lazy `seq` that you can then use elsewhere in your program, just like any other clojure `seq`.


## Under the hood

Both the `Producer` and `Consumer` records contain a key `:workers`, which is a `seq` of clojure `future` objects.  When all of the work is completed, each `future` will return an integer which is a count of how many work items they processed.  The only case where this is really useful is if you want/need to dereference the futures to determine when the producer/consumer system has completed its work and shut down.

## Development

Running the tests:

    $ lein2 test

## Documentation

Not yet generating documentation... TODO

## License

Released under the MIT License: <https://github.com/cprice-puppet/freemarket/blob/master/MIT-LICENSE.txt>
