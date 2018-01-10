/**
 * NetworkServices.java
 * @author Jason Tu <jwt8264@rit.edu>
 *
 * This class provides constants and functions that are useful to everyone
 *
 */

public class NetworkServices
{

	/**
	 * convert an int to a byte array
	 * @param i - number
	 * @return byte array of the int
	 */
	public static byte[] intToBytes(int i)
	{
		byte[] result = new byte[4];
		result[0] = (byte) (i >> 24);
		result[1] = (byte) (i >> 16);
		result[2] = (byte) (i >> 8);
		result[3] = (byte) (i );
		// print(String.format("encoding %d", i));

		return result;
	}

	/**
	 * Get an int from an array of bytes
	 * As there are 4 bytes in a long, start+4 must be less than bytes.length
	 * @param bytes - the array
	 * @param start - the index of the beginning of the int
	 * @return - the int
	 */
	public static int bytesToInt(byte[] bytes, int start)
	{
		return
			 bytes[start+0]         << 24 |
			(bytes[start+1] & 0xff) << 16 |
			(bytes[start+2] & 0xff) << 8  |
			(bytes[start+3] & 0xff);
	}

    /**
        byteToString()
        get the binary representation of a byte
        @param b - the byte to print
        @return - the byte as a bit string
    */
	public static String byteToString(byte b)
	{
		return String.format("%8s", Integer.toBinaryString(b & 0xFF)).replace(' ', '0');
	}

    /**
        byteArrToString()
        get the binary representation of a byte array
        @param a - the array
        @param start - the first byte to print out
        @param len - the number of bytes to print
        @return - the bytes as a series of bit strings
    */
	public static String byteArrToString(byte[] a, int start, int len)
	{
		String s = "";
		for (int i = start; i < start + len && i < a.length; i++)
			s += byteToString(a[i]) + " ";
		return s;
	}

	// print debug messages
	private static void print(String s)
	{
		if (fcntcp.debug){System.out.println("NetworkServices.java: "+s);}
	}

}
