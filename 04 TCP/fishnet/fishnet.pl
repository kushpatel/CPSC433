#!/usr/local/bin/perl

# Simple script to start Fishnet

main();

sub main {
    
    $classpath = "bin";
    
    $fishnetArgs = join " ", @ARGV;

    exec("nice -n 19 java -cp $classpath lib/Fishnet $fishnetArgs");
    # exec("nice java -cp $classpath Fishnet $fishnetArgs");
}

