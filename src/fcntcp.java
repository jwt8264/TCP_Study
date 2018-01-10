/**
* fcntcp.java
* @author Jason Tu <jwt8264@rit.edu>
* This class contains the main method for running
*/

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import java.util.List;

import java.io.FileNotFoundException;
import java.io.IOException;

public class fcntcp
{

    public static boolean debug = false;
    public static boolean verbose = false;

    /**
        main()
        the main function of fcntcp
    */
    public static void main(String[] args)
    {

        Options options = null;
        Client client = null;
        Server server = null;

        int timeout = 1000; // default timeout time in ms

        try
        {
            options = new Options();
            options.addOption("c", false, "run as client");
            options.addOption("s", false, "run as server");
            options.addOption("f", "file", true, "specify file to send, in absolute or relative path (client only)");
            options.addOption("t", "timeout", true, "timeout in milliseconds for retransmit");
            options.addOption("v", "verbose", false, "print detailed diagnostics");
            options.addOption("d", "debug", false, "turn on debug mode");

            CommandLineParser parser = new DefaultParser();
            CommandLine cmd = parser.parse(options, args);
            List<String> parameters = cmd.getArgList();

            if (cmd.hasOption("v"))
            {
                verbose = true;
            }
            if (cmd.hasOption("debug"))
            {
                debug = true;
            }

            if (cmd.hasOption("c"))
            {
                // CLIENT MODE
                String serverAddr = parameters.get(0);
                int port = Integer.parseInt(parameters.get(1));
                String file = cmd.getOptionValue("file");
                if (cmd.hasOption("t") || cmd.hasOption("timeout"))
                {
                    timeout = Integer.parseInt(cmd.getOptionValue("timeout"));
                }
                client = new Client(serverAddr, port, file, timeout);
                client.start();
                return;
            }
            else if (cmd.hasOption("s"))
            {
                // SERVER MODE
                int port = Integer.parseInt(parameters.get(0));
                server = new Server(port);
                server.start();
                return;
            }
            else
            {
                // did not specify client or server
                System.out.println("You must specify client or server");
            }
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
        catch (FileNotFoundException e)
        {
            e.printStackTrace();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        catch (IndexOutOfBoundsException e)
        {
            e.printStackTrace();
        }
        catch (NumberFormatException e)
        {
            e.printStackTrace();
        }
        catch (ParseException e)
        {
            e.printStackTrace();
        }
        usage(options);
    }

    /**
        print a debug message
    */
    private static void debug(String s)
    {
        if (debug){System.out.println("fcntcp: "+s);}
    }

    /**
        print a usage message
    */
    private static void usage(Options o)
    {
		// automatically generate the help statement
		HelpFormatter formatter = new HelpFormatter();
		String usage = "fcntcp -{c,s} [options] [server address] port";
		formatter.printHelp( usage, o );
    }

}
