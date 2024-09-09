package micheal65536.vienna.buildplate.launcher;

import com.github.steveice10.opennbt.NBTIO;
import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import com.google.gson.Gson;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import micheal65536.vienna.buildplate.connector.model.ConnectorPluginArg;
import micheal65536.vienna.buildplate.connector.model.FindPlayerIdRequest;
import micheal65536.vienna.buildplate.connector.model.InitialPlayerStateResponse;
import micheal65536.vienna.buildplate.connector.model.InventoryAddItemMessage;
import micheal65536.vienna.buildplate.connector.model.InventoryRemoveItemRequest;
import micheal65536.vienna.buildplate.connector.model.InventoryResponse;
import micheal65536.vienna.buildplate.connector.model.InventorySetHotbarMessage;
import micheal65536.vienna.buildplate.connector.model.InventoryType;
import micheal65536.vienna.buildplate.connector.model.InventoryUpdateItemWearMessage;
import micheal65536.vienna.buildplate.connector.model.PlayerConnectedRequest;
import micheal65536.vienna.buildplate.connector.model.PlayerConnectedResponse;
import micheal65536.vienna.buildplate.connector.model.PlayerDisconnectedRequest;
import micheal65536.vienna.buildplate.connector.model.PlayerDisconnectedResponse;
import micheal65536.vienna.buildplate.connector.model.WorldSavedMessage;
import micheal65536.vienna.eventbus.client.EventBusClient;
import micheal65536.vienna.eventbus.client.RequestHandler;
import micheal65536.vienna.eventbus.client.RequestSender;
import micheal65536.vienna.eventbus.client.Subscriber;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Instance
{
	private static final long HOST_PLAYER_CONNECT_TIMEOUT = 20000;

	@NotNull
	public static Instance run(@NotNull EventBusClient eventBusClient, @Nullable String playerId, @NotNull String buildplateId, @NotNull BuildplateSource buildplateSource, @NotNull String instanceId, boolean survival, boolean night, boolean saveEnabled, @NotNull InventoryType inventoryType, @Nullable Long shutdownTime, @NotNull String publicAddress, int port, int serverInternalPort, @NotNull String javaCmd, @NotNull File fountainBridgeJar, @NotNull File serverTemplateDir, @NotNull String fabricJarName, @NotNull File connectorPluginJar, @NotNull File baseDir, @NotNull String eventBusConnectionString)
	{
		if (playerId == null && buildplateSource == BuildplateSource.PLAYER)
		{
			throw new IllegalArgumentException();
		}

		Instance instance = new Instance(eventBusClient, playerId, buildplateId, buildplateSource, instanceId, survival, night, saveEnabled, inventoryType, shutdownTime, publicAddress, port, serverInternalPort, javaCmd, fountainBridgeJar, serverTemplateDir, fabricJarName, connectorPluginJar, baseDir, eventBusConnectionString);
		instance.threadStartedSemaphore.acquireUninterruptibly();
		new Thread(() ->
		{
			instance.run();
		}, "Instance %s".formatted(instanceId)).start();
		instance.threadStartedSemaphore.acquireUninterruptibly();
		instance.threadStartedSemaphore.release();
		return instance;
	}

	private final EventBusClient eventBusClient;

	@Nullable
	private final String playerId;
	private final String buildplateId;
	private final BuildplateSource buildplateSource;
	public final String instanceId;
	private final boolean survival;
	private final boolean night;
	private final boolean saveEnabled;
	private final InventoryType inventoryType;
	private final Long shutdownTime;

	public final String publicAddress;
	public final int port;
	private final int serverInternalPort;

	private final String javaCmd;
	private final File fountainBridgeJar;
	private final File serverTemplateDir;
	private final String fabricJarName;
	private final File connectorPluginJar;
	private final File baseDir;
	private final String eventBusAddress;
	private final String eventBusQueueName;
	private final String connectorPluginArgString;

	private Thread thread;
	private final Semaphore threadStartedSemaphore = new Semaphore(1, true);
	private final CompletableFuture<Void> readyFuture = new CompletableFuture<>();
	private final Logger logger;

	private RequestSender requestSender = null;

	private Subscriber subscriber = null;
	private RequestHandler requestHandler = null;

	private File serverWorkDir;
	private File bridgeWorkDir;
	private Process serverProcess = null;
	private Process bridgeProcess = null;
	private boolean shuttingDown = false;
	private final ReentrantLock subprocessLock = new ReentrantLock(true);

	private volatile boolean hostPlayerConnected = false;

	private Instance(@NotNull EventBusClient eventBusClient, @Nullable String playerId, @NotNull String buildplateId, @NotNull BuildplateSource buildplateSource, @NotNull String instanceId, boolean survival, boolean night, boolean saveEnabled, @NotNull InventoryType inventoryType, @Nullable Long shutdownTime, @NotNull String publicAddress, int port, int serverInternalPort, @NotNull String javaCmd, @NotNull File fountainBridgeJar, @NotNull File serverTemplateDir, @NotNull String fabricJarName, @NotNull File connectorPluginJar, @NotNull File baseDir, @NotNull String eventBusConnectionString)
	{
		this.eventBusClient = eventBusClient;

		this.playerId = playerId;
		this.buildplateId = buildplateId;
		this.buildplateSource = buildplateSource;
		this.instanceId = instanceId;
		this.survival = survival;
		this.night = night;
		this.saveEnabled = saveEnabled;
		this.inventoryType = inventoryType;
		this.shutdownTime = shutdownTime;

		this.publicAddress = publicAddress;
		this.port = port;
		this.serverInternalPort = serverInternalPort;

		this.javaCmd = javaCmd;
		this.fountainBridgeJar = fountainBridgeJar;
		this.serverTemplateDir = serverTemplateDir;
		this.fabricJarName = fabricJarName;
		this.connectorPluginJar = connectorPluginJar;
		this.baseDir = baseDir;
		this.eventBusAddress = eventBusConnectionString;
		this.eventBusQueueName = "buildplate_" + this.instanceId;
		this.connectorPluginArgString = new Gson().newBuilder().serializeNulls().create().toJson(new ConnectorPluginArg(
				this.eventBusAddress,
				this.eventBusQueueName,
				this.inventoryType
		), ConnectorPluginArg.class);

		this.logger = LogManager.getLogger("Instance %s".formatted(this.instanceId));
	}

	private void run()
	{
		this.thread = Thread.currentThread();
		this.threadStartedSemaphore.release();

		try
		{
			switch (this.buildplateSource)
			{
				case PLAYER ->
				{
					this.logger.info("Starting for player {} buildplate {} (survival = {}, saveEnabled = {}, inventoryType = {})", this.playerId, this.buildplateId, this.survival, this.saveEnabled, this.inventoryType);
				}
				case SHARED ->
				{
					this.logger.info("Starting for shared buildplate {} (player = {}, survival = {}, saveEnabled = {}, inventoryType = {})", this.buildplateId, this.playerId, this.survival, this.saveEnabled, this.inventoryType);
				}
				case ENCOUNTER ->
				{
					this.logger.info("Starting for encounter buildplate {} (player = {}, survival = {}, saveEnabled = {}, inventoryType = {})", this.buildplateId, this.playerId, this.survival, this.saveEnabled, this.inventoryType);
				}
			}
			this.logger.info("Using port {} internal port {}", this.port, this.serverInternalPort);

			this.requestSender = this.eventBusClient.addRequestSender();

			this.logger.info("Setting up server");

			BuildplateLoadResponse buildplateLoadResponse = switch (this.buildplateSource)
			{
				case PLAYER -> this.sendEventBusRequestRaw("load", new BuildplateLoadRequest(this.playerId, this.buildplateId), BuildplateLoadResponse.class).join();
				case SHARED -> this.sendEventBusRequestRaw("loadShared", new SharedBuildplateLoadRequest(this.buildplateId), BuildplateLoadResponse.class).join();
				case ENCOUNTER -> this.sendEventBusRequestRaw("loadEncounter", new EncounterBuildplateLoadRequest(this.buildplateId), BuildplateLoadResponse.class).join();
			};

			byte[] serverData;
			try
			{
				serverData = Base64.getDecoder().decode(buildplateLoadResponse.serverDataBase64);
			}
			catch (IllegalArgumentException exception)
			{
				this.logger.error("Buildplate load response contained invalid base64 data");
				return;
			}

			try
			{
				this.serverWorkDir = this.setupServerFiles(serverData);
				if (this.serverWorkDir == null)
				{
					this.logger.error("Could not set up files for server");
					return;
				}
			}
			catch (IOException exception)
			{
				this.logger.error("Could not set up files for server", exception);
				return;
			}
			try
			{
				this.bridgeWorkDir = this.setupBridgeFiles(serverData);
				if (this.bridgeWorkDir == null)
				{
					this.logger.error("Could not set up files for bridge");
					return;
				}
			}
			catch (IOException exception)
			{
				this.logger.error("Could not set up files for bridge", exception);
				return;
			}

			this.logger.info("Running server");

			this.subscriber = this.eventBusClient.addSubscriber(this.eventBusQueueName, new Subscriber.SubscriberListener()
			{
				@Override
				public void event(@NotNull Subscriber.Event event)
				{
					Instance.this.handleConnectorEvent(event);
				}

				@Override
				public void error()
				{
					Instance.this.logger.error("Event bus subscriber error");
					Instance.this.beginShutdown();
				}
			});
			this.requestHandler = this.eventBusClient.addRequestHandler(this.eventBusQueueName, new RequestHandler.Handler()
			{
				@Override
				@Nullable
				public String request(@NotNull RequestHandler.Request request)
				{
					Object responseObject = Instance.this.handleConnectorRequest(request);
					if (responseObject != null)
					{
						Gson gson = new Gson().newBuilder().serializeNulls().create();
						return gson.toJson(responseObject);
					}
					else
					{
						return null;
					}
				}

				@Override
				public void error()
				{
					Instance.this.logger.error("Event bus request handler error");
					Instance.this.beginShutdown();
				}
			});

			this.subprocessLock.lock();
			if (!this.shuttingDown)
			{
				this.startServerProcess();
				if (this.serverProcess != null)
				{
					this.subprocessLock.unlock();
					int exitCode = waitForProcess(this.serverProcess);
					this.subprocessLock.lock();
					this.serverProcess = null;
					if (!this.shuttingDown)
					{
						this.logger.warn("Server process has unexpectedly terminated with exit code {}", exitCode);
					}
					else
					{
						this.logger.info("Server has finished with exit code {}", exitCode);
					}

					this.shuttingDown = true;

					if (this.bridgeProcess != null)
					{
						this.logger.info("Bridge is still running, shutting it down now");
						this.bridgeProcess.destroy();
						this.subprocessLock.unlock();
						exitCode = waitForProcess(this.bridgeProcess);
						this.subprocessLock.lock();
						this.bridgeProcess = null;
						this.logger.info("Bridge has finished with exit code {}", exitCode);
					}
				}
				else
				{
					this.logger.info("Server failed to start");
				}
			}
			this.subprocessLock.unlock();
		}
		catch (Exception exception)
		{
			this.logger.error("Unhandled exception", exception);
		}
		finally
		{
			if (this.subscriber != null)
			{
				this.subscriber.close();
			}
			if (this.requestHandler != null)
			{
				this.requestHandler.close();
			}
			if (this.requestSender != null)
			{
				this.requestSender.flush();
				this.requestSender.close();
			}

			this.cleanupBaseDir();

			this.logger.info("Finished");
		}
	}

	private void handleConnectorEvent(@NotNull Subscriber.Event event)
	{
		switch (event.type)
		{
			case "started" ->
			{
				Instance.this.logger.info("Server is ready");
				Instance.this.startBridgeProcess();
				Instance.this.readyFuture.complete(null);
				if (Instance.this.shutdownTime != null)
				{
					Instance.this.startShutdownTimer();
				}
				else
				{
					Instance.this.startHostPlayerConnectTimeout();
				}
			}
			case "saved" ->
			{
				if (this.saveEnabled)
				{
					WorldSavedMessage worldSavedMessage = this.readJson(event.data, WorldSavedMessage.class);
					if (worldSavedMessage != null)
					{
						if (this.hostPlayerConnected)
						{
							this.logger.info("Saving snapshot");
							this.sendEventBusRequest("saved", worldSavedMessage, null);
						}
						else
						{
							this.logger.info("Not saving snapshot because host player never connected");
						}
					}
				}
				else
				{
					this.logger.info("Ignoring save data because saving is disabled");
				}
			}
			case "inventoryAdd" ->
			{
				InventoryAddItemMessage inventoryAddItemMessage = this.readJson(event.data, InventoryAddItemMessage.class);
				if (inventoryAddItemMessage != null)
				{
					this.sendEventBusRequest("inventoryAdd", inventoryAddItemMessage, null);
				}
			}
			case "inventoryUpdateWear" ->
			{
				InventoryUpdateItemWearMessage inventoryUpdateItemWearMessage = this.readJson(event.data, InventoryUpdateItemWearMessage.class);
				if (inventoryUpdateItemWearMessage != null)
				{
					this.sendEventBusRequest("inventoryUpdateWear", inventoryUpdateItemWearMessage, null);
				}
			}
			case "inventorySetHotbar" ->
			{
				InventorySetHotbarMessage inventorySetHotbarMessage = this.readJson(event.data, InventorySetHotbarMessage.class);
				if (inventorySetHotbarMessage != null)
				{
					this.sendEventBusRequest("inventorySetHotbar", inventorySetHotbarMessage, null);
				}
			}
		}
	}

	@Nullable
	private Object handleConnectorRequest(@NotNull RequestHandler.Request request)
	{
		switch (request.type)
		{
			case "playerConnected" ->
			{
				PlayerConnectedRequest playerConnectedRequest = this.readJson(request.data, PlayerConnectedRequest.class);
				if (playerConnectedRequest != null)
				{
					if (this.playerId != null && !this.hostPlayerConnected && !playerConnectedRequest.uuid().equals(this.playerId))
					{
						this.logger.info("Rejecting player connection for player {} because the host player must connect first", playerConnectedRequest.uuid());
						return new PlayerConnectedResponse(false, null);
					}

					PlayerConnectedResponse playerConnectedResponse = this.sendEventBusRequest("playerConnected", playerConnectedRequest, PlayerConnectedResponse.class).join();
					if (playerConnectedResponse != null)
					{
						this.logger.info("Player {} has connected", playerConnectedRequest.uuid());

						if (this.playerId != null && !this.hostPlayerConnected && playerConnectedRequest.uuid().equals(this.playerId))
						{
							this.hostPlayerConnected = true;
						}

						return playerConnectedResponse;
					}
				}
			}
			case "playerDisconnected" ->
			{
				PlayerDisconnectedRequest playerDisconnectedRequest = this.readJson(request.data, PlayerDisconnectedRequest.class);
				if (playerDisconnectedRequest != null)
				{
					PlayerDisconnectedResponse playerDisconnectedResponse = this.sendEventBusRequest("playerDisconnected", playerDisconnectedRequest, PlayerDisconnectedResponse.class).join();
					if (playerDisconnectedResponse != null)
					{
						this.logger.info("Player {} has disconnected", playerDisconnectedRequest.playerId());

						if (this.shutdownTime == null && this.playerId != null && playerDisconnectedRequest.playerId().equals(this.playerId))
						{
							this.logger.info("Host player has disconnected, beginning shutdown");
							this.beginShutdown();
						}

						return playerDisconnectedResponse;
					}
				}
			}
			case "getInventory" ->
			{
				String playerId = this.readJson(request.data, String.class);
				if (playerId != null)
				{
					InventoryResponse inventoryResponse = this.sendEventBusRequest("getInventory", playerId, InventoryResponse.class).join();
					if (inventoryResponse != null)
					{
						return inventoryResponse;
					}
				}
			}
			case "inventoryRemove" ->
			{
				InventoryRemoveItemRequest inventoryRemoveItemRequest = this.readJson(request.data, InventoryRemoveItemRequest.class);
				if (inventoryRemoveItemRequest != null)
				{
					if (inventoryRemoveItemRequest.instanceId() != null)
					{
						Boolean success = this.sendEventBusRequest("inventoryRemove", inventoryRemoveItemRequest, Boolean.class).join();
						if (success != null)
						{
							return success;
						}
					}
					else
					{
						Integer removedCount = this.sendEventBusRequest("inventoryRemove", inventoryRemoveItemRequest, Integer.class).join();
						if (removedCount != null)
						{
							return removedCount;
						}
					}
				}
			}

			case "findPlayer" ->
			{
				FindPlayerIdRequest findPlayerIdRequest = this.readJson(request.data, FindPlayerIdRequest.class);
				if (findPlayerIdRequest != null)
				{
					// TODO
					return findPlayerIdRequest.minecraftName();
				}
			}
			case "getInitialPlayerState" ->
			{
				String playerId = this.readJson(request.data, String.class);
				if (playerId != null)
				{
					InitialPlayerStateResponse initialPlayerStateResponse = this.sendEventBusRequest("getInitialPlayerState", playerId, InitialPlayerStateResponse.class).join();
					if (initialPlayerStateResponse != null)
					{
						return initialPlayerStateResponse;
					}
				}
			}
		}
		return null;
	}

	@Nullable
	private <T> T readJson(@NotNull String string, @NotNull Class<T> tClass)
	{
		try
		{
			return new Gson().fromJson(string, tClass);
		}
		catch (Exception exception)
		{
			this.logger.error("Failed to decode event bus message JSON", exception);
			this.beginShutdown();
			return null;
		}
	}

	@NotNull
	private <T> CompletableFuture<T> sendEventBusRequest(@NotNull String type, @NotNull Object object, @Nullable Class<T> responseClass)
	{
		record RequestWithInstanceId(
				@NotNull String instanceId,
				@NotNull Object request
		)
		{
		}

		RequestWithInstanceId request = new RequestWithInstanceId(this.instanceId, object);

		try
		{
			return this.requestSender.request("buildplates", type, new Gson().newBuilder().serializeNulls().create().toJson(request)).thenApply(response ->
			{
				if (response == null)
				{
					this.logger.error("Event bus request failed (no response)");
					this.beginShutdown();
					return null;
				}
				if (responseClass != null)
				{
					return new Gson().fromJson(response, responseClass);
				}
				else
				{
					return null;
				}
			});
		}
		catch (Exception exception)
		{
			this.logger.error("Event bus request failed", exception);
			this.beginShutdown();
			return CompletableFuture.completedFuture(null);
		}
	}

	@NotNull
	private <T> CompletableFuture<T> sendEventBusRequestRaw(@NotNull String type, @NotNull Object object, @Nullable Class<T> responseClass)
	{
		try
		{
			return this.requestSender.request("buildplates", type, new Gson().newBuilder().serializeNulls().create().toJson(object)).thenApply(response ->
			{
				if (response == null)
				{
					this.logger.error("Event bus request failed (no response)");
					this.beginShutdown();
					return null;
				}
				if (responseClass != null)
				{
					return new Gson().fromJson(response, responseClass);
				}
				else
				{
					return null;
				}
			});
		}
		catch (Exception exception)
		{
			this.logger.error("Event bus request failed", exception);
			this.beginShutdown();
			return CompletableFuture.completedFuture(null);
		}
	}

	@Nullable
	private File setupServerFiles(byte[] serverData) throws IOException
	{
		File workDir = new File(this.baseDir, "server");
		if (!workDir.mkdir())
		{
			this.logger.error("Could not create server working directory");
			return null;
		}

		if (!copyServerFile(new File(this.serverTemplateDir, this.fabricJarName), new File(workDir, this.fabricJarName), false))
		{
			this.logger.error("Fabric JAR {} does not exist in server template directory", this.fabricJarName);
			return null;
		}
		boolean warnedMissingServerFiles = false;
		if (!copyServerFile(new File(new File(this.serverTemplateDir, ".fabric"), "server"), new File(new File(workDir, ".fabric"), "server"), true))
		{
			if (!warnedMissingServerFiles)
			{
				this.logger.warn("Server files were not pre-downloaded in server template directory, it is recommended to pre-download all server files to improve instance start-up time and reduce network data usage");
				warnedMissingServerFiles = true;
			}
		}
		if (!copyServerFile(new File(this.serverTemplateDir, "libraries"), new File(workDir, "libraries"), true))
		{
			if (!warnedMissingServerFiles)
			{
				this.logger.warn("Server files were not pre-downloaded in server template directory, it is recommended to pre-download all server files to improve instance start-up time and reduce network data usage");
				warnedMissingServerFiles = true;
			}
		}
		if (!copyServerFile(new File(this.serverTemplateDir, "versions"), new File(workDir, "versions"), true))
		{
			if (!warnedMissingServerFiles)
			{
				this.logger.warn("Server files were not pre-downloaded in server template directory, it is recommended to pre-download all server files to improve instance start-up time and reduce network data usage");
				warnedMissingServerFiles = true;
			}
		}
		if (!copyServerFile(new File(this.serverTemplateDir, "mods"), new File(workDir, "mods"), true))
		{
			this.logger.error("Mods directory was not present in server template directory, the buildplate server instance will not function correctly without the Fountain and Vienna Fabric mods installed");
		}

		Files.writeString(new File(workDir, "eula.txt").toPath(), "eula=true", StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW);

		String serverProperties = new StringBuilder()
				.append("online-mode=false\n")
				.append("enforce-secure-profile=false\n")
				.append("sync-chunk-writes=false\n")
				.append("spawn-protection=0\n")
				.append("server-port=%d\n".formatted(this.serverInternalPort))
				.append("gamemode=%s\n".formatted(this.survival ? "survival" : "creative"))
				.append("vienna-event-bus-address=%s\n".formatted(this.eventBusAddress))
				.append("vienna-event-bus-queue-name=%s\n".formatted(this.eventBusQueueName))
				.toString();
		Files.writeString(new File(workDir, "server.properties").toPath(), serverProperties, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW);

		File worldDir = new File(workDir, "world");
		if (!worldDir.mkdir())
		{
			this.logger.error("Could not create server world directory");
			return null;
		}
		File worldEntitiesDir = new File(worldDir, "entities");
		if (!worldEntitiesDir.mkdir())
		{
			this.logger.error("Could not create server world entities directory");
			return null;
		}
		File worldRegionDir = new File(worldDir, "region");
		if (!worldRegionDir.mkdir())
		{
			this.logger.error("Could not create server world regions directory");
			return null;
		}

		CompoundTag levelDatTag = createLevelDat(this.survival, this.night);
		NBTIO.writeFile(levelDatTag, new File(worldDir, "level.dat"));

		try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(serverData); ZipInputStream zipInputStream = new ZipInputStream(byteArrayInputStream))
		{
			for (ZipEntry zipEntry = zipInputStream.getNextEntry(); zipEntry != null; zipEntry = zipInputStream.getNextEntry())
			{
				if (zipEntry.isDirectory())
				{
					zipInputStream.closeEntry();
					continue;
				}

				File file = new File(worldDir, zipEntry.getName());
				Files.copy(zipInputStream, file.toPath());
				zipInputStream.closeEntry();
			}
		}

		return workDir;
	}

	private static boolean copyServerFile(@NotNull File src, @NotNull File dst, boolean directory) throws IOException
	{
		if (!src.exists())
		{
			return false;
		}

		if (directory)
		{
			Files.walkFileTree(src.toPath(), new FileVisitor<Path>()
			{
				@Override
				public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes basicFileAttributes) throws IOException
				{
					Path dstPath;
					try
					{
						dstPath = dst.toPath().resolve(src.toPath().relativize(path));
					}
					catch (IllegalArgumentException exception)
					{
						throw new IOException(exception);
					}
					Files.createDirectories(dstPath);
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFile(Path path, BasicFileAttributes basicFileAttributes) throws IOException
				{
					Path dstPath;
					try
					{
						dstPath = dst.toPath().resolve(src.toPath().relativize(path));
					}
					catch (IllegalArgumentException exception)
					{
						throw new IOException(exception);
					}
					Files.copy(path, dstPath);
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFileFailed(Path path, IOException exception) throws IOException
				{
					if (exception != null)
					{
						throw exception;
					}
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult postVisitDirectory(Path path, IOException exception) throws IOException
				{
					if (exception != null)
					{
						throw exception;
					}
					return FileVisitResult.CONTINUE;
				}
			});
		}
		else
		{
			Files.copy(src.toPath(), dst.toPath());
		}
		return true;
	}

	@NotNull
	private static CompoundTag createLevelDat(boolean survival, boolean night)
	{
		CompoundTag dataTag = new NbtBuilder.Compound()
				.put("GameType", survival ? 0 : 1)
				.put("Difficulty", 1)
				.put("DayTime", !night ? 6000 : 18000)
				.put("GameRules", new NbtBuilder.Compound()
						.put("doDaylightCycle", "false")
						.put("doWeatherCycle", "false")
						.put("doMobSpawning", "false")
						.put("fountain:doMobDespawn", "false")
				)
				.put("WorldGenSettings", new NbtBuilder.Compound()
						.put("seed", (long) 0)    // TODO
						.put("generate_features", (byte) 0)
						.put("dimensions", new NbtBuilder.Compound()
								.put("minecraft:overworld", new NbtBuilder.Compound()
										.put("type", "minecraft:overworld")
										.put("generator", new NbtBuilder.Compound()
												.put("type", "fountain:wrapper")
												.put("buildplate", new NbtBuilder.Compound()
														.put("ground_level", 63))
												.put("inner", new NbtBuilder.Compound()
														.put("type", "minecraft:noise")
														.put("settings", "minecraft:overworld")
														.put("biome_source", new NbtBuilder.Compound()
																.put("type", "minecraft:multi_noise")
																.put("preset", "minecraft:overworld")
														)
												)
										)
								)
								.put("minecraft:the_nether", new NbtBuilder.Compound()
										.put("type", "minecraft:the_nether")
										.put("generator", new NbtBuilder.Compound()
												.put("type", "fountain:wrapper")
												.put("buildplate", new NbtBuilder.Compound()
														.put("ground_level", 32))
												.put("inner", new NbtBuilder.Compound()
														.put("type", "minecraft:noise")
														.put("settings", "minecraft:nether")
														.put("biome_source", new NbtBuilder.Compound()
																.put("type", "minecraft:fixed")
																.put("biome", "minecraft:nether_wastes")
														)
												)
										)
								)
						)
				)
				.put("DataVersion", 3700)
				.put("version", 19133)
				.put("Version", new NbtBuilder.Compound()
						.put("Id", 3700)
						.put("Name", "1.20.4")
						.put("Series", "main")
						.put("Snapshot", (byte) 0)
				)
				.put("initialized", (byte) 1)
				.build("Data");

		CompoundTag tag = new CompoundTag("");
		tag.put(dataTag);
		return tag;
	}

	@Nullable
	private File setupBridgeFiles(byte[] serverData) throws IOException
	{
		File workDir = new File(this.baseDir, "bridge");
		if (!workDir.mkdir())
		{
			this.logger.error("Could not create bridge working directory");
			return null;
		}

		// empty

		return workDir;
	}

	private void cleanupBaseDir()
	{
		this.logger.info("Cleaning up runtime directory");
		try
		{
			Files.walkFileTree(this.baseDir.toPath(), new FileVisitor<Path>()
			{
				@Override
				public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes basicFileAttributes) throws IOException
				{
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFile(Path path, BasicFileAttributes basicFileAttributes) throws IOException
				{
					Files.delete(path);
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFileFailed(Path path, IOException exception) throws IOException
				{
					if (exception != null)
					{
						throw exception;
					}
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult postVisitDirectory(Path path, IOException exception) throws IOException
				{
					if (exception != null)
					{
						throw exception;
					}
					Files.delete(path);
					return FileVisitResult.CONTINUE;
				}
			});
		}
		catch (IOException exception)
		{
			this.logger.error("Exception while cleaning up runtime directory", exception);
		}
	}

	private void startServerProcess()
	{
		this.subprocessLock.lock();

		if (this.shuttingDown)
		{
			this.logger.debug("Already shutting down, not starting server process");
			this.subprocessLock.unlock();
			return;
		}
		if (this.serverProcess != null)
		{
			this.logger.debug("Server process has already been started");
			this.subprocessLock.unlock();
			return;
		}

		this.logger.info("Starting server process");

		try
		{
			this.serverProcess = new ProcessBuilder()
					.command(this.javaCmd, "-jar", this.fabricJarName, "-nogui")
					.directory(this.serverWorkDir)
					.redirectOutput(ProcessBuilder.Redirect.PIPE)
					.redirectErrorStream(true)
					.start();
			new Thread(() ->
			{
				try
				{
					InputStream inputStream = this.serverProcess.getInputStream();
					InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
					BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
					String line;
					while ((line = bufferedReader.readLine()) != null)
					{
						this.logger.debug("[server] %s".formatted(line));
					}
				}
				catch (IOException exception)
				{
					this.logger.debug("Error reading server process log output", exception);
				}
			}).start();
			this.logger.info("Server process started, PID {}", this.serverProcess.pid());
		}
		catch (IOException exception)
		{
			this.logger.error("Could not start server process", exception);
		}

		this.subprocessLock.unlock();
	}

	private void startBridgeProcess()
	{
		this.subprocessLock.lock();

		if (this.shuttingDown)
		{
			this.logger.debug("Already shutting down, not starting bridge process");
			this.subprocessLock.unlock();
			return;
		}
		if (this.bridgeProcess != null)
		{
			this.logger.debug("Bridge process has already been started");
			this.subprocessLock.unlock();
			return;
		}

		this.logger.info("Starting bridge process");

		try
		{
			Process process = new ProcessBuilder()
					.command(this.javaCmd, "-jar", this.fountainBridgeJar.getAbsolutePath(),
							"-port", Integer.toString(this.port),
							"-serverAddress", "127.0.0.1",
							"-serverPort", Integer.toString(this.serverInternalPort),
							"-connectorPluginJar", this.connectorPluginJar.getAbsolutePath(),
							"-connectorPluginClass", "micheal65536.vienna.buildplate.connector.plugin.ViennaConnectorPlugin",
							"-connectorPluginArg", this.connectorPluginArgString,
							"-useUUIDAsUsername")
					.directory(this.bridgeWorkDir)
					.redirectOutput(ProcessBuilder.Redirect.PIPE)
					.redirectErrorStream(true)
					.start();
			new Thread(() ->
			{
				try
				{
					InputStream inputStream = process.getInputStream();
					InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
					BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
					String line;
					while ((line = bufferedReader.readLine()) != null)
					{
						this.logger.debug("[bridge] %s".formatted(line));
					}
				}
				catch (IOException exception)
				{
					this.logger.debug("Error reading bridge process log output", exception);
				}
			}).start();
			this.bridgeProcess = process;
			this.logger.info("Bridge process started, PID {}", this.bridgeProcess.pid());

			new Thread(() ->
			{
				waitForProcess(process);
				this.subprocessLock.lock();
				if (!this.shuttingDown)
				{
					this.logger.warn("Bridge process has unexpectedly terminated with exit code {}", process.exitValue());
					this.bridgeProcess = null;
					this.beginShutdown();
				}
				this.subprocessLock.unlock();
			}).start();
		}
		catch (IOException exception)
		{
			this.logger.error("Could not start bridge process", exception);
		}

		this.subprocessLock.unlock();
	}

	private void startHostPlayerConnectTimeout()
	{
		new Thread(() ->
		{
			try
			{
				Thread.sleep(HOST_PLAYER_CONNECT_TIMEOUT);
			}
			catch (InterruptedException exception)
			{
				throw new AssertionError(exception);
			}

			this.subprocessLock.lock();
			if (this.shuttingDown)
			{
				this.subprocessLock.unlock();
				return;
			}
			this.subprocessLock.unlock();

			if (!this.hostPlayerConnected)
			{
				this.logger.info("Host player has not connected yet, shutting down");
				this.beginShutdown();
			}
		}).start();
	}

	private void startShutdownTimer()
	{
		new Thread(() ->
		{
			if (this.shutdownTime != null)
			{
				long currentTime = System.currentTimeMillis();
				while (currentTime < this.shutdownTime)
				{
					long duration = this.shutdownTime - currentTime;
					if (duration > 0)
					{
						this.logger.info("Server will shut down in {} milliseconds", duration);
						try
						{
							Thread.sleep(duration > 2000 ? (duration / 2) : duration);
						}
						catch (InterruptedException exception)
						{
							throw new AssertionError(exception);
						}
					}

					currentTime = System.currentTimeMillis();
				}
			}

			this.logger.info("Shutdown time has been reached, shutting down");
			this.beginShutdown();
		}).start();
	}

	private void beginShutdown()
	{
		new Thread(() ->
		{
			this.subprocessLock.lock();

			if (this.shuttingDown)
			{
				this.logger.debug("Already shutting down, not beginning shutdown");
				this.subprocessLock.unlock();
				return;
			}
			this.shuttingDown = true;

			this.logger.info("Beginning shutdown");

			if (this.bridgeProcess != null)
			{
				this.logger.info("Waiting for bridge to shut down");
				this.bridgeProcess.destroy();
				this.subprocessLock.unlock();
				int exitCode = waitForProcess(this.bridgeProcess);
				this.subprocessLock.lock();
				this.bridgeProcess = null;
				this.logger.info("Bridge has finished with exit code {}", exitCode);
			}

			if (this.serverProcess != null)
			{
				this.logger.info("Asking the server to shut down");
				this.serverProcess.destroy();
			}

			this.subprocessLock.unlock();
		}).start();
	}

	private static int waitForProcess(@NotNull Process process)
	{
		int exitCode;
		for (; ; )
		{
			try
			{
				exitCode = process.waitFor();
				break;
			}
			catch (InterruptedException exception)
			{
				continue;
			}
		}
		return exitCode;
	}

	public void waitForReady()
	{
		for (; ; )
		{
			try
			{
				this.readyFuture.get(1000, TimeUnit.MILLISECONDS);
				break;
			}
			catch (InterruptedException | ExecutionException exception)
			{
				continue;
			}
			catch (TimeoutException exception)
			{
				if (!this.thread.isAlive())
				{
					break;
				}
			}
		}
	}

	public void waitForShutdown()
	{
		for (; ; )
		{
			try
			{
				this.thread.join();
				break;
			}
			catch (InterruptedException exception)
			{
				continue;
			}
		}
	}

	private record BuildplateLoadRequest(
			@NotNull String playerId,
			@NotNull String buildplateId
	)
	{
	}

	private record SharedBuildplateLoadRequest(
			@NotNull String sharedBuildplateId
	)
	{
	}

	private record EncounterBuildplateLoadRequest(
			@NotNull String encounterBuildplateId
	)
	{
	}

	private record BuildplateLoadResponse(
			@NotNull String serverDataBase64
	)
	{
	}

	public enum BuildplateSource
	{
		PLAYER,
		SHARED,
		ENCOUNTER
	}
}