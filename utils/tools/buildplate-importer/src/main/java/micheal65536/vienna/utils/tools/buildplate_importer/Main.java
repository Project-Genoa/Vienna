package micheal65536.vienna.utils.tools.buildplate_importer;

import com.google.gson.Gson;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.config.Configurator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import micheal65536.vienna.db.DatabaseException;
import micheal65536.vienna.db.EarthDB;
import micheal65536.vienna.db.model.player.Buildplates;
import micheal65536.vienna.eventbus.client.EventBusClient;
import micheal65536.vienna.eventbus.client.EventBusClientException;
import micheal65536.vienna.eventbus.client.RequestSender;
import micheal65536.vienna.objectstore.client.ObjectStoreClient;
import micheal65536.vienna.objectstore.client.ObjectStoreClientException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class Main
{
	public static void main(String[] args)
	{
		System.setProperty("log4j.shutdownHookEnabled", "false");
		Configurator.setRootLevel(Level.DEBUG);

		Options options = new Options();
		options.addOption(Option.builder()
				.option("db")
				.hasArg()
				.argName("db")
				.desc("Database path, defaults to ./earth.db")
				.build());
		options.addOption(Option.builder()
				.option("objectstore")
				.hasArg()
				.argName("objectstore")
				.desc("Object storage address, defaults to localhost:5396")
				.build());
		options.addOption(Option.builder()
				.option("eventbus")
				.hasArg()
				.argName("eventbus")
				.desc("Event bus address, defaults to localhost:5532")
				.build());
		options.addOption(Option.builder()
				.option("playerId")
				.hasArg()
				.required()
				.argName("id")
				.desc("Player ID to import for")
				.build());
		options.addOption(Option.builder()
				.option("worldFile")
				.hasArg()
				.required()
				.argName("file")
				.desc("World file or directory to import")
				.build());
		CommandLine commandLine;
		String dbConnectionString;
		String objectStoreConnectionString;
		String eventBusConnectionString;
		String playerId;
		String worldFile;
		try
		{
			commandLine = new DefaultParser().parse(options, args);
			dbConnectionString = commandLine.hasOption("db") ? commandLine.getOptionValue("db") : "./earth.db";
			objectStoreConnectionString = commandLine.hasOption("objectstore") ? commandLine.getOptionValue("objectstore") : "localhost:5396";
			eventBusConnectionString = commandLine.hasOption("eventbus") ? commandLine.getOptionValue("eventbus") : "localhost:5532";
			playerId = commandLine.getOptionValue("playerId");
			worldFile = commandLine.getOptionValue("worldFile");
		}
		catch (ParseException exception)
		{
			LogManager.getLogger().fatal(exception);
			System.exit(1);
			return;
		}

		LogManager.getLogger().info("Connecting to database");
		EarthDB earthDB;
		try
		{
			earthDB = EarthDB.open(dbConnectionString);
		}
		catch (DatabaseException exception)
		{
			LogManager.getLogger().fatal("Could not connect to database", exception);
			System.exit(1);
			return;
		}
		LogManager.getLogger().info("Connected to database");

		LogManager.getLogger().info("Connecting to object storage");
		ObjectStoreClient objectStoreClient;
		try
		{
			objectStoreClient = ObjectStoreClient.create(objectStoreConnectionString);
		}
		catch (ObjectStoreClientException exception)
		{
			LogManager.getLogger().fatal("Could not connect to object storage", exception);
			System.exit(1);
			return;
		}
		LogManager.getLogger().info("Connected to object storage");

		LogManager.getLogger().info("Connecting to event bus");
		EventBusClient eventBusClient;
		try
		{
			eventBusClient = EventBusClient.create(eventBusConnectionString);
			LogManager.getLogger().info("Connected to event bus");
		}
		catch (EventBusClientException exception)
		{
			LogManager.getLogger().warn("Could not connect to event bus, buildplate preview will not be generated", exception);
			eventBusClient = null;
		}

		byte[] serverData = createServerDataFromWorldFile(worldFile);
		if (serverData == null)
		{
			LogManager.getLogger().fatal("Could not get world data");
			System.exit(2);
			return;
		}

		String buildplateId = UUID.randomUUID().toString();

		if (!storeBuildplate(earthDB, eventBusClient, objectStoreClient, playerId, buildplateId, serverData, System.currentTimeMillis()))
		{
			LogManager.getLogger().fatal("Could not add buildplate");
			System.exit(3);
			return;
		}

		LogManager.getLogger().info("Added buildplate with ID {} for player {}", buildplateId, playerId);
		System.exit(0);
		return;
	}

	private static byte[] createServerDataFromWorldFile(@NotNull String worldFileName)
	{
		File worldFile = new File(worldFileName);
		if (!worldFile.exists())
		{
			LogManager.getLogger().error("World file/directory does not exist");
			return null;
		}
		if (worldFile.isFile())
		{
			byte[] data;
			try (FileInputStream fileInputStream = new FileInputStream(worldFile))
			{
				data = fileInputStream.readAllBytes();
			}
			catch (IOException exception)
			{
				LogManager.getLogger().error("Could not read world file", exception);
				return null;
			}
			return data;
		}
		else if (worldFile.isDirectory())
		{
			byte[] data;
			try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(); ZipOutputStream zipOutputStream = new ZipOutputStream(byteArrayOutputStream))
			{
				for (String dirName : new String[]{"region", "entities"})
				{
					File dir = new File(worldFile, dirName);
					for (String regionName : new String[]{"r.0.0.mca", "r.0.-1.mca", "r.-1.0.mca", "r.-1.-1.mca"})
					{
						ZipEntry zipEntry = new ZipEntry(dirName + "/" + regionName);
						zipEntry.setMethod(ZipEntry.DEFLATED);
						zipOutputStream.putNextEntry(zipEntry);
						try (FileInputStream fileInputStream = new FileInputStream(new File(dir, regionName)))
						{
							fileInputStream.transferTo(zipOutputStream);
						}
						zipOutputStream.closeEntry();
					}
				}
				zipOutputStream.finish();
				data = byteArrayOutputStream.toByteArray();
			}
			catch (IOException exception)
			{
				LogManager.getLogger().error("Could not get saved world data from world directory", exception);
				return null;
			}
			return data;
		}
		else
		{
			LogManager.getLogger().error("World file/directory cannot be accessed");
			return null;
		}
	}

	private static boolean storeBuildplate(@NotNull EarthDB earthDB, @Nullable EventBusClient eventBusClient, @NotNull ObjectStoreClient objectStoreClient, @NotNull String playerId, @NotNull String buildplateId, byte[] serverData, long timestamp)
	{
		String preview;
		if (eventBusClient != null)
		{
			record PreviewRequest(
					@NotNull String serverDataBase64,
					boolean night
			)
			{
			}

			RequestSender requestSender = eventBusClient.addRequestSender();
			preview = requestSender.request("buildplates", "preview", new Gson().toJson(new PreviewRequest(Base64.getEncoder().encodeToString(serverData), false))).join();
			requestSender.close();

			if (preview == null)
			{
				LogManager.getLogger().warn("Could not get preview for buildplate (preview generator did not respond to event bus request)");
			}
		}
		else
		{
			preview = null;
		}

		String serverDataObjectId = objectStoreClient.store(serverData).join();
		if (serverDataObjectId == null)
		{
			LogManager.getLogger().error("Could not store data object in object store");
			return false;
		}

		String previewObjectId = objectStoreClient.store(preview != null ? preview.getBytes(StandardCharsets.US_ASCII) : new byte[0]).join();
		if (previewObjectId == null)
		{
			LogManager.getLogger().error("Could not store preview object in object store");
			return false;
		}

		try
		{
			EarthDB.Results results = new EarthDB.Query(true)
					.get("buildplates", playerId, Buildplates.class)
					.then(results1 ->
					{
						Buildplates buildplates = (Buildplates) results1.get("buildplates").value();

						Buildplates.Buildplate buildplate = new Buildplates.Buildplate(16, 63, 33, false, timestamp, serverDataObjectId, previewObjectId);    // TODO: make size/offset/etc. configurable

						buildplates.addBuildplate(buildplateId, buildplate);

						return new EarthDB.Query(true)
								.update("buildplates", playerId, buildplates);
					})
					.execute(earthDB);
			return true;
		}
		catch (DatabaseException exception)
		{
			LogManager.getLogger().error("Failed to store buildplate in database", exception);
			objectStoreClient.delete(serverDataObjectId);
			objectStoreClient.delete(previewObjectId);
			return false;
		}
	}
}