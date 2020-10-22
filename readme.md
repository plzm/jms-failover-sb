
### PLEASE NOTE FOR THE ENTIRETY OF THIS REPOSITORY AND ALL ASSETS
#### 1. No warranties or guarantees are made or implied.
#### 2. All assets here are provided by me "as is". Use at your own risk. Validate before use.
#### 3. I am not representing my employer with these assets, and my employer assumes no liability whatsoever, and will not provide support, for any use of these assets.
#### 4. Use of the assets in this repo in your Azure environment may or will incur Azure usage and charges. You are completely responsible for monitoring and managing your Azure usage.

Unless otherwise noted, all assets here are authored by me. Feel free to examine, learn from, comment, and re-use (subject to the above) as needed and without intellectual property restrictions.

If anything here helps you, attribution and/or a quick note is much appreciated.

---

## JMS Service Bus Client

### Scenario

Java Messaging Service (JMS) client apps using the Apache Qpid <sup>TM</sup> AMQP client library were connecting to an on-premise Apache ActiveMQ messaging infrastructure and sending AMQP 1.0 messages. The clients were configured for failover over multiple ActiveMQ endpoints.

The client apps needed to target [Azure Service Bus](https://docs.microsoft.com/azure/service-bus-messaging/), which [supports JMS and AMQP](https://docs.microsoft.com/azure/service-bus-messaging/service-bus-java-how-to-use-jms-api-amqp). Failover across Azure regions was required for continuity. Source code changes and added dependencies, on Azure Service Bus or other libraries, needed to be minimized or avoided.

### Azure Service Bus Configuration

Two or more Azure Service Bus namespaces, each in a separate Azure region, are needed.

This example assumes that clients will authenticate to Azure Service Bus using [Shared Access Signatures (SAS)](https://docs.microsoft.com/azure/service-bus-messaging/service-bus-sas).

**NOTE** I recommend NOT using the default *RootManageSharedAccessKey* policy that is automatically created with a new namespace. Instead, create a Shared Access Policy **without** the Manage claim.

For example, create a *SendListen* policy with only the *Send* and *Listen* claims, or create separate *Send* and *Listen* policies, each with only that claim, for even more granular separation of responsibilities when client apps only need to Send or Listen, but not both.

Your Azure Service Bus namespaces should each have the same-named Shared Access Policy created. For example, if you decide on a *SendListen* policy, create it on each of your Azure Service Bus namespaces.

### Qpid JMS Client Configuration

Qpid JMS client configuration is documented [here](https://qpid.apache.org/releases/qpid-jms-0.54.0/docs/index.html). (Please note, this was the current version at the time of this writing.)

Failover Configuration options are documented [here](https://qpid.apache.org/releases/qpid-jms-0.54.0/docs/index.html#failover-configuration-options). Note that the *failover* list includes a list of comma-separated AMQP/AMQPS URIs.

To use a set of Azure Service Bus namespaces in the failover list, configure the list as follows.

Construct each endpoint URI with the AMQPS scheme, then the Azure Service Bus URI, then the AMQPS port. For example: `amqps://myfirstnamespace.servicebus.windows.net:5671` and `amqps://mysecondnamespace.servicebus.windows.net:5671`. Substitute your own namespace names for `myfirstnamespace` and `mysecondnamespace`.

The failover URI with the list of endpoints looks like this before adding other options:

```
failover:(amqps://myfirstnamespace.servicebus.windows.net:5671,amqps://mysecondnamespace.servicebus.windows.net:5671)
```

The JMS client needs to authenticate to Azure Service Bus. Again, in this example you are using Shared Access Signature authentication (SAS).

To authenticate to Azure Service Bus, use the jms.username and jms.password configuration options. Per the [Apache Qpid documentation](https://qpid.apache.org/releases/qpid-jms-0.54.0/docs/index.html#failover-configuration-options), only `transport.` or `amqp.` options can be specified individually on each endpoint within the failover URI list, whereas `jms.` options must be applied to the overall failover URI, outside the parentheses, and affect the entire JMS connection object.

This means that authentication information (SAS rule name and key) *cannot be provided separately for each individual Azure Service Bus namespace URI*!

This is what a failover URI with authentication information looks like when a Shared Access Policy named *SendListen* is used (obviously substitute your Shared Access Policy key for *mysharedaccesskey*):

```
failover:(amqps://myfirstnamespace.servicebus.windows.net:5671,amqps://mysecondnamespace.servicebus.windows.net:5671)?jms.username=SendListen&jms.password=mysharedaccesskey
```

**NOTE** The preceding failover URI still omits some other JMS and AMQP options that aren't relevant to failover configuration. See the end of this README below for a complete failover URI including added options.

Above, I said each Azure Service Bus namespace should be configured with the same-named Shared Access Policy with needed claims - in the failover URI above, I'm using *SendListen*.

When Azure Service Bus creates a Shared Access Policy, it automatically generates unique keys. This means that the password will be different between namespaces, which will not work since we are only able to specify a single `jms.password` option in the failover URI! Another step is needed to synchronize the keys for the Shared Access Policy across Azure Service Bus namespaces.

To do this, I'll use a shell script and some Azure Service Bus CLI commands. The complete version of the following code is also in [sb-namespace-sync-keys.sh](./sb-namespace-sync-keys.sh) in this repo.

This code:

1. Retrieves the primary and secondary keys for the specified source Azure Service Bus namespace and Shared Access Policy
2. Writes the primary and secondary key values to the specified destination Azure Service Bus namespace and Shared Access Policy

This is how to synchronize Shared Access Policy keys across Azure Service Bus namespaces so that a JMS failover URI with multiple Azure Service Bus namespace endpoints, but only the one username and password allowed by the JMS specification, can be used by a JMS client app.

```bash
# Get source namespace primary and secondary keys
primary_key="$(az servicebus namespace authorization-rule keys list --subscription "$subscription_id" -g "$resource_group_name_source" --namespace-name "$namespace_name_source" -n "$shared_access_policy_name" -o tsv --query 'primaryKey')"
secondary_key="$(az servicebus namespace authorization-rule keys list --subscription "$subscription_id" -g "$resource_group_name_source" --namespace-name "$namespace_name_source" -n "$shared_access_policy_name" -o tsv --query 'secondaryKey')"

# Set to destination namespace
az servicebus namespace authorization-rule keys renew --subscription "$subscription_id" \
    -g "$resource_group_name_destination" --namespace-name "$namespace_name_destination" -n "$shared_access_policy_name" \
    --key PrimaryKey --key-value "$primary_key" --verbose

az servicebus namespace authorization-rule keys renew --subscription "$subscription_id" \
    -g "$resource_group_name_destination" --namespace-name "$namespace_name_destination" -n "$shared_access_policy_name" \
    --key SecondaryKey --key-value "$secondary_key" --verbose
```

**NOTE** if you rotate or regenerate keys on one Azure Service Bus namespace, you must re-run this script to re-synchronize Shared Access Policy keys across the namespaces included in the failover endpoint list.

### Complete failover URI

In addition to the jms.username and jms.password options discussed above, a number of other JMS and AMQP configuration options can be configured on the failover URI.

Values used successfully in this sample are appended in the following sample failover URI. Please consult the [JMS documentation](https://qpid.apache.org/releases/qpid-jms-0.54.0/docs/index.html) for details and other available options.

- `jms.clientID`: The ClientID value that is applied to the connection.
- `failover.maxReconnectAttempts`: The number of reconnection attempts allowed before reporting the connection as failed to the client. The default is no limit or (-1). In this sample, set to 1 for immediate failover after one connection failure.
- `amqp.idleTimeout`: The idle timeout in milliseconds after which the connection will be failed if the peer sends no AMQP frames. Default is 60,000. In this sample, set to 120,000.
- `amqp.traceFrames`: In this sample, set to true to add a protocol tracer to Proton and configure the frames logger to include log output.

```
failover:(amqps://myfirstnamespace.servicebus.windows.net:5671,amqps://mysecondnamespace.servicebus.windows.net:5671)?jms.username=SendListen&jms.password=mysharedaccesskey&jms.clientID=jms_amqp_sb_client&failover.maxReconnectAttempts=1&amqp.idleTimeout=120000&amqp.traceFrames=true
```

### Putting it together and running the sample client app

Follow these steps to try this out.

1. You will need a Java environment. I use Visual Studio Code with the Java Extension Pack, the Java <sup>TM</sup> SE 11 JDK, working in WSL2 with Maven. Adjust as you prefer.
2. Deploy two (or more) Azure Service Bus Premium namespaces in different Azure regions.
3. Create a Shared Access Policy with at least *Send* claim in each namespace. In this sample, I use a SAP name of *SendListen* with those claims.
4. Create a queue with the same name in each namespace. In this sample, a default queue name of **q1** is used, but you can override that with the `-q` command-line arg for the sample app.
5. Clone this repo.
6. Edit [sb-namespace-sync-keys.sh](./sb-namespace-sync-keys.sh). Provide values for the variables storing your Azure subscription ID, the Resource Group names holding your source and destination Azure Service Bus namespaces (the namespaces can be in the same Resource Group - in that case just provide the same Resource Group name for both variable), the source and destination Azure Service Bus namespace names, and the name of the Shared Access Policy you created in each namespace.
7. Open your favorite shell. Change to the root folder of this repo where you cloned it. Run ./sb-namespace-sync-keys.sh. It should take a few seconds to complete.
8. In the shell, switch into the ./app folder. Run the following commands:
   a. `mvn clean package` - this will build and package the app
   b. `cs="failover:(amqps://myfirstnamespace.servicebus.windows.net:5671,amqps://mysecondnamespace.servicebus.windows.net:5671)?jms.username=SendListen&jms.password=mysharedaccesskey&jms.clientID=jms_amqp_sb_client&failover.maxReconnectAttempts=1&amqp.idleTimeout=120000&amqp.traceFrames=true"` - this stores your complete failover URI in a bash variable (**NOTE** be careful NOT to include spaces on either side of the variable assignment = operator)
   c. `java -jar ./target/jmsclient-1.0.0-jar-with-dependencies.jar -c $cs` - this passes the failover URI you stored in a $cs variable in step b. into the sample application.
   d. Optionally, you can add `-q` command-line args with your own queue name, and `-n` with a number of messages to send (will be constrained to be between 1 and 1,000). For a queue name of "q2" and 5 messages, this would change the command line from the previous step to `java -jar ./target/jmsclient-1.0.0-jar-with-dependencies.jar -c $cs -q "q2" -n 5`.

If everything was configured correctly, you should see console log traces showing messages sent to the first Azure Service Bus namespace in the failover URI list.

You can test failover by deleting the first Service Bus namespace (simulating a regional failure or other loss of connectivity), then re-running step 8c (or 8d). You should see a connection failure message, and then messages should be sent to the *second* Azure Service Bus namespace in the list.

**NOTE** Please do NOT delete production or can't-lose Azure Service Bus or other resources to try this sample! You *are* working in a test environment or learning sandbox, right? :)

### The code

The sample app consists of two code files: [App.java](./app/src/main/java/com/elazem/azure/servicebus/jmsclient/App.java) and [Client.java](./app/src/main/java/com/elazem/azure/servicebus/jmsclient/Client.java).

App.java contains the main entry point and gets the failover URI (connection string) passed on the command line in step 8c (or 8d) above. It then instantiates Client and calls its `Send()` method.

Client.java contains the implementation of `Send()`, which sends sample messages to the endpoints in the failover endpoint list. It will attempt each endpoint in order, and if an endpoint fails, it will attempt the next one.

No dependencies/imports are required for any Azure Service Bus libraries. This minimizes the amount of change required to migrate a JMS/Qpid/AMQP application from Apache ActiveMQ to Azure Service Bus.

Hope this sample is useful to you!
