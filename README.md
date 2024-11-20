# CoralRing

CoralRing is a ultra-low-latency, lock-free, garbage-free, batching and concurrent circular queue (_ring_)
in off-heap shared memory for inter-process communication (IPC) in Java across different JVMs using memory-mapped files.

An interesting characteristic of memory-mapped files is that `they allow your shared memory to exceed the size of your machine physical memory (RAM) by relying on the OS's virtual memory mechanism`. Therefore your shared memory is limited not by your RAM but by the size of your hard drive (HDD/SSD). The trade-off of a large memory-mapped file is performance as the OS needs to swap pages back and forth from the hard drive to memory and vice-versa, in a process called _paging_.

For maximum performance (lowest possible latency) you should place your memory-mapped file inside the Linux `/dev/shm/` folder so that the contents of your file are entirely kept in RAM memory. Of course by doing so you are back to being limited to your available RAM memory. CoralRing uses a _circular_ queue (_ring_) in shared memory so even with a small piece of memory you can transmit millions of messages to the other process.

## Blocking Ring

<img src="images/BlockingRing.png" alt="BlockingRing" width="50%" height="50%" />

Because the ring is a _bounded_ circular queue, the first approach is to have a _blocking_ producer and consumer. In other words, the ring producer will block (_wait_) when the ring is full and the ring consumer will block (_wait_) when the ring is empty. The consumer reads the messages (all the messages) in the same order that they were sent by the producer.

- Click [here](src/main/java/com/coralblocks/coralring/example/ring/minimal/MinimalBlockingProducer.java) for a minimal example of using blocking ring producer
- Click [here](src/main/java/com/coralblocks/coralring/example/ring/minimal/MinimalBlockingConsumer.java) for a minimal example of using blocking ring consumer
<br/><br/>
- Click [here](src/main/java/com/coralblocks/coralring/example/ring/BlockingProducer.java) for a basic example of using blocking ring producer
- Click [here](src/main/java/com/coralblocks/coralring/example/ring/BlockingConsumer.java) for a basic example of using blocking ring consumer

Note that for maximum performance the producer and consumer should busy spin when blocking. However you can also choose to use wait strategies from [CoralQueue](https://github.com/coralblocks/CoralQueue).
