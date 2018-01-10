/**
    MD5Summer.java
    @author Jason Tu <jwt8264@rit.edu>

    This class calculates an MD5 sum of a bunch of bytes
    and can give back the result.
*/

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.File;

public class MD5Summer
{
    private MessageDigest summer;

    /**
        Constructor
        Initialize this MD5 summer
    */
    public MD5Summer()
    {
        try
        {
            summer = MessageDigest.getInstance("MD5");
        }
        catch (NoSuchAlgorithmException e){}   // should never be thrown
        summer.reset();
    }

    /**
        next()
        Add another chunk of bytes to the MD5 sum
        @param b - the bytes to add
        @param offset - the byte to start from
        @param len - how many bytes to add
    */
    public void next(byte[] b, int offset, int len)
    {
        summer.update(b, offset, len);
    }

    /**
        getResult()
        Get the resulting MD5 sum of what has been summed so far.
        This will also reset the object.
        @return - a string with the resulting MD5 sum
    */
    public String getResult()
    {
        byte[] result = summer.digest();

        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < result.length; i++) {
            sb.append(Integer.toString((result[i] & 0xff) + 0x100, 16).substring(1));
        }

        // StringBuffer hexString = new StringBuffer();
    	// for (int i=0;i<result.length;i++) {
    	// 	String hex=Integer.toHexString(0xff & result[i]);
       	//     	if(hex.length()==1) hexString.append('0');
       	//     	hexString.append(hex);
    	// }
        reset();
        return sb.toString();//+"\n"+hexString.toString();
    }

    /**
        reset()
        Reset this object to start MD5 summing another set of bytes
    */
    public void reset()
    {
        summer.reset();
    }

    /**
        main method to test the functionality of this object.
    */
    public static void main(String[] args)
    {
        FileInputStream stream = null;
        try
        {
            stream = new FileInputStream(new File("test1M.bin"));
            byte[] b = new byte[1024];
            int bytesRead = 0;
            MD5Summer s = new MD5Summer();
            while ((bytesRead = stream.read(b)) != -1)
            {
                s.next(b, 0, bytesRead);
            }
            System.out.println(s.getResult());
            System.out.println("e0eeb7b83e03b2b0f34659066c4b669f - expected");


            stream.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        finally
        {
            try {
                if (stream != null)
                    stream.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }

    }
}
