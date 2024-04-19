package micheal65536.vienna.buildplate.launcher;

import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import micheal65536.vienna.db.EarthDB;
import micheal65536.vienna.eventbus.client.EventBusClient;
import micheal65536.vienna.objectstore.client.ObjectStoreClient;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;

public class Starter
{
	private final EarthDB earthDB;
	private final ObjectStoreClient objectStoreClient;
	private final EventBusClient eventBusClient;

	private final String publicAddress;
	private final String javaCmd;
	private final File tmpDir;
	private final String eventBusConnectionString;
	private final String apiServerAddress;
	private final String apiServerToken;

	private final File fountainBridgeJar;
	private final File serverTemplateDir;
	private final String fabricJarName;
	private final File connectorPluginJar;

	private static final int BASE_PORT = 19132;
	private static final int SERVER_INTERNAL_BASE_PORT = 25565;
	private final HashSet<Integer> portsInUse = new HashSet<>();
	private final HashSet<Integer> serverInternalPortsInUse = new HashSet<>();

	public Starter(@NotNull EarthDB earthDB, @NotNull ObjectStoreClient objectStoreClient, @NotNull EventBusClient eventBusClient, @NotNull String eventBusConnectionString, @NotNull String apiServerAddress, @NotNull String apiServerToken, @NotNull String publicAddress, @NotNull String bridgeJar, @NotNull String serverTemplateDir, @NotNull String fabricJarName, @NotNull String connectorPluginJar)
	{
		this.earthDB = earthDB;
		this.objectStoreClient = objectStoreClient;
		this.eventBusClient = eventBusClient;

		this.publicAddress = publicAddress;
		this.javaCmd = locateJava();
		this.tmpDir = new File(System.getProperty("java.io.tmpdir"));
		this.eventBusConnectionString = eventBusConnectionString;
		this.apiServerAddress = apiServerAddress;
		this.apiServerToken = apiServerToken;

		this.fountainBridgeJar = new File(bridgeJar).getAbsoluteFile();
		this.serverTemplateDir = new File(serverTemplateDir).getAbsoluteFile();
		this.fabricJarName = fabricJarName;
		this.connectorPluginJar = new File(connectorPluginJar);
	}

	@Nullable
	public Instance startInstance(@NotNull String instanceId, @NotNull String playerId, @NotNull String buildplateId, boolean survival, boolean night)
	{
		File baseDir = this.createInstanceBaseDir(instanceId);
		if (baseDir == null)
		{
			return null;
		}
		int port = findPort(this.portsInUse, BASE_PORT);
		int serverInternalPort = findPort(this.serverInternalPortsInUse, SERVER_INTERNAL_BASE_PORT);
		Instance instance = Instance.run(this.earthDB, this.objectStoreClient, this.eventBusClient, playerId, buildplateId, instanceId, survival, night, this.publicAddress, port, serverInternalPort, this.javaCmd, this.fountainBridgeJar, this.serverTemplateDir, this.fabricJarName, this.connectorPluginJar, baseDir, this.eventBusConnectionString, this.apiServerAddress, this.apiServerToken);
		new Thread(() ->
		{
			instance.waitForShutdown();
			releasePort(this.portsInUse, port);
			releasePort(this.serverInternalPortsInUse, serverInternalPort);
		}).start();
		return instance;
	}

	private static int findPort(@NotNull HashSet<Integer> portsInUse, int basePort)
	{
		synchronized (portsInUse)
		{
			int port = basePort;
			while (portsInUse.contains(port))
			{
				port++;
			}
			portsInUse.add(port);
			return port;
		}
	}

	private static void releasePort(@NotNull HashSet<Integer> portsInUse, int port)
	{
		synchronized (portsInUse)
		{
			if (!portsInUse.remove(port))
			{
				throw new AssertionError();
			}
		}
	}

	@NotNull
	private static String locateJava()
	{
		LogManager.getLogger().info("Trying to locate Java");

		Map<String, String> env = System.getenv();

		String javaHome = env.getOrDefault("JAVA_HOME", "");
		if (!javaHome.isEmpty())
		{
			LogManager.getLogger().info("Trying JAVA_HOME");
			try
			{
				File file = new File(new File(new File(javaHome), "bin"), "java").getCanonicalFile();
				if (file.canExecute())
				{
					String path = file.getPath();
					LogManager.getLogger().info("Using Java from JAVA_HOME ({})", path);
					return path;
				}
			}
			catch (IOException exception)
			{
				// empty
			}
			LogManager.getLogger().info("Java from JAVA_HOME is not suitable (does not exist or cannot be accessed)");
		}
		else
		{
			LogManager.getLogger().info("JAVA_HOME is not set");
		}

		LogManager.getLogger().info("Using \"java\"");
		return "java";
	}

	@Nullable
	private File createInstanceBaseDir(@NotNull String instanceId)
	{
		File file = new File(this.tmpDir, "vienna-buildplate-instance_%s".formatted(instanceId));
		if (!file.mkdir())
		{
			LogManager.getLogger().error("Error creating instance base directory for {}", instanceId);
			return null;
		}
		LogManager.getLogger().debug("Created instance base directory {}", file.getPath());
		return file;
	}
}