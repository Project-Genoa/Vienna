package micheal65536.vienna.tappablesgenerator;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.config.Configurator;
import org.jetbrains.annotations.NotNull;

import micheal65536.vienna.eventbus.client.EventBusClient;
import micheal65536.vienna.eventbus.client.EventBusClientException;

public class Main
{
	public static void main(String[] args)
	{
		Configurator.setRootLevel(Level.DEBUG);

		Options options = new Options();
		options.addOption(Option.builder()
				.option("eventbus")
				.hasArg()
				.argName("eventbus")
				.desc("Event bus address, defaults to localhost:5532")
				.build());
		CommandLine commandLine;
		String eventBusConnectionString;
		try
		{
			commandLine = new DefaultParser().parse(options, args);
			eventBusConnectionString = commandLine.hasOption("eventbus") ? commandLine.getOptionValue("eventbus") : "localhost:5532";
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

		Generator generator = new Generator();
		Spawner[] spawner = new Spawner[1];
		ActiveTiles activeTiles = new ActiveTiles(eventBusClient, new ActiveTiles.ActiveTileListener()
		{
			@Override
			public void active(ActiveTiles.@NotNull ActiveTile activeTile)
			{
				spawner[0].spawnTile(activeTile.tileX(), activeTile.tileY());
			}

			@Override
			public void inactive(ActiveTiles.@NotNull ActiveTile activeTile)
			{
				// empty
			}
		});
		spawner[0] = new Spawner(eventBusClient, activeTiles, generator);
		spawner[0].run();
	}
}