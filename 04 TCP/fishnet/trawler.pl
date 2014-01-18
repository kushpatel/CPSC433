#!/usr/local/bin/perl

# Simple script to start Trawler

main();

sub main {
    
    $classpath = "bin";
    
    $fishnetArgs = join " ", @ARGV;

    exec("nice -n 19 java -cp $classpath lib/Trawler $fishnetArgs");
}
