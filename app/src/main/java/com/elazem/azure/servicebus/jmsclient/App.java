package com.elazem.azure.servicebus.jmsclient;

import org.apache.commons.cli.*;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class App 
{
    protected static final String ENV_VAR_NAME_SB_CONNECTION_STRING = "SB_CONNECTION_STRING";
    protected static final String QUEUE_NAME = "q1";
    protected static final int NUM_OF_MSGS = 10;

    private static final Logger logger = LogManager.getLogger(App.class);

    public static void main(String[] args) {
        logger.trace("Entering application.");

        int status = -1;

        try {
            String connectionString = getConnectionString(args);

            if (isNullOrWhitespace(connectionString))
            {
                status = 1;
            }
            else
            {
                Client client = new Client();
                boolean sendResult = client.send(connectionString, QUEUE_NAME, NUM_OF_MSGS);

                if (sendResult)
                    status = 0;
            }
        }
        catch (Exception e)
        {
            System.out.printf("%s", e.toString());
            status = 2;
        }

        logger.trace("Exiting application.");

        System.exit(status);
    }

    private static String getConnectionString(String[] args)
    {
        String connectionString = null;

        Options options = new Options();
        options.addOption(new Option("c", true, "Connection string"));

        try
        {
            // First, try to parse connection string from command line
            CommandLineParser clp = new DefaultParser();
            CommandLine cl = clp.parse(options, args);
            if (cl.getOptionValue("c") != null)
            {
                connectionString = cl.getOptionValue("c");
            }
        }
        catch (Exception e)
        {
            System.out.printf("%s", e.toString());
        }

        if (isNullOrWhitespace(connectionString))
        {
            // Since we didn't get a connection string on the command line, let's try
            // getting it from an environment variable
            String env = System.getenv(ENV_VAR_NAME_SB_CONNECTION_STRING);
            if (env != null) {
                connectionString = env;
            }
        }

        if (isNullOrWhitespace(connectionString))
        {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("No connection string found! Run jar with", "", options, "", true);
        }

        return connectionString;
    }

    private static boolean isNullOrWhitespace(String checkMe)
    {
        return !(checkMe != null && !checkMe.isEmpty() && !checkMe.trim().isEmpty());
    }
}
