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
import java.util.HashMap;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
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
				.desc("World file to import")
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
		playerId = playerId.toLowerCase(Locale.ROOT);

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

		WorldData worldData = readWorldFile(worldFile);
		if (worldData == null)
		{
			LogManager.getLogger().fatal("Could not get world data");
			System.exit(2);
			return;
		}

		String buildplateId = UUID.randomUUID().toString();

		if (!storeBuildplate(earthDB, eventBusClient, objectStoreClient, playerId, buildplateId, worldData, System.currentTimeMillis()))
		{
			LogManager.getLogger().fatal("Could not add buildplate");
			System.exit(3);
			return;
		}

		LogManager.getLogger().info("Added buildplate with ID {} for player {}", buildplateId, playerId);
		System.exit(0);
		return;
	}

	@Nullable
	private static WorldData readWorldFile(@NotNull String worldFileName)
	{
		HashMap<String, byte[]> worldFileContents = new HashMap<>();
		try (FileInputStream fileInputStream = new FileInputStream(new File(worldFileName)); ZipInputStream zipInputStream = new ZipInputStream(fileInputStream))
		{
			for (ZipEntry zipEntry = zipInputStream.getNextEntry(); zipEntry != null; zipEntry = zipInputStream.getNextEntry())
			{
				boolean skip;
				if (zipEntry.isDirectory())
				{
					skip = true;
				}
				else
				{
					String name = zipEntry.getName();
					if (name.equals("buildplate_metadata.json"))
					{
						skip = false;
					}
					else
					{
						String[] parts = name.split("/");
						if (parts.length != 2)
						{
							skip = true;
						}
						else
						{
							if (!parts[0].equals("region") && !parts[0].equals("entities"))
							{
								skip = true;
							}
							else if (!parts[1].equals("r.0.0.mca") && !parts[1].equals("r.0.-1.mca") && !parts[1].equals("r.-1.0.mca") && !parts[1].equals("r.-1.-1.mca"))
							{
								skip = true;
							}
							else
							{
								skip = false;
							}
						}
					}
				}

				if (!skip)
				{
					ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
					zipInputStream.transferTo(byteArrayOutputStream);
					worldFileContents.put(zipEntry.getName(), byteArrayOutputStream.toByteArray());
				}

				zipInputStream.closeEntry();
			}
		}
		catch (IOException exception)
		{
			LogManager.getLogger().error("Could not read world file", exception);
			return null;
		}

		byte[] serverData;
		try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(); ZipOutputStream zipOutputStream = new ZipOutputStream(byteArrayOutputStream))
		{
			for (String dirName : new String[]{"region", "entities"})
			{
				for (String regionName : new String[]{"r.0.0.mca", "r.0.-1.mca", "r.-1.0.mca", "r.-1.-1.mca"})
				{
					byte[] data = worldFileContents.getOrDefault(dirName + "/" + regionName, null);
					if (data == null)
					{
						LogManager.getLogger().error("World file is missing {}", dirName + "/" + regionName);
						return null;
					}

					ZipEntry zipEntry = new ZipEntry(dirName + "/" + regionName);
					zipEntry.setMethod(ZipEntry.DEFLATED);
					zipOutputStream.putNextEntry(zipEntry);
					zipOutputStream.write(data);
					zipOutputStream.closeEntry();
				}
			}
			zipOutputStream.finish();
			serverData = byteArrayOutputStream.toByteArray();
		}
		catch (IOException exception)
		{
			LogManager.getLogger().error("Could not prepare server data", exception);
			return null;
		}

		int size;
		int offset;
		boolean night;
		try
		{
			byte[] buildplateMetadataFileData = worldFileContents.getOrDefault("buildplate_metadata.json", null);
			String buildplateMetadataString = buildplateMetadataFileData != null ? new String(buildplateMetadataFileData, StandardCharsets.UTF_8) : null;
			if (buildplateMetadataString == null)
			{
				LogManager.getLogger().warn("World file does not contain buildplate_metadata.json, using default values");
				size = 16;
				offset = 63;
				night = false;
			}
			else
			{
				int version;
				record BuildplateMetadataVersion(
						int version
				)
				{
				}
				BuildplateMetadataVersion buildplateMetadataVersion = new Gson().fromJson(buildplateMetadataString, BuildplateMetadataVersion.class);
				version = buildplateMetadataVersion.version;

				switch (version)
				{
					case 1 ->
					{
						record BuildplateMetadata(
								int size,
								int offset,
								boolean night
						)
						{
						}
						BuildplateMetadata buildplateMetadata = new Gson().fromJson(buildplateMetadataString, BuildplateMetadata.class);
						size = buildplateMetadata.size;
						offset = buildplateMetadata.offset;
						night = buildplateMetadata.night;
					}
					default ->
					{
						LogManager.getLogger().error("Unsupported buildplate metadata version {}", version);
						return null;
					}
				}
			}
		}
		catch (Exception exception)
		{
			LogManager.getLogger().error("Could not read buildplate metadata file", exception);
			return null;
		}

		if (size != 8 && size != 16 && size != 32)
		{
			LogManager.getLogger().error("Invalid buildplate size {}", size);
			return null;
		}

		return new WorldData(serverData, size, offset, night);
	}

	private static boolean storeBuildplate(@NotNull EarthDB earthDB, @Nullable EventBusClient eventBusClient, @NotNull ObjectStoreClient objectStoreClient, @NotNull String playerId, @NotNull String buildplateId, @NotNull WorldData worldData, long timestamp)
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
			preview = requestSender.request("buildplates", "preview", new Gson().toJson(new PreviewRequest(Base64.getEncoder().encodeToString(worldData.serverData), worldData.night))).join();
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

		String serverDataObjectId = objectStoreClient.store(worldData.serverData).join();
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

						int scale = switch (worldData.size)
						{
							case 8 -> 14;
							case 16 -> 33;
							case 32 -> 64;
							default -> 33;
						};
						Buildplates.Buildplate buildplate = new Buildplates.Buildplate(worldData.size, worldData.offset, scale, worldData.night, timestamp, serverDataObjectId, previewObjectId);

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

	private record WorldData(
			byte[] serverData,
			int size,
			int offset,
			boolean night
	)
	{
	}
}