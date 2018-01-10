/**
    Packet.java
    @author Jason Tu <jwt8264@rit.edu>

    This class provides a wrapper for an fcntcp packet. It provides several 
    useful operations that can be performed on a packet. 
*/


    /*
        bytes in an fcntcp packet
    0                   1                   2                   3
    0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   |                               0                               | 0 - 3
   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   |                        Sequence Number                        | 4 - 7
   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   |                    Acknowledgment Number                      | 8 - 11
   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   |               |     |A| |R|S|F|                               |
   |        0      |  0  |C|0|S|Y|I|            Window             | 12 - 15
   |               |     |K| |T|N|N|                               |
   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   |           Checksum            |               0               | 16 - 19
   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   |                             data                              | 20 + 
   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+

    */

public class Packet
{
    private int seqNum;
    private int ackNum;
    private int ack;
    private int rst;
    private int syn;
    private int fin;
    private int window;
    private byte[] checksum;
    private byte[] data;
    private int dataLen;

    private boolean receiverAckd = false;

    public static final int headerLen = 20;
    public static final int MSS = 1000;
    public static final int MTU = MSS + headerLen;

    /**
        Packet()
        Construct a new packet with only a sequence number. This should ONLY
        be used when a packet is needed for comparison. This will not create a 
        valid packet for anything other than packet equality checking and 
        comparison. 
    */
    public Packet(int seqNum)
    {
        this.seqNum = seqNum;
    }

    /**
        Packet()
        Construct a new packet with the given attributes.
        @param seqNum - the sequence number of this packet
        @param ackNum - the ack number of the packet
        @param ack - the ack flag (1 to set, 0 to unset)
        @param rst - the rst flag (1 to set, 0 to unset)
        @param syn - the syn flag (1 to set, 0 to unset)
        @param fin - the fin flag (1 to set, 0 to unset)
        @param window - the receiving window size
        @param data - the bytes of data to send
        @param dataLen - the length of the data bytes
    */
    public Packet(int seqNum, int ackNum, int ack, int rst, int syn,
        int fin, int window, byte[] data, int dataLen)
    {
        this.seqNum = seqNum;
        this.ackNum = ackNum;
        this.ack = ack;
        this.rst = rst;
        this.syn = syn;
        this.fin = fin;
        this.window = window;
        this.data = data;
        this.dataLen = dataLen;
    }

    /**
        Packet()
        Construct a new packet from a byte array. This will extract the header
        values and data from the byte array.
    */
    public Packet(byte[] bytes)
    {
        // printHeader(bytes);

        // ignore bytes 0-3
        int offset = 4;
        // get seq num from bytes 4-7
        this.seqNum = NetworkServices.bytesToInt(bytes, offset);
        offset += 4;
        // get ack num from bytes 8-11
        this.ackNum = NetworkServices.bytesToInt(bytes, offset);
        offset += 4;
        // ignore byte 12
        offset += 1;
        // get flags from byte 13
        byte flags = bytes[offset++];
        this.ack = 0;
        this.rst = 0;
        this.syn = 0;
        this.fin = 0;
        if ((flags & 0x10) != 0) { this.ack = 1; }
        if ((flags & 0x04) != 0) { this.rst = 1; }
        if ((flags & 0x02) != 0) { this.syn = 1; }
        if ((flags & 0x01) != 0) { this.fin = 1; }
        // get window from bytes 14-15
        window = (NetworkServices.bytesToInt(bytes, offset-2) & 0x0000ffff);
        // get checksum from bytes 16-17
        checksum = new byte[2];
        checksum[0] = bytes[offset++];
        checksum[1] = bytes[offset++];
        // payload processing
        if (bytes.length == headerLen)
        {
            data = null;
            dataLen = 0;
        }
        else
        {
            dataLen = bytes.length - headerLen;
            data = new byte[dataLen];
            for (int i = headerLen; i < bytes.length; i++)
            {
                data[i - headerLen] = bytes[i];
            }
        }
    }

    /**
        printHeader()
        print the header bytes as bit strings
        @param bytes - the bytes of the packet/header
    */
    private void printHeader(byte[] bytes)
    {
        if (!fcntcp.debug)
            return;
        System.out.println("Packet header:");
        for (int i = 0; i < 5; i ++)
        {
            System.out.println(NetworkServices.byteArrToString(bytes, i*4, 4));
        }
    }

    /**
        getBytes()
        get this packet as a byte array
        @return - the bytes of thsi packet
    */
    public byte[] getBytes()
    {
        checksum = new byte[2];

        byte[] header = new byte[headerLen];
        // leave bytes 0-3 zero'd out
        // set bytes 4-7 with sequence number
        int offset = 4;
        byte[] seqNumBytes = NetworkServices.intToBytes(seqNum);
        for (int i = offset; i < offset+4; i++)
        {
            header[i] = seqNumBytes[i-offset];
        }
        offset += 4;
        // set bytes 8-11 with ack num
        byte[] ackNumBytes = NetworkServices.intToBytes(ackNum);
        for (int i = offset; i < offset+4; i ++)
        {
            header[i] = ackNumBytes[i-offset];
        }
        offset += 4;
        // ignore byte 12
        offset++;
        // set byte 13 with rest of reserved and flags
        byte flags = 0;
        if (ack == 1) { flags = (byte)(flags | 0x10);}
        if (rst == 1) { flags = (byte)(flags | 0x04);}
        if (syn == 1) { flags = (byte)(flags | 0x02);}
        if (fin == 1) { flags = (byte)(flags | 0x01);}
        header[offset++] = flags;
        // set bytes 14-15 with window
        byte[] windowBytes = NetworkServices.intToBytes(window);
        header[offset++] = windowBytes[2];
        header[offset++] = windowBytes[3];
        // set bytes 16-17 with checksum

        // TODO generate checksum
        checksum = new byte[2];

        header[offset++] = checksum[0];
        header[offset++] = checksum[1];
        // leave bytes 18-19 zero
        // header is done



        if (dataLen == 0)
        {
            return header;
        }
        byte[] completePacket = new byte[headerLen+dataLen];
        for (int i = 0; i < headerLen; i++)
        {
            completePacket[i] = header[i];
        }
        for (int i = 0; i < dataLen; i++)
        {
            completePacket[i+headerLen] = data[i];
        }
        return completePacket;
    }

    // simple setters/getters
    public int seqNum(){return seqNum;}
    public int ackNum(){return ackNum;}
    public boolean ack(){return ack == 1;}
    public boolean rst(){return rst == 1;}
    public boolean syn(){return syn == 1;}
    public boolean fin(){return fin == 1;}
    public int window(){return window;}
    public byte[] data(){return data;}
    public int dataLen(){return dataLen;}
    public void setReceiverAckd(){receiverAckd = true;}
    public boolean receiverAckd(){return receiverAckd;}

    /**
        checksum()
        determine if this packet passes a checksum checksum
        @return true if uncorrupted packet, false if corrupted
    */
    public boolean checksum()
    {
        int length = dataLen;
        int i = 0;
        long sum = 0;
        while (length > 0) {
            sum += (data[i++]&0xff) << 8;
            if ((--length)==0) break;
            sum += (data[i++]&0xff);
            --length;
        }
        long result = (~((sum & 0xFFFF)+(sum >> 16)))&0xFFFF;
        return true;
    }

    /**
        toString()
        get a string representing this packet. 
    */
    public String toString()
    {
        // String bytes = "Bytes:\n:";
        // for (int j = 0; j < data.length)
        return
            "seqNum    = "   + seqNum   +
            "\nackNum    = " + ackNum   +
            "\nack       = " + ack      +
            "\nrst       = " + rst      +
            "\nsyn       = " + syn      +
            "\nfin       = " + fin      +
            "\nwindow    = " + window   +
            "\nchecksum  = " + (checksum == null ? "" : NetworkServices.byteArrToString(checksum, 0, 2) )+
            "\ndata      = " + (data == null ? "" : NetworkServices.byteArrToString(data, 0, 4) + " ..." )+
            "\ndataLen   = " + dataLen  + "\n";
    }

    /**
        check if this packet is equal to another packet. this will only compare sequence numbers
        @return true if equal, false otherwise
    */
    public boolean equals(Object o)
    {
        if (o == null) {return false;}
        if (!(o instanceof Packet)) {return false;}
        Packet p = (Packet)o;
        return
                this.seqNum() == p.seqNum()
            // &&  this.ackNum() == p.ackNum()
            ;
    }

    /**
        print()
        print a string to the console when debug is on
        @param s - the string to print
    */
    private void print(String s)
    {
        if (fcntcp.debug){System.out.println("Packet.java: "+s);}
    }


}
