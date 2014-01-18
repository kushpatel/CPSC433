Directory Structure
====================

The root directory contains:
	-> All the java source code files:
		compile locally by javac *.java
	-> apacheRequests:
		A list of files (with Pareto distribution) to be requested from Apache server. The list is the same as in requests file, the only difference being the url path to each file.
	-> requests:
		A list of files (with Pareto distribution) to be requested from all my servers. The list is the same as in apacheRequests file, the only difference being the url path to each file.
	-> server_config:
		The configuration file for my servers that specifies port, document root, thread pool size, cache size, plugin loadbalancer class etc.
	-> benchmark.sh:
		The benchmarking script that runs on all my servers and the Apache server.
		usage: ./benchmark.sh [TEST_TIME]  default TEST_TIME = 10 seconds
		Starts an instance of all my servers and executes benchmarking using my SHTTPTestClient. Benchmark results are dumped into ./output directory.
		Throughput results from ./output can be plotted using gnuplot with the following command in gnuplot:
		plot "Sequential_thp.txt" using 1:2 with lines, "CompetingThreads_thp.txt" using 1:2 with lines, "PerRequestThread_thp.txt" using 1:2 with lines,"SharedQueueBusyWait_thp.txt" using 1:2 with lines, "SharedQueueSuspension_thp.txt" using 1:2 with lines, "Async_thp.txt" using 1:2 with lines, "Apache_thp.txt" using 1:2 with lines
	-> gen:
		NOTE: not included in this jar because of its size. Needs to be downloaded from http://zoo.cs.yale.edu/classes/cs433/cs433-2013-fall/assignments/assign3/gen.tar (No changes in the directory structure)
		Contains all of the files that the sample benchmarking requests from my servers.
	-> output:
		Stores results from benchmarking (see description for benchmark.sh)
	-> report.pdf:
		A report on my server design, performance metrics, plots etc.