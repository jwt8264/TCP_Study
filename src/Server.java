/**
    Server.java
    @author Jason Tu <jwt8264@rit.edu>

    This class provides the server side functionality of fcntcp
*/
import java.io.IOException;

import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;

import java.util.Random;
import java.util.LinkedList;
import java.util.Collections;
import java.util.Comparator;

public class Server
{
    private int port = -1;
    private DatagramSocket socket;

    private InetAddress clientAddr;
    private int clientPort = -1;
    private int timeout = 1000;

    private int ackNum = 0;
    private int nextSeq = 0; // next sequence number we expect
    private int seqNum = 0;

    private int window = 5000 * 1000;

    private double chanceToDropAcks = 0.0; // with debug turned on , the chance to drop acks

    private MD5Summer md5 = new MD5Summer();
    LinkedList<Packet> buffer = new LinkedList<Packet>();

    /**
        Server 
        constructor for a new server object
    */
    public Server(int p)
    {
        port = p;
    }

    /**
        start()
        the main function of the server. this will start listening on the 
        port and await connections
    */
    public void start() throws IOException
    {
        socket = new DatagramSocket(port);
        DatagramPacket datagram;
        Packet packet;
        int actualLen = -1;
        byte[] data;
        byte[] packetContents;
        byte[] packetBytes;
        printv("Server started! ");
        while(true)
        {
        
            boolean connected = false;
            while (!connected)
            {
                connected = handshake();
            }
            printv("Hands shaken! Waiting for data");
            boolean done = false;
            while (!done)
            {
                data = new byte[Packet.MTU];
                datagram = new DatagramPacket(data, data.length);
                socket.receive(datagram);
                actualLen  = datagram.getLength();
                packetContents = null;
                if (Packet.MTU != actualLen)
                {
                    // packet is smaller than max size, trim excess
                    packetBytes = datagram.getData();
                    packetContents = new byte[actualLen];
                    for (int i = 0; i < actualLen; i++)
                    {
                        packetContents[i] = packetBytes[i];
                    }
                }
                else
                {
                    packetContents = datagram.getData();
                }
                // TODO move setting client fields to the handshake
                clientAddr = datagram.getAddress();
                clientPort = datagram.getPort();
                packet = new Packet(packetContents);
                printv("Received packet "+packet.seqNum());
                if (packet.checksum() && packet.fin())
                {
                    while (!done)
                    {
                        done = finshake(packet);
                    }
                    connected = false;
                    ackNum = 0;
                    nextSeq = 0; 
                    seqNum = 0;
                    System.out.println(md5.getResult());
                    continue;
                }
                if (packet.checksum() && packet.seqNum() == nextSeq)
                {
                    nextSeq += packet.dataLen();
                    md5.next(packet.data(), 0, packet.dataLen());
                    processBuffer();

                }
                else if (packet.checksum() && packet.seqNum() > nextSeq && !buffer.contains(packet))
                {
                    buffer.add(packet);
                }
                sendAck(nextSeq);
            }
        }
    }

    /**
        processBuffer()
        process the packets that have been buffered, if possible. 
    */
    private synchronized void processBuffer()
    {
        if (buffer.size() == 0)
        {
            return;
        }
        Collections.sort(buffer, new Comparator<Packet>() {
            @Override
            public int compare(Packet a, Packet b) {
                return a.seqNum() - b.seqNum();
            }
        });
        LinkedList<Packet> toRemove = new LinkedList<Packet>();
        for (Packet p : buffer)
        {
            if (p.seqNum() == nextSeq)
            {
                nextSeq += p.dataLen();
                md5.next(p.data(), 0, p.dataLen());
                toRemove.add(p);
            }
            else
            {
                break;
            }
        }
        for (Packet p : toRemove)
        {
            buffer.remove(p);
        }
    }

    /**
        sendAck()
        send an ack to the client.
        @param seqNum - the sequence number we are ack'ing
    */
    private synchronized void sendAck(int seqNum) throws IOException
    {
        if (fcntcp.debug)
        {
            Random rand = new Random();
            if (rand.nextDouble() < chanceToDropAcks)
                return;
        }
        Packet ack = new Packet(
             0 // seq num
            ,seqNum // ack num
            ,1,0,0,0 // ack, rst, syn, fin
            ,window // window
            ,null,0 // data, datalen
        );
        byte[] ackBytes = ack.getBytes();
        DatagramPacket datagram = new DatagramPacket
            (ackBytes, ackBytes.length, clientAddr, clientPort);
        printv("Sending ack "+seqNum);
        socket.send(datagram);
    }

    /**
        handshake()
        wait for and perform a handshake. 
        @return true if connected, false otherwise
    */
    private synchronized boolean handshake() 
    {
        try
        {
            byte[] data = new byte[Packet.headerLen];
            DatagramPacket datagram = new DatagramPacket(data, data.length);
            // receive a syn request
            socket.receive(datagram);
            clientAddr = datagram.getAddress();
            clientPort = datagram.getPort();
            Packet syn = new Packet(datagram.getData() );
            if (syn.checksum() && syn.syn() )
            {
                printv("Got a SYN request");
                // we got a syn request
                // make synack response
                Packet synAck = new Packet(
                     seqNum // seq num TODO
                    ,ackNum++ // ack num
                    ,1,0,1,0 // ack, rst, syn, fin
                    ,window // window
                    ,null,0 // data, datalen
                );
                byte[] synAckBytes = synAck.getBytes();
                datagram= new DatagramPacket(synAckBytes, synAckBytes.length, clientAddr, clientPort);
                //try{Thread.sleep(500);}catch(InterruptedException e){}
                socket.send(datagram);
                printv("Sent SYNACK.");
                // wait for ack
                data = new byte[Packet.headerLen];
                datagram= new DatagramPacket(data, data.length);
                socket.setSoTimeout(2*timeout);
                socket.receive(datagram);
                socket.setSoTimeout(0);
                Packet ack = new Packet(datagram.getData());
                printv(ack.toString());
                if (ack.checksum() && ack.ack() && ack.seqNum() == nextSeq && !ack.syn() && !ack.fin())
                {
                    printv("Connection established");
                    return true;
                }
                printv("Not a valid ACK");
            }
            printv("Not a valid SYN request");
        }
        catch (SocketTimeoutException e){}
        catch (IOException e){}
        return false;
    }

    /**
        finshake()
        do the fin procedure. 
        @param fin - the first packet that had the fin flag set.
    */
    private synchronized boolean finshake(Packet fin)
    {
        // received fin already
        // send ack
        Packet ack = new Packet(
            seqNum // seq num
            ,fin.seqNum()+1 // ack num
            ,1,0,0,0 // ack, rst, syn, fin
            ,window // window 
            ,null, 0 // data, datalen
        );
        try
        {
            byte[] data = ack.getBytes();
            DatagramPacket datagram = new DatagramPacket(data, data.length, clientAddr, clientPort);
            socket.send(datagram);
            return true;
        }
        catch (IOException e){}
        return false;
    }

    /**
        print()
        print a message to the console
        @param s - the string to print
    */
    private synchronized void print(String s)
    {
        System.out.println("Server: "+s);
        //if (fcntcp.debug){System.out.println("Server: "+s);}
    }

    /**
        printv()
        print a message to the console when verbose is turned on 
        @param s - the string to print
    */
    private synchronized void printv(String s)
    {
        if (fcntcp.verbose){System.out.println("Server: "+s);}
    }
}
