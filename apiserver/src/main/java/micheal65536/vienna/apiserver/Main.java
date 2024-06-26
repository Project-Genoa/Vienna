package micheal65536.vienna.apiserver;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.config.Configurator;
import org.jetbrains.annotations.NotNull;

import micheal65536.vienna.apiserver.routes.AuthenticatedRouter;
import micheal65536.vienna.apiserver.routes.ResourcePacksRouter;
import micheal65536.vienna.apiserver.routes.SigninRouter;
import micheal65536.vienna.apiserver.routing.Application;
import micheal65536.vienna.apiserver.routing.Router;
import micheal65536.vienna.apiserver.utils.BuildplateInstanceRequestHandler;
import micheal65536.vienna.apiserver.utils.BuildplateInstancesManager;
import micheal65536.vienna.db.DatabaseException;
import micheal65536.vienna.db.EarthDB;
import micheal65536.vienna.eventbus.client.EventBusClient;
import micheal65536.vienna.eventbus.client.EventBusClientException;
import micheal65536.vienna.objectstore.client.ObjectStoreClient;
import micheal65536.vienna.objectstore.client.ObjectStoreClientException;
import micheal65536.vienna.staticdata.StaticData;
import micheal65536.vienna.staticdata.StaticDataException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class Main
{
	public static void main(String[] args)
	{
		Configurator.setRootLevel(Level.DEBUG);

		Options options = new Options();
		options.addOption(Option.builder()
				.option("port")
				.hasArg()
				.argName("port")
				.type(Number.class)
				.desc("Port to listen on, defaults to 8080")
				.build());
		options.addOption(Option.builder()
				.option("db")
				.hasArg()
				.argName("db")
				.desc("Database path, defaults to ./earth.db")
				.build());
		options.addOption(Option.builder()
				.option("staticData")
				.hasArg()
				.argName("dir")
				.desc("Static data path, defaults to ./data")
				.build());
		options.addOption(Option.builder()
				.option("eventbus")
				.hasArg()
				.argName("eventbus")
				.desc("Event bus address, defaults to localhost:5532")
				.build());
		options.addOption(Option.builder()
				.option("objectstore")
				.hasArg()
				.argName("objectstore")
				.desc("Object storage address, defaults to localhost:5396")
				.build());
		CommandLine commandLine;
		int httpPort;
		String dbConnectionString;
		String staticDataPath;
		String eventBusConnectionString;
		String objectStoreConnectionString;
		try
		{
			commandLine = new DefaultParser().parse(options, args);
			httpPort = commandLine.hasOption("port") ? (int) (long) commandLine.getParsedOptionValue("port") : 8080;
			dbConnectionString = commandLine.hasOption("db") ? commandLine.getOptionValue("db") : "./earth.db";
			staticDataPath = commandLine.hasOption("staticData") ? commandLine.getOptionValue("staticData") : "./data";
			eventBusConnectionString = commandLine.hasOption("eventbus") ? commandLine.getOptionValue("eventbus") : "localhost:5532";
			objectStoreConnectionString = commandLine.hasOption("objectstore") ? commandLine.getOptionValue("objectstore") : "localhost:5396";
		}
		catch (ParseException exception)
		{
			LogManager.getLogger().fatal(exception);
			System.exit(1);
			return;
		}

		LogManager.getLogger().info("Loading static data");
		StaticData staticData;
		try
		{
			staticData = new StaticData(new File(staticDataPath));
		}
		catch (StaticDataException staticDataException)
		{
			LogManager.getLogger().fatal("Failed to load static data", staticDataException);
			System.exit(1);
			return;
		}
		LogManager.getLogger().info("Loaded static data");

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

		Application application = buildApplication(earthDB, staticData, eventBusClient, objectStoreClient);

		startServer(httpPort, application);
	}

	@NotNull
	private static Application buildApplication(@NotNull EarthDB earthDB, @NotNull StaticData staticData, @NotNull EventBusClient eventBusClient, @NotNull ObjectStoreClient objectStoreClient)
	{
		Application application = new Application();
		Router router = new Router();
		application.router.addSubRouter("/*", 0, router);

		BuildplateInstancesManager buildplateInstancesManager = new BuildplateInstancesManager(eventBusClient);

		router.addSubRouter("/auth/api/v1.1/*", 3, new SigninRouter());    // for some reason MCE uses the base path from the previous session when switching users without restarting the app
		router.addSubRouter("/auth/api/v1.1/*", 3, new AuthenticatedRouter(earthDB, staticData, eventBusClient, objectStoreClient, buildplateInstancesManager));
		router.addSubRouter("/api/v1.1/*", 2, new SigninRouter());
		router.addSubRouter("/api/v1.1/*", 2, new ResourcePacksRouter());

		BuildplateInstanceRequestHandler.start(earthDB, eventBusClient, objectStoreClient, buildplateInstancesManager, staticData.catalog);

		return application;
	}

	private static void startServer(int port, @NotNull Application application)
	{
		LogManager.getLogger().info("Starting embedded Tomcat server");
		File tomcatDir = null;
		try
		{
			tomcatDir = Files.createTempDirectory("vienna-apiserver-tomcat-").toFile();
		}
		catch (IOException exception)
		{
			LogManager.getLogger().fatal("Could not start Tomcat server", exception);
			System.exit(1);
			return;
		}
		File baseDir = new File(tomcatDir, "baseDir");
		baseDir.mkdir();
		File docBase = new File(tomcatDir, "docBase");
		docBase.mkdir();
		Tomcat tomcat = new Tomcat();
		tomcat.setBaseDir(baseDir.getAbsolutePath());
		Connector connector = new Connector();
		connector.setPort(port);
		tomcat.setConnector(connector);
		Context context = tomcat.addContext("", docBase.getAbsolutePath());
		tomcat.addServlet("", "", new HttpServlet()
		{
			@Override
			protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
			{
				application.handleRequest(request, response);
			}
		});
		context.addServletMappingDecoded("/*", "");
		try
		{
			tomcat.start();
		}
		catch (LifecycleException exception)
		{
			LogManager.getLogger().fatal("Could not start Tomcat server", exception);
			System.exit(1);
			return;
		}

		LogManager.getLogger().info("Started");
	}
}