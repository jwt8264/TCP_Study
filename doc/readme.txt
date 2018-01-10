Jason Tu
Data Comm & Network
Apr 30, 2017
Project 2

fcntcp

    Compilation:

To compile fcntcp, navigate to the jwt8264 folder and run the command:

javac -d bld $(find . -name *.java)

When running from a script, you will likely need to put quotes around *.java

    Execution:

To run fcntcp, navigate to the bld folder, and run the command

java fcntcp -{c,s} [options] [server address] port

For a description of the options and arguments, run the command

java fcntcp

