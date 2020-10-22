package com.elazem.azure.servicebus.jmsclient;

import org.apache.commons.cli.*;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class App 
{
    protected static final String ENV_VAR_NAME_SB_CONNECTION_STRING = "SB_CONNECTION_STRING";

    protected static final String DEFAULT_QUEUE_NAME = "q1";
    protected static final int DEFAULT_NUM_OF_MSGS = 10;

    protected static String connectionString;
    protected static String queueName;
    protected static int numOfMsgs;

    protected static final Logger logger = LogManager.getLogger(App.class);

    public static void main(String[] args) {
        logger.trace("Entering application.");

        int status = -1;

        try {
            logger.trace("Get args.");
            getArgs(args);

            logger.trace(connectionString);
            logger.trace(queueName);
            logger.trace(numOfMsgs);

            if (isNullOrWhitespace(connectionString))
            {
                status = 1;
            }
            else
            {
                Client client = new Client();
                boolean sendResult = client.send(connectionString, queueName, numOfMsgs);

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

    private static void getArgs(String[] args)
    {
        Options options = new Options();
        options.addOption(new Option("c", true, "Connection string"));
        options.addOption(new Option("q", true, "Queue name"));
        options.addOption(new Option("n", true, "Number of messages to send"));

        try
        {
            CommandLineParser clp = new DefaultParser();
            CommandLine cl = clp.parse(options, args);

            if (cl.getOptionValue("c") != null) {
                connectionString = cl.getOptionValue("c");
            }

            if (cl.getOptionValue("q") != null) {
                queueName = cl.getOptionValue("c");
            }

            if (cl.getOptionValue("n") != null) {
                numOfMsgs = Integer.parseInt(cl.getOptionValue("n"));
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

        if (isNullOrWhitespace(queueName)) {
            queueName = DEFAULT_QUEUE_NAME;
        }

        if (numOfMsgs <= 0 || numOfMsgs > 1000) {
            numOfMsgs = DEFAULT_NUM_OF_MSGS;
        }
    }

    private static boolean isNullOrWhitespace(String checkMe)
    {
        return !(checkMe != null && !checkMe.isEmpty() && !checkMe.trim().isEmpty());
    }
}
