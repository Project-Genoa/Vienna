package micheal65536.vienna.buildplate.launcher;

import com.github.steveice10.opennbt.NBTIO;
import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import com.google.gson.Gson;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import micheal65536.vienna.buildplate.connector.model.InventoryAddItemMessage;
import micheal65536.vienna.buildplate.connector.model.InventoryRemoveItemMessage;
import micheal65536.vienna.buildplate.connector.model.InventorySetHotbarMessage;
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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
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
	@NotNull
	public static Instance run(@NotNull EventBusClient eventBusClient, @NotNull String playerId, @NotNull String buildplateId, @NotNull String instanceId, boolean survival, boolean night, @NotNull String publicAddress, int port, int serverInternalPort, @NotNull String javaCmd, @NotNull File fountainBridgeJar, @NotNull File serverTemplateDir, @NotNull String fabricJarName, @NotNull File connectorPluginJar, @NotNull File baseDir, @NotNull String eventBusConnectionString)
	{
		Instance instance = new Instance(eventBusClient, playerId, buildplateId, instanceId, survival, night, publicAddress, port, serverInternalPort, javaCmd, fountainBridgeJar, serverTemplateDir, fabricJarName, connectorPluginJar, baseDir, eventBusConnectionString);
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

	private final String playerId;
	private final String buildplateId;
	public final String instanceId;
	private final boolean survival;
	private final boolean night;

	public final String publicAddress;
	public final int port;
	private final int serverInternalPort;

	private final String javaCmd;
	private final File fountainBridgeJar;
	private final File serverTemplateDir;
	private final String fabricJarName;
	private final File connectorPluginJar;
	private final File baseDir;
	private final String eventBusConnectionString;

	private Thread thread;
	private final Semaphore threadStartedSemaphore = new Semaphore(1, true);
	private final CompletableFuture<Void> readyFuture = new CompletableFuture<>();
	private final Logger logger;

	private RequestSender requestSender = null;

	private final String eventBusQueueName;
	private Subscriber subscriber = null;
	private RequestHandler requestHandler = null;

	private File serverWorkDir;
	private File bridgeWorkDir;
	private Process serverProcess = null;
	private Process bridgeProcess = null;
	private boolean shuttingDown = false;
	private final ReentrantLock subprocessLock = new ReentrantLock(true);

	private Instance(@NotNull EventBusClient eventBusClient, @NotNull String playerId, @NotNull String buildplateId, @NotNull String instanceId, boolean survival, boolean night, @NotNull String publicAddress, int port, int serverInternalPort, @NotNull String javaCmd, @NotNull File fountainBridgeJar, @NotNull File serverTemplateDir, @NotNull String fabricJarName, @NotNull File connectorPluginJar, @NotNull File baseDir, @NotNull String eventBusConnectionString)
	{
		this.eventBusClient = eventBusClient;

		this.playerId = playerId;
		this.buildplateId = buildplateId;
		this.instanceId = instanceId;
		this.survival = survival;
		this.night = night;

		this.publicAddress = publicAddress;
		this.port = port;
		this.serverInternalPort = serverInternalPort;

		this.javaCmd = javaCmd;
		this.fountainBridgeJar = fountainBridgeJar;
		this.serverTemplateDir = serverTemplateDir;
		this.fabricJarName = fabricJarName;
		this.connectorPluginJar = connectorPluginJar;
		this.baseDir = baseDir;
		this.eventBusConnectionString = eventBusConnectionString;

		this.logger = LogManager.getLogger("Instance %s".formatted(this.instanceId));

		this.eventBusQueueName = "buildplate_" + this.instanceId;
	}

	private void run()
	{
		this.thread = Thread.currentThread();
		this.threadStartedSemaphore.release();

		try
		{
			this.logger.info("Starting for buildplate {} player {}", this.buildplateId, this.playerId);
			this.logger.info("Using port {} internal port {}", this.port, this.serverInternalPort);

			this.requestSender = this.eventBusClient.addRequestSender();

			this.logger.info("Setting up server");

			BuildplateLoadResponse buildplateLoadResponse = this.sendEventBusRequestRaw("load", new BuildplateLoadRequest(this.playerId, this.buildplateId), BuildplateLoadResponse.class).join();
			if (buildplateLoadResponse == null)
			{
				this.logger.error("Could not load buildplate information for buildplate {} player {}", this.buildplateId, this.playerId);
				return;
			}

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
			}
			case "saved" ->
			{
				WorldSavedMessage worldSavedMessage = this.readJson(event.data, WorldSavedMessage.class);
				if (worldSavedMessage != null)
				{
					this.logger.info("Saving snapshot");
					this.sendEventBusRequest("saved", worldSavedMessage, null);
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
			case "inventoryRemove" ->
			{
				InventoryRemoveItemMessage inventoryRemoveItemMessage = this.readJson(event.data, InventoryRemoveItemMessage.class);
				if (inventoryRemoveItemMessage != null)
				{
					this.sendEventBusRequest("inventoryRemove", inventoryRemoveItemMessage, null);
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
					if (playerConnectedRequest.uuid().equals(this.playerId))    // TODO: probably remove this eventually and put in API server
					{
						PlayerConnectedResponse playerConnectedResponse = this.sendEventBusRequest("playerConnected", playerConnectedRequest, PlayerConnectedResponse.class).join();
						if (playerConnectedResponse != null)
						{
							this.logger.info("Player {} has connected", playerConnectedRequest.uuid());
							return playerConnectedResponse;
						}
					}
					else
					{
						return new PlayerConnectedResponse(false, null);
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

						if (playerDisconnectedRequest.playerId().equals(this.playerId))
						{
							this.logger.info("Host player has disconnected, beginning shutdown");
							this.beginShutdown();
						}

						return playerDisconnectedResponse;
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
		record RequestWithBuildplateIds(
				@NotNull String playerId,
				@NotNull String buildplateId,
				@NotNull String instanceId,
				@NotNull Object request
		)
		{
		}

		RequestWithBuildplateIds request = new RequestWithBuildplateIds(this.playerId, this.buildplateId, this.instanceId, object);

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
		if (!copyServerFile(new File(this.serverTemplateDir, ".fabric/server"), new File(workDir, ".fabric/server"), true))
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
			this.logger.error("Mods directory was not present in server template directory, the buildplate server instance will not function correctly without the Fountain Fabric mod installed");
		}

		Files.writeString(new File(workDir, "eula.txt").toPath(), "eula=true", StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW);

		String serverProperties = new StringBuilder()
				.append("online-mode=false\n")
				.append("enforce-secure-profile=false\n")
				.append("spawn-protection=0\n")
				.append("server-port=%d\n".formatted(this.serverInternalPort))
				.append("fountain-connector-plugin-jar=%s\n".formatted(this.connectorPluginJar.getAbsolutePath()))
				.append("fountain-connector-plugin-class=micheal65536.vienna.buildplate.connector.plugin.ViennaConnectorPlugin\n")
				.append("fountain-connector-plugin-arg=%s/%s\n".formatted(this.eventBusConnectionString, this.eventBusQueueName))
				.append("gamemode=%s\n".formatted(this.survival ? "survival" : "creative"))
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
					.command(this.javaCmd, "-jar", "./" + this.fabricJarName, "-nogui")
					.directory(this.serverWorkDir)
					.redirectOutput(ProcessBuilder.Redirect.to(new File("log_%s-server".formatted(this.instanceId))))
					.redirectErrorStream(true)
					.start();
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
							"-connectorPluginArg", this.eventBusConnectionString + "/" + this.eventBusQueueName)
					.directory(this.bridgeWorkDir)
					.redirectOutput(ProcessBuilder.Redirect.to(new File("log_%s-bridge".formatted(this.instanceId))))
					.redirectErrorStream(true)
					.start();
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

	private record BuildplateLoadResponse(
			@NotNull String serverDataBase64
	)
	{
	}
}