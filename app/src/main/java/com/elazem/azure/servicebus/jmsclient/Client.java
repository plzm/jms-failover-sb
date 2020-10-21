package com.elazem.azure.servicebus.jmsclient;

import javax.jms.*;
import javax.naming.Context;
import javax.naming.InitialContext;
import java.util.Hashtable;

public class Client
{
	public boolean send(String connectionString, String queueName, int numOfMsgs) throws JMSException
	{
		boolean result = false;
		MessageProducer producer = null;
		Session session = null;
		Connection connection = null;
		Destination queue = null;

		try
		{
			Hashtable<String, String> jndiContext = getJNDIContext(connectionString, queueName);

			Context context = new InitialContext(jndiContext);
			ConnectionFactory cf = (ConnectionFactory) context.lookup("SBCF");
			
			// Look up queue
			queue = (Destination) context.lookup("QUEUE");

			// Create Connection
			connection = cf.createConnection();

			// Create Session, no transaction, client ack
			session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

			// Create producer
			producer = session.createProducer(queue);

			// Send messages
			for (int i = 0; i < numOfMsgs; i++) {
				BytesMessage message = session.createBytesMessage();
				message.writeBytes(String.valueOf(i).getBytes());
				producer.send(message);
				System.out.printf("Sent message %d.%n", i + 1);
			}

			result = true;
		}
		catch (Exception e)
		{
			result = false;
		}

		try
		{
			if (producer != null)
				producer.close();
			
			if (session != null)
				session.close();
			
			if (connection != null)
			{
				connection.stop();
				connection.close();
			}
		}
		catch (Exception e)
		{
            System.out.printf("%s", e.toString());
		}
		
		return result;
	}

	private Hashtable<String, String> getJNDIContext(String connectionString, String queueName)
	{
		// set up JNDI context
		Hashtable<String, String> hashtable = new Hashtable<>();

        hashtable.put("connectionfactory.SBCF", connectionString);
        hashtable.put("queue.QUEUE", queueName);
        hashtable.put(Context.INITIAL_CONTEXT_FACTORY, "org.apache.qpid.jms.jndi.JmsInitialContextFactory");

		return hashtable;
	}	
}
