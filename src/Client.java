/**
    Client.java
    @author Jason Tu <jwt8264@rit.edu>

    This class provides the main functionality of the client side of fcntcp.
*/
import java.io.FileInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import java.net.InetAddress;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.UnknownHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import java.util.LinkedList;
import java.util.Random;

public class Client
{
    private static final int SLOW_START = 1;
    private static final int CONG_AVOID = 2;
    private static final int FAST_REC =   3;
    private static final int HANDSHAKE =  4;
    private int congestionState = SLOW_START;

    private double chanceToDropPackets = 0.1;
    private double packetDelay = 1100;

    private InetAddress server;
    private int port;
    private String file;
    private int timeout;

    private int cwnd = 2 * Packet.MSS; 
    private int rwnd = Integer.MAX_VALUE;
    private int ssthresh = 64000; // slow start threshold of 64k

    private int bytesInFlight = 0; // how many bytesInFlight are in flight
    private int seqNum = 0; // sequence num of outgoing packets
    private int lastAck = 0; // last ack number received
    private int dupAcks = 0; // how many times the last ack has been repeated
    private boolean finished = false;
    private boolean connected = false;
    private int finalByte = -1;

    private DatagramSocket socket;

    private LinkedList<Packet> packets; // the outgoing packets queue
    private LinkedList<TimeoutTimer> timeouts; // the timeouts for all sent packets
    private PacketListener listener; // a listener for incoming messages
    private Thread listenerThread; // a thread to run the listener
    private FileInputStream instream; // filestream to read the file we send

    /**
        Client()

        Constructor to create a new client object
    */
    public Client(String s, int p, String f, int t) throws UnknownHostException
    {
        server = InetAddress.getByName(s);
        port = p;
        file = f;
        timeout = t;

        packets = new LinkedList<Packet>();
        timeouts = new LinkedList<TimeoutTimer>();
    }

    /**
        start()
        The main function of the client. initiates the connection and necessary
        objects, and transfers the file. 
    */
    public synchronized void start() throws FileNotFoundException, SocketException, IOException, InterruptedException
    {
        instream = new FileInputStream(new File(file));
        socket = new DatagramSocket();
        listener = new PacketListener(this);
        listenerThread = new Thread(listener);
        listenerThread.start();
        while (!connected)
        {
            connected = handshake();
        }
        sendNext();
    }

    /**
        handshake()
        do the handshake to initialize the connection with the server
        @return true if connect, false otherwise
    */  
    private synchronized boolean handshake()
    {
        try
        {
            // send SYN
            Packet syn = new Packet(
                seqNum // seqNum
                ,0 // ackNum
                ,0,0,1,0 //ack, rst, syn, fin
                ,0 // window
                ,null,0 // data, dataLen
            );
            byte[] data = syn.getBytes();
            DatagramPacket datagram = new DatagramPacket(data, data.length, server, port);
            socket.send(datagram);
            // receive SYNACK
            socket.setSoTimeout(2 * timeout);
            data = new byte[Packet.headerLen];
            datagram = new DatagramPacket(data, data.length);
            socket.receive(datagram);
            socket.setSoTimeout(0);
            Packet synack = new Packet(datagram.getData());
            if (synack.checksum() && synack.syn() && synack.ack())
            {
                // send ACK
                Packet ack = new Packet(
                    seqNum // seqNum
                    ,synack.seqNum() // ackNum
                    ,1,0,0,0 // ack, rst, syn, fin
                    ,0  // window
                    ,null,0 // data, dataLen
                );
                data = ack.getBytes();
                datagram = new DatagramPacket(data, data.length, server, port);
                socket.send(datagram);
                print("Connection established");
                return true;
            }
        }
        catch (SocketTimeoutException e){}
        catch (IOException e){}
        return false;
    }

    /**
        sendNext()
        send the next packets if possible. 
    */
    private synchronized void sendNext() throws IOException, InterruptedException
    {
        if (finished)
        {
            return;
        }
        int maxBytes = rwnd < cwnd ? rwnd : cwnd - Packet.MSS;
        printv("max bytes is "+maxBytes+" and bytes in flight is "+bytesInFlight);
        while (instream != null && bytesInFlight < maxBytes)
        {
            byte[] data = new byte[Packet.MSS];
            int bytesRead = instream.read(data);
            if (bytesRead != -1 )
            {
                Packet p = new Packet(
                     seqNum //seq number
                    ,0 // ack number
                    ,0,0,0,0 // ack,rst,syn,fin flags
                    ,0 // window
                    ,data // data
                    ,bytesRead // data len
                );
                seqNum += bytesRead;
                printv("Sending new packet");
                sendPacket(p);
                bytesInFlight += bytesRead;
            }
            else
            {
                finalByte = seqNum;
                printv("last byte is "+finalByte);
                instream.close();
                instream = null;
            }

        }
    }

    /**
        finish()
        various clean up as well as the fin procedure
    */
    private synchronized void finish() throws InterruptedException
    {
        printv("finishing!");
        finished = true;
        listenerThread.interrupt();
        for (TimeoutTimer t : timeouts)
        {
            t.interrupt();
        }
        timeouts = null;
        packets = null;
        cwnd = 0;

        while (connected)
        {
            try
            {
                // send FIN
                Packet fin = new Packet(
                    seqNum++ // seq num
                    ,0 // ack num
                    ,0,0,0,1 // ack, rst, syn, fin
                    ,0 // window
                    ,null,0 // data, datalen
                );
                byte[] data = fin.getBytes();
                DatagramPacket datagram = new DatagramPacket(data, data.length, server, port);
                socket.setSoTimeout(2*timeout);
                socket.send(datagram);
                data = new byte[Packet.headerLen];
                datagram = new DatagramPacket(data, data.length);
                // receive ACK
                printv("waiting for ack");
                socket.receive(datagram);
                socket.setSoTimeout(0);
                Packet ack = new Packet(datagram.getData());
                if (ack.checksum() && ack.ack() && seqNum == ack.ackNum())
                {
                    connected = false;
                }
            }
            catch(SocketTimeoutException e){}
            catch(IOException e){}
        }
        socket.close();
        printv("finished....all done");
        System.exit(0);
    }

    /**
        sendPacket()
        send a packet to the server. starts a timeout and queues this packet.
        with debug turned on, this will drop and delay packets. 
        @param packet - the packet to send
    */
    private synchronized void sendPacket(Packet packet) throws IOException, InterruptedException
    {

        if (finished)
        {
            return;
        }
        byte[] data = packet.getBytes();
        DatagramPacket datagram = new DatagramPacket(data, data.length, server, port);
        TimeoutTimer timer = new TimeoutTimer(this, timeout, packet.seqNum());
        printv("Sending packet "+packet.seqNum());
        if (fcntcp.debug)
        {
            Random rand = new Random();
            // drop packets occasionally
            if (rand.nextDouble() > chanceToDropPackets)
            {
                    new Thread(new Runnable(){
                        public void run()
                        {
                            try
                            {
                                double val = rand.nextGaussian();
                                if (val < 0)
                                    val = val * -1;
                                val = val * packetDelay;
                                Thread.sleep((int)val);
                                socket.send(datagram);
                            }
                            catch (IOException e){}
                            catch (InterruptedException e){}
                            
                        }
                    }).start();
            }
        }
        else
        {
            socket.send(datagram);
        }
        
        if (!packets.contains(packet))
        {
            packets.add(packet);
        }
        while (timeouts.contains(timer))
        {
            int index = timeouts.indexOf(timer);
            if (index != -1)
            {
                //poo
                (timeouts.remove(index)).interrupt();
            }
        }
        timer.start();
        timeouts.add(timer);
    }

    /**
        packetTimedOut
        notify this client that a packet timed out. if the packet has not been 
        ack'd, the packet will be resent. changes to congestion state will also
        be made if necessary.
        @param seqNum - the sequence num of the packet that timed out
    */
    public synchronized void packetTimedOut(int seqNum) throws IOException, InterruptedException
    {
        printv("Packet "+seqNum+" timed out!");
        if (lastAck > seqNum || finished)
        {
            printv("Ignoring that...seqNum is "+seqNum);
            return;
        }
        if (congestionState == CONG_AVOID || congestionState == FAST_REC)
        {
            print("Going to slow start");
            congestionState = SLOW_START;
            halfCWND();
            ssthresh = cwnd;
            cwnd = ssthresh + 3 * Packet.MSS;
        }
        else if (congestionState == SLOW_START)
        {
            halfCWND();
            ssthresh = cwnd;
        }
        congState();
        Packet temp = new Packet(seqNum);
        int index = packets.indexOf(temp);
        if (index == -1)
        {
            printv("Packet not found...ignoring");
            return;
        }
        Packet p = packets.remove(index);
        printv("Resending "+seqNum);
        sendPacket(p);
    }

    /**
        gotAck()
        notify this client that an ack was received. this will remove all old
        timeouts and queued packets and make changes to congestion state as needed.
        if the ack was the last one we are expecting, this will initiate the 
        finish procedure.
        @param ackNum - the ack number received
    */
    public synchronized void gotAck(int ackNum) throws IOException, InterruptedException
    {
        printv("Got ack # "+ackNum);
        // stop and remove old timeouts
        for (int i = timeouts.size()-1; i >= 0; i--)
        {
            int s = timeouts.get(i).seqNum();
            if (s < ackNum)
            {
                //poo
                timeouts.remove(i).interrupt();
            }
        }
        // remove old packets
        for (int i = packets.size()-1; i >= 0; i--)
        {
            int s = packets.get(i).seqNum();
            if (s < ackNum)
            {
                bytesInFlight -= packets.remove(i).dataLen();
            }
        }
        if (ackNum == finalByte)
        {
            printv("That was the last ack!");
            finish();
            return;
        }
        if (ackNum == lastAck)
        {
            dupAcks ++;
            if (congestionState == FAST_REC)
            {
                cwnd += Packet.MSS;
            }
            else
            {
                print("Going to fast recovery");
                congestionState = FAST_REC;
                halfCWND();
                ssthresh = cwnd;
                cwnd = ssthresh + 3 * Packet.MSS;
            }
        }
        else
        {
            if (congestionState == FAST_REC)
            {
                print("Going to congestion avoidance");
                congestionState = CONG_AVOID;
            }
            dupAcks = 0;
            incrementCWND();
        }
        lastAck = ackNum;
        if (dupAcks >= 3)
        {
            printv("fast retransmit of "+ackNum);
            Packet p = new Packet(ackNum);
            sendPacket(packets.get(packets.indexOf(p)));
        }
        else
        {
            sendNext();
        }
        congState();
    }

    /**
        setRWND()
        set the servers receive window. 
        @param r - the size in bytes of the receive window
    */
    public synchronized void setRWND(int r)
    {
        printv("setting rwnd to "+r);
        rwnd = r;
    }

    /**
        incrementCWND()
        increase the size of the congestion window. depending on the congestion state,
        the cwnd will be increased by a certain amount. 
    */
    private synchronized void incrementCWND()
    {
        if (congestionState == SLOW_START)
        {
            cwnd += Packet.MSS;
            if (cwnd > ssthresh)
            {
                print("Moving to congestion avoidance");
                congestionState = CONG_AVOID;
                congState();
            }
        }
        else if (congestionState == CONG_AVOID)
        {
            cwnd = cwnd + (int)(Packet.MSS * (1.0*Packet.MSS/cwnd));
        }
        else if (congestionState == FAST_REC)
        {
            cwnd = ssthresh;
        }
    }

    /**
        halfCWND()
        half the size of the congestion window.
    */
    private synchronized void halfCWND()
    {
        if (cwnd <= 2 * Packet.MSS)
        {
            return;
        }
        if (cwnd % Packet.MSS != 0)
        {
            cwnd -= Packet.MSS;
        }
        cwnd /= 2;
    }

    /**
        getSocket()
        get the socket that the client is connected to the server with.
    */  
    public synchronized DatagramSocket getSocket()
    {
        return socket;
    }

    /**
        PacketListener
        this class will listen for incoming packets, and notify the client
        of any events necessary based on the contents of the packet. 
    */
    private class PacketListener implements Runnable
    {
        private Client client;
        private volatile boolean done = false;
        public PacketListener(Client c)
        {
            client = c;
        }
        public void done(){ done = true;}
        public void run()
        {
            DatagramSocket socket = client.getSocket();
            DatagramPacket datagram;
            Packet packet;
            byte[] data;
            try
            {
                while (!done)
                {
                    data = new byte[Packet.MTU];
                    datagram = new DatagramPacket(data, data.length);
                    socket.receive(datagram);
                    // printv("Received a packet!");
                    packet = new Packet(datagram.getData());
                    if (packet.checksum())
                    {
                        client.setRWND(packet.window());
                        if (packet.ack()) // only really care about acks
                        {
                            // printv("Got ack " + packet.ackNum());
                            client.gotAck(packet.ackNum());
                        }
                    }
                }
            }
            catch (InterruptedException e)
            {e.printStackTrace();}
            catch (IOException e)
            {e.printStackTrace();}
        }
    }

    /**
        congState()
        print a detailed report of congestion state 
    */
    private synchronized void congState()
    {
        if (congestionState == SLOW_START)
            printv("\tSlow start");
        else if (congestionState == CONG_AVOID)
            printv("\tCongestion avoidance");
        else if (congestionState == FAST_REC)
            printv("\tFast recovery");
        else
            printv("\tunknown state = "+congestionState);
        printv("\tcwnd = "+cwnd);
        printv("\tssthresh = "+ssthresh);
        printv("\tdup acks = "+dupAcks);
    }

    /**
        printv()
        print a message to the console when verbose is turned on
        @param s - the string to print
    */
    private synchronized void printv(String s)
    {
        if (fcntcp.verbose){System.out.println("Client: "+s);}
    }

    /**
        print()
        print a message to the console
        @param s - the string to print
    */
    private synchronized void print(String s)
    {
        System.out.println("Client: "+s);
        //if (fcntcp.debug){System.out.println("Client: "+s);}
    }
}
