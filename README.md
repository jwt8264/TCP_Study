# TCP Study

This project was an individual final project that I completed for my computer networking course.
##Summary
This program sends a file over the network using a TCP-like transfer protocol, built using only UDP. I have implemented multiple facets of reliable data transfer: checksum checking, flow control, congestion control, to overcome unreliable connections. The file will transmit successfully even with lost, corrupted, or out-of-order packets. The file is read in through an input stream, and sends small chunks. As the server recieves pieces of the file, an MD5 sum is calculated/updated. When the server has recieved all of the packets, it will print the final MD5 sum, which should match the MD5 sum of the original file. The entire file itself is not stored on the server, only the MD5 sum is calculated. Packets are thrown away by the server once they are no longer needed.

##How to compile
run the script called "go"

or

run the command `javac -d bld $(find . -name "*.java")`

##How to run
You will need two instances of the program running, a client to send the file and a server to recieve. 
###Execution options
go to the bld folder

run `java fcntcp` to display a list of arguments

###Constraints
The program was designed to transmit 1-4MB files, so larger files might not work. Smaller files will probably work. 
###Start the server first
go to the bld folder

run `java fcntcp -s <port>` and give it an available port.
###Start the client
in a second terminal, go to the bld folder

run `java fcntcp -c -f <file> <server address> <port>` and supply the file you are sending, the address of the server started in the previous step, and the port that it is listening on. 

##How do I know it worked
Calculate the MD5 sum of the original file you are sending, either by hand, or use a diff. The MD5 output by the server instance of the program should match the MD5 that you calculated. 

If you don't want to do that, then you can use the file `test1M.bin` as the file to send, and check against the checksum contained in `test1M.bin.md5`. This test data was given with our assignment. 