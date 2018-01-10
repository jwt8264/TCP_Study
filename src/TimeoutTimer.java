/**
    TimeoutTimer.java
    This class provides a timeout timer for a packet. 
*/
import java.io.IOException;

public class TimeoutTimer extends Thread implements Runnable
{
    private Client client;
    private int timeout;
    private int seqNum;
    private boolean gotAck;

    /**
        TimeoutTimer
        Construct a new timoeout
        @param c - the client the timeout belongs to
        @param t - the length of the timeout in ms
        @param seq - the sequence number of this timeout
    */
    public TimeoutTimer(Client c, int t, int seq)
    {
        client = c;
        timeout = t;
        seqNum = seq;
        gotAck = false;
    }

    /**
        run()
        the main run function of this timeout. 
    */
    public void run()
    {
        try
        {
            Thread.sleep(timeout);
            if (!gotAck)
            {
                client.packetTimedOut(seqNum);
            }
        }
        catch (InterruptedException e){}
        catch (IOException e){}
    }
    
    /**
        seqNum()
        get the sequence number of this timeout
    */
    public int seqNum(){return seqNum;}

    /**
        equals()
        determine if this timeout is equivalent to another timeout.
        only compares sequence number.
    */
    @Override
    public boolean equals(Object o)
    {
        if (o == null) {return false;}
        if (!(o instanceof TimeoutTimer)) {return false;}
        TimeoutTimer t = (TimeoutTimer)o;
        return seqNum == t.seqNum();
    }
}
