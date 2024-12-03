# CoralRing

CoralRing is an ultra-low-latency, lock-free, garbage-free, batching and concurrent circular queue (_ring_)
in off-heap shared memory for inter-process communication (IPC) in Java across different JVMs using memory-mapped files.
It uses memory barriers through [volatile operations](https://github.com/coralblocks/CoralRing/blob/9d341629c330875c8c6d31559a670742c224e524/src/main/java/com/coralblocks/coralring/util/MemoryVolatileLong.java#L57) instead of locks to allow messages to be sent as fast as possible.

An interesting characteristic of _memory-mapped files_ is that `they allow your shared memory to exceed the size of your machine physical memory (RAM) by relying on the OS's virtual memory mechanism`. Therefore your shared memory is limited not by your RAM but by the size of your hard drive (HDD/SSD). The trade-off of a large memory-mapped file is performance as the OS needs to swap pages back and forth from hard drive to memory and vice-versa, in a process called _paging_.

For maximum performance (lowest possible latency) you should place your memory-mapped file inside the Linux `/dev/shm/` folder so that the contents of your file are entirely kept in RAM memory. Of course by doing so you are back to being limited to your available RAM memory. CoralRing uses a _circular_ queue (_ring_) in shared memory so even with a small piece of memory you can transmit an unlimited number of messages to the other process.

For some performance numbers you can check [this link](https://www.coralblocks.com/index.php/inter-process-communication-with-coralqueue/).

## Blocking Ring

<img src="images/BlockingRing.png" alt="BlockingRing" width="50%" height="50%" />

**NOTE:** By _blocking_ we want to mean that the producer will have to block on a full ring by waiting around `nextToDispatch()` until it returns an object. On the other hand, the consumer always has to block on an empty ring.

Because the ring is a _bounded_ circular queue, the first approach is to have a _blocking_ producer and consumer. In other words, the ring producer will block (_wait_) when the ring is full and the ring consumer will block (_wait_) when the ring is empty. Basically a slow consumer will cause the producer to block, waiting for space to become available in the ring. The consumer reads the messages (all the messages) in the same order that they were sent by the producer.

- Click [here](src/main/java/com/coralblocks/coralring/example/ring/minimal/MinimalBlockingProducer.java) for a minimal example of using blocking ring producer
- Click [here](src/main/java/com/coralblocks/coralring/example/ring/minimal/MinimalBlockingConsumer.java) for a minimal example of using blocking ring consumer
<br/><br/>
- Click [here](src/main/java/com/coralblocks/coralring/example/ring/BlockingProducer.java) for a basic example of using blocking ring producer
- Click [here](src/main/java/com/coralblocks/coralring/example/ring/BlockingConsumer.java) for a basic example of using blocking ring consumer

Note that for maximum performance the producer and consumer should busy spin when blocking. However you can also choose to use a _wait strategy_ from [CoralQueue](https://github.com/coralblocks/CoralQueue).

## Blocking Broadcast Ring

<img src="images/BlockingMcastRing.png" alt="BlockingMcastRing" width="50%" height="50%" />

You can also have a single producer broadcasting messages to multiple consumers so that `each consumer gets all the messages in the same order that they were sent by the producer`. Any slow consumer can cause the ring to get full and the producer to block. As the slow consumer makes progress so will the producer.

- Click [here](src/main/java/com/coralblocks/coralring/example/ring/minimal/MinimalBlockingBroadcastProducer.java) for a minimal example of using blocking broadcast ring producer
- Click [here](src/main/java/com/coralblocks/coralring/example/ring/minimal/MinimalBlockingBroadcastConsumer.java) for a minimal example of using blocking broadcast ring consumer
<br/><br/>
- Click [here](src/main/java/com/coralblocks/coralring/example/ring/BlockingBroadcastProducer.java) for a basic example of using blocking broadcast ring producer
- Click [here](src/main/java/com/coralblocks/coralring/example/ring/BlockingBroadcastConsumer.java) for a basic example of using blocking broadcast ring consumer

Note that for maximum performance the producer and consumers should busy spin when blocking. However you can also choose to use a _wait strategy_ from [CoralQueue](https://github.com/coralblocks/CoralQueue).

## Non-Blocking Ring

<img src="images/NonBlockingRing.png" alt="NonBlockingRing" width="50%" height="50%" />

**NOTE:** By _non-blocking_ we want to mean that the producer will _not_ have to block on a full ring and will just keep going, overwriting the oldest entries in the circular ring. `That means that the method nextToDispatch() will never return null`. On the other hand, the consumer always has to block on an empty ring.

Things get more interesting when we allow the ring producer to write as fast as possible without ever blocking on a full ring. Because the ring is a _circular_ queue, the producer can just keep writing forever, overwriting the oldest messages on the head of the queue with the newest ones. In this new scenario, a _lagging consumer_ that falls behind and loses messages will simply disconnect (give up) _instead of causing the producer to block_. It has to disconnect because it must never skip messages from the producer.
```Java
long avail = ringConsumer.availableToFetch();
			
if (avail == 0) continue; // busy spin as the ring is empty
			
if (avail == -1) throw new RuntimeException("The consumer fell behind too much! (ring wrapped)");
```

This lagging consumer problem can be mitigated by creating a large memory-mapped file so that your shared memory ring is big enough to give room for the consumer to fall behind and catch up. However there is a more important issue that we need to address with a non-blocking ring which is when the consumer falls behind so much that it hits the _edge_ of the circular ring. When that happens there is a _small chance_ that the consumer will be reading the oldest message in the ring at the same time that the producer is overwriting it with the newest message. In other words, the consumer can _trip over_ the producer.

### Using a _fall behind tolerance_

The _tripping over_ problem will _only_ happen when the consumer falls behind N messages, where N is equal to the capacity of the ring. If it falls behind a little more, it simply disconnects. If it falls behind a little less it _should_ still be able to read the next message without any issues. `So the bigger the capacity of the ring the less likely it is for the consumer to trip over the producer` because the more room it has to fall behind safely. Therefore, to reduce the chances for the consumer to get close to the edge, we can introduce a _fall behind tolerance_, in other words, `we can make the consumer give up and disconnect early when it falls to a percentage P of the capacity of the ring`.

The constructor of `NonBlockingConsumer` can take a _float_ argument `fallBehindTolerance` to specify the percentage of the ring capacity to fall behind before disconnecting. When it falls below that threshold then its `availableToFetch()` method returns `-1`.

Unfortantely, although this will further reduce the chances for the consumer to read a corrupt message, **it does not make it zero**. Theoretically, the slowness of the consumer is so _unpredictable_ that while it is reading a message there will always be a small chance that the producer is overwriting it. If we really want to eliminate this possibility completely we must use a _checksum_ for each message.

### Using a _checksum_ for each message

To completely solve the _corrupt message_ consumer problem, we can make the producer write a _checksum_ together with each message so that the consumer can check the integrity of the message after it reads it. Although we use a _fast_ hash algorithm ([_xxHash_](https://github.com/apache/drill/blob/master/exec/java-exec/src/main/java/org/apache/drill/exec/expr/fn/impl/XXHash.java](https://xxhash.com/))) to calculate the checksum, there is a small performance penalty to pay when you choose this approach.

The constructor of `NonBlockingProducer` can take a _boolean_ argument `writeChecksum` to tell the producer to write the _checksum_. The constructor of `NonBlockingConsumer` can take a _boolean_ argument `checkChecksum` to tell the consumer to check the _checksum_. The consumer can check for a _checksum error_ by checking for a `null` value returned from `fetch()`:
```Java
for(long i = 0; i < avail; i++) {
      
    MutableLong ml = ringConsumer.fetch();
      
    if (ml == null) {
        throw new RuntimeException("The consumer tripped over the producer! (checksum failed)");
    }
      
    // (...)
}
```

Note that when using the _checksum_ approach there is no reason to also use a _fall behind tolerance_. You can catch the exception, assume that the consumer has fallen behind too much and disconnect (give up).

### Using a _very large_ memory-mapped file

There is also another _simple_ approach to solve the _tripping over_ problem: `just allocate a very large memory-mapped file so that the producer never has to wrap around the ring`. For example, let's say you want to send _100 million_ messages per day, with a maximum size of _1024 bytes_. If you do the math you will see that this is _only_ 95 gigabytes of hard drive space. And as a bonus, as long as you don't go above your predicted maximum number of messages (no wrapping around the ring), you will also have all your messages persisted to disk at the end of your daily session. Then to begin a new session you can move the session file someplace else for archiving, reset the message sequence back to 1, and start over again.

- Click [here](src/main/java/com/coralblocks/coralring/example/ring/minimal/MinimalNonBlockingProducer.java) for a minimal example of using non-blocking ring producer
- Click [here](src/main/java/com/coralblocks/coralring/example/ring/minimal/MinimalNonBlockingConsumer.java) for a minimal example of using non-blocking ring consumer
<br/><br/>
- Click [here](src/main/java/com/coralblocks/coralring/example/ring/NonBlockingProducer.java) for a basic example of using non-blocking ring producer
- Click [here](src/main/java/com/coralblocks/coralring/example/ring/NonBlockingConsumer.java) for a basic example of using non-blocking ring consumer

## Non-Blocking Multicast Ring

A non-blocking ring can be used naturally to implement _multicast consumers_. That means that `you can have multiple non-blocking ring consumers reading from the same non-blocking ring producer`. All consumers will read all messages in the exact same order. A consumer can still fall behind and disconnect, but it will never miss a message or process a message out of order. It will also never block the producer which does not even know how many consumers it is multicasting to. In other words, `consumers can leave and join the ring at any moment` without impacting the producer.

<img src="images/NonBlockingMcastRing.png" alt="NonBlockingMcastRing" width="50%" height="50%" />

## CoralQueue

CoralRing is great for threads running in different JVMs. But what about threads running inside the _same JVM_? For that you can check our [CoralQueue](https://github.com/coralblocks/CoralQueue) project which is a collection of circular data structures for inter-thread communication in Java.

<img src="images/Queue.png" alt="CoralQueue" width="50%" height="50%" />
