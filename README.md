# CoralRing

CoralRing is a ultra-low-latency, lock-free, garbage-free, batching and concurrent circular queue (_ring_)
in off-heap shared memory for inter-process communication (IPC) in Java across different JVMs using memory-mapped files.

An interesting characteristic of memory-mapped files is that `they allow your shared memory to exceed the size of your machine physical memory (RAM) by relying on the OS's virtual memory mechanism`. Therefore your shared memory is limited not by your RAM but by the size of your hard drive (HDD/SSD). The trade-off of a large memory-mapped file is performance as the OS needs to swap pages back and forth from the hard drive to memory and vice-versa, in a process called _paging_.

For maximum performance (lowest possible latency) you should place your memory-mapped file inside the Linux `/dev/shm/` folder so that the contents of your file are entirely kept in RAM memory. Of course by doing so you are back to being limited to your available RAM memory. CoralRing uses a _circular_ queue (_ring_) in shared memory so even with a small piece of memory you can transmit an unlimited number of messages to the other process.

## Blocking Ring

<img src="images/BlockingRing.png" alt="BlockingRing" width="50%" height="50%" />

Because the ring is a _bounded_ circular queue, the first approach is to have a _blocking_ producer and consumer. In other words, the ring producer will block (_wait_) when the ring is full and the ring consumer will block (_wait_) when the ring is empty. The consumer reads the messages (all the messages) in the same order that they were sent by the producer.

- Click [here](src/main/java/com/coralblocks/coralring/example/ring/minimal/MinimalBlockingProducer.java) for a minimal example of using blocking ring producer
- Click [here](src/main/java/com/coralblocks/coralring/example/ring/minimal/MinimalBlockingConsumer.java) for a minimal example of using blocking ring consumer
<br/><br/>
- Click [here](src/main/java/com/coralblocks/coralring/example/ring/BlockingProducer.java) for a basic example of using blocking ring producer
- Click [here](src/main/java/com/coralblocks/coralring/example/ring/BlockingConsumer.java) for a basic example of using blocking ring consumer

Note that for maximum performance the producer and consumer should busy spin when blocking. However you can also choose to use _wait strategies_ from [CoralQueue](https://github.com/coralblocks/CoralQueue).

## Non-Blocking Ring

<img src="images/NonBlockingRing.png" alt="NonBlockingRing" width="50%" height="50%" />

Things get more interesting when we allow the ring producer to write as fast as possible without ever blocking on a full ring. Because the ring is a _circular_ queue, the producer can just keep writing forever, overwritting the oldest messages on the head of the queue with the newest ones. In this new scenario, a _lagging consumer_ that falls behind and loses messages will simply disconnect _instead of causing the producer to block_. It has to disconnect because it must never skip messages from the producer.

This lagging consumer problem can be mitigated by creating a large memory-mapped file so that your shared memory ring is big enough to give room for the consumer to fall behind and catch up. However there is a more important issue that we need to address with a non-blocking ring which is when the consumer falls behind so much that it hits the _edge_ of the circular ring. When that happens there is a small chance that the consumer will be reading the oldest message in the ring at the same time that the producer is overwritting it with the newest mesage. In other words, the consumer can _trip over_ the producer.

### Using a _fall behind tolerance_

