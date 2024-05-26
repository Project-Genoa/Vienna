package micheal65536.vienna.utils.locator;

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

import micheal65536.vienna.utils.http.routing.Application;
import micheal65536.vienna.utils.http.routing.Handler;
import micheal65536.vienna.utils.http.routing.Request;
import micheal65536.vienna.utils.http.routing.Response;
import micheal65536.vienna.utils.http.routing.Router;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;

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
				.option("api")
				.hasArg()
				.required()
				.argName("address")
				.desc("API address")
				.build());
		options.addOption(Option.builder()
				.option("cdn")
				.hasArg()
				.required()
				.argName("address")
				.desc("CDN address")
				.build());
		options.addOption(Option.builder()
				.option("playfabTitleId")
				.hasArg()
				.required()
				.argName("id")
				.desc("PlayFab title ID")
				.build());
		CommandLine commandLine;
		int httpPort;
		String apiAddress;
		String cdnAddress;
		String playfabTitleId;
		try
		{
			commandLine = new DefaultParser().parse(options, args);
			httpPort = commandLine.hasOption("port") ? (int) (long) commandLine.getParsedOptionValue("port") : 8080;
			apiAddress = commandLine.getOptionValue("api");
			cdnAddress = commandLine.getOptionValue("cdn");
			playfabTitleId = commandLine.getOptionValue("playfabTitleId");
		}
		catch (ParseException exception)
		{
			LogManager.getLogger().fatal(exception);
			System.exit(1);
			return;
		}

		Application application = buildApplication(apiAddress, cdnAddress, playfabTitleId);

		startServer(httpPort, application);
	}

	@NotNull
	private static Application buildApplication(@NotNull String apiAddress, @NotNull String cdnAddress, @NotNull String playfabTitleId)
	{
		Application application = new Application();

		Handler locatorHandler = request ->
		{
			record LocatorResponse(
					@NotNull Result result,
					@NotNull HashMap<String, Integer> updates
			)
			{
				record Result(
						@NotNull HashMap<String, ServiceEnvironment> serviceEnvironments,
						@NotNull HashMap<String, String[]> supportedEnvironments
				)
				{
					record ServiceEnvironment(
							@NotNull String serviceUri,
							@NotNull String cdnUri,
							@NotNull String playfabTitleId
					)
					{
					}
				}
			}
			LocatorResponse locatorResponse = new LocatorResponse(new LocatorResponse.Result(new HashMap<>(), new HashMap<>()), new HashMap<>());
			locatorResponse.result.serviceEnvironments.put("production", new LocatorResponse.Result.ServiceEnvironment(apiAddress, cdnAddress, playfabTitleId));
			locatorResponse.result.supportedEnvironments.put("2020.1217.02", new String[]{"production"});
			return Response.okFromJson(locatorResponse, LocatorResponse.class);
		};
		application.router.addHandler(new Router.Route.Builder(Request.Method.GET, "/player/environment").addQueryParameter("buildNumber").build(), locatorHandler);
		application.router.addHandler(new Router.Route.Builder(Request.Method.GET, "/api/v1.1/player/environment").addQueryParameter("buildNumber").build(), locatorHandler);    // for some reason MCE sometimes includes the "/api/v1.1" prefix on the locator URL and sometimes does not

		return application;
	}

	private static void startServer(int port, @NotNull Application application)
	{
		LogManager.getLogger().info("Starting embedded Tomcat server");
		File tomcatDir = null;
		try
		{
			tomcatDir = Files.createTempDirectory("vienna-utils-locator-tomcat-").toFile();
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