Apache Artemix JMH Benchmarks
-------
This module contains optional [JMH](http://openjdk.java.net/projects/code-tools/jmh/) performance tests.

Note that this module is an optional part of the overall project build and does not deploy anything, due to its use
of JMH which is not permissively licensed. The module must be built directly.

Building the benchmarks
-------
The benchmarks are maven built and involve some code generation for the JMH part. As such it is required that you
rebuild upon changing the code.

    mvn clean install

Running the benchmarks: General
-------
It is recommended that you consider some basic benchmarking practices before running benchmarks:

 1. Use a quiet machine with enough CPUs to run the number of threads you mean to run.
 2. Set the CPU freq to avoid variance due to turbo boost/heating.
 3. Use an OS tool such as taskset to pin the threads in the topology you mean to measure.

Running the JMH Benchmarks
-----
To run all JMH benchmarks:

    java -jar target/benchmark.jar

To list available benchmarks:

    java -jar target/benchmark.jar -l
Some JMH help:

    java -jar target/benchmark.jar -h

Example
-----
To run a benchmark on a single thread (tg 1) with gc profiling use:

    java -jar target/benchmark.jar -prof gc -tg 1

