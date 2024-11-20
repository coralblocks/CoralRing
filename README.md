# CoralRing

CoralRing is a ultra-low-latency, lock-free, garbage-free, batching and concurrent circular queue (ring)
in off-heap shared memory for inter-process communication (IPC) in Java across different JVMs using memory-mapped files.

An interesting characteristic of memory-mapped files is that `they allow your shared memory to exceed the size of your machine physical memory (RAM) by relying on the OS's virtual memory mechanism`. Therefore your shared memory is limited not by your RAM but by the size of your hard drive (HDD/SSD). The trade-off of a large memory-mapped file is performance as the OS needs to swap pages back and forth from the hard drive to memory and vice-versa, in a process called _paging_.

For maximum performance (lowest possible latency) you should place your memory-mapped file inside the Linux `/dev/shm/` folder so that the contents of your file are entirely kept in RAM memory. Of course by doing so you are back to being limited to your available RAM memory. CoralRing uses a _circular_ queue (ring) in shared memory so even with a small piece of memory you can transmit millions of messages to the other process.

## Blocking Ring



