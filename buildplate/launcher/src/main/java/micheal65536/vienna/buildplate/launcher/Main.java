package micheal65536.vienna.buildplate.launcher;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.config.Configurator;

import micheal65536.vienna.eventbus.client.EventBusClient;
import micheal65536.vienna.eventbus.client.EventBusClientException;

public class Main
{
	public static void main(String[] args)
	{
		System.setProperty("log4j.shutdownHookEnabled", "false");
		Configurator.setRootLevel(Level.DEBUG);

		Options options = new Options();
		options.addOption(Option.builder()
				.option("eventbus")
				.hasArg()
				.argName("eventbus")
				.desc("Event bus address, defaults to localhost:5532")
				.build());
		options.addOption(Option.builder()
				.option("publicAddress")
				.hasArg()
				.argName("address")
				.required()
				.desc("Public server address to report in instance info")
				.build());
		options.addOption(Option.builder()
				.option("bridgeJar")
				.hasArg()
				.argName("jar")
				.required()
				.desc("Fountain bridge JAR file")
				.build());
		options.addOption(Option.builder()
				.option("serverTemplateDir")
				.hasArg()
				.argName("dir")
				.required()
				.desc("Minecraft/Fabric server template directory, containing the Fabric JAR, mods, and libraries")
				.build());
		options.addOption(Option.builder()
				.option("fabricJarName")
				.hasArg()
				.argName("name")
				.required()
				.desc("Name of the Fabric JAR to run within the server template directory")
				.build());
		options.addOption(Option.builder()
				.option("connectorPluginJar")
				.hasArg()
				.argName("jar")
				.required()
				.desc("Fountain connector plugin JAR")
				.build());
		CommandLine commandLine;
		String eventBusConnectionString;
		String publicAddress;
		String bridgeJar;
		String serverTemplateDir;
		String fabricJarName;
		String connectorPluginJar;
		try
		{
			commandLine = new DefaultParser().parse(options, args);
			eventBusConnectionString = commandLine.hasOption("eventbus") ? commandLine.getOptionValue("eventbus") : "localhost:5532";
			publicAddress = commandLine.getOptionValue("publicAddress");
			bridgeJar = commandLine.getOptionValue("bridgeJar");
			serverTemplateDir = commandLine.getOptionValue("serverTemplateDir");
			fabricJarName = commandLine.getOptionValue("fabricJarName");
			connectorPluginJar = commandLine.getOptionValue("connectorPluginJar");
		}
		catch (ParseException exception)
		{
			LogManager.getLogger().fatal(exception);
			System.exit(1);
			return;
		}

		LogManager.getLogger().info("Connecting to event bus");
		EventBusClient eventBusClient;
		try
		{
			eventBusClient = EventBusClient.create(eventBusConnectionString);
		}
		catch (EventBusClientException exception)
		{
			LogManager.getLogger().fatal("Could not connect to event bus", exception);
			System.exit(1);
			return;
		}
		LogManager.getLogger().info("Connected to event bus");

		String javaCmd = JavaLocator.locateJava();
		Starter starter = new Starter(eventBusClient, eventBusConnectionString, publicAddress, javaCmd, bridgeJar, serverTemplateDir, fabricJarName, connectorPluginJar);
		PreviewGenerator previewGenerator = new PreviewGenerator(javaCmd, bridgeJar);
		InstanceManager instanceManager = new InstanceManager(eventBusClient, starter, previewGenerator);

		Runtime.getRuntime().addShutdownHook(new Thread(() ->
		{
			instanceManager.shutdown();
		}));
	}
}