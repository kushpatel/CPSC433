#!/bin/bash

# Names of output files
seq_output="output/SequentialServer.txt"
comp_thread_output="output/CompetingThreadsServer.txt"
request_thread_output="output/PerRequestThreadServer.txt"
busy_wait_output="output/SharedQueueBusyWaitServer.txt"
suspension_output="output/SharedQueueSuspensionServer.txt"
async_output="output/AsyncServer.txt"
apache_output="output/ApacheServer.txt"
outputs_array=($seq_output $comp_thread_output $request_thread_output $busy_wait_output $suspension_output $async_output $apache_output)

# Names of transactions throughput output files
seq_txn="output/Sequential_txn.txt"
comp_thread_txn="output/CompetingThreads_txn.txt"
request_thread_txn="output/PerRequestThread_txn.txt"
busy_wait_txn="output/SharedQueueBusyWait_txn.txt"
suspension_txn="output/SharedQueueSuspension_txn.txt"
async_txn="output/Async_txn.txt"
apache_txn="output/Apache_txn.txt"
txns_array=($seq_txn $comp_thread_txn $request_thread_txn $busy_wait_txn $suspension_txn $async_txn $apache_txn)

# Names of data throughput output files
seq_thp="output/Sequential_thp.txt"
comp_thread_thp="output/CompetingThreads_thp.txt"
request_thread_thp="output/PerRequestThread_thp.txt"
busy_wait_thp="output/SharedQueueBusyWait_thp.txt"
suspension_thp="output/SharedQueueSuspension_thp.txt"
async_thp="output/Async_thp.txt"
apache_thp="output/Apache_thp.txt"
thps_array=($seq_thp $comp_thread_thp $request_thread_thp $busy_wait_thp $suspension_thp $async_thp $apache_thp)

# Names of average wait output files
seq_wait="output/Sequential_wait.txt"
comp_thread_wait="output/CompetingThreads_wait.txt"
request_thread_wait="output/PerRequestThread_wait.txt"
busy_wait_wait="output/SharedQueueBusyWait_wait.txt"
suspension_wait="output/SharedQueueSuspension_wait.txt"
async_wait="output/Async_wait.txt"
apache_wait="output/Apache_wait.txt"
waits_array=($seq_wait $comp_thread_wait $request_thread_wait $busy_wait_wait $suspension_wait $async_wait $apache_wait)

# Array of number of client threads over which to run the the tests
threads=(1 2 5 10 15 20 30 50 70 100 150 200 300)

# Clear contents of the files first if they exist and stick in header line
for i in {0..6}
do
    cat /dev/null > ${txns_array[$i]}
    cat /dev/null > ${thps_array[$i]}
    cat /dev/null > ${waits_array[$i]}

    printf "%s\n" "Test_Time Transactions_per_second" >> ${txns_array[$i]}
    printf "%s\n" "Test_Time Megabytes_per_second">> ${thps_array[$i]}
    printf "%s\n" "Test_Time Milli_seconds">> ${waits_array[$i]}
done


# Reformat contents of output file and write to transaction, throughput and average wait files for plotting with gnuplotter
for i in {0..6}
do
    output_file=${outputs_array[$i]}
    txn_file=${txns_array[$i]}
    thp_file=${thps_array[$i]}
    wait_file=${waits_array[$i]}

    threadIdx=0
    grep '(#transactions per second): ' $output_file | while read line; do
        printf "%d " "${threads[$threadIdx]}" >> $txn_file
        echo $line | cut -d: -f2 -s | cut -d' ' -f2 >> $txn_file
        threadIdx=$((threadIdx+1))
    done

    threadIdx=0
    grep '(#megabytes per second): ' $output_file | while read line; do
        printf "%d " "${threads[$threadIdx]}" >> $thp_file
        echo $line | cut -d: -f2 -s | cut -d' ' -f2 >> $thp_file
        threadIdx=$((threadIdx+1))
    done

    threadIdx=0
    grep '(ms): ' $output_file | while read line; do
    printf "%d " "${threads[$threadIdx]}" >> $wait_file
    echo $line | cut -d: -f2 -s | cut -d' ' -f2 >> $wait_file
    threadIdx=$((threadIdx+1))
    done

done