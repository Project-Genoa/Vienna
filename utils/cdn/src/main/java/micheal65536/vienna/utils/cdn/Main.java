package micheal65536.vienna.utils.cdn;

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
import micheal65536.vienna.utils.http.routing.Request;
import micheal65536.vienna.utils.http.routing.Response;
import micheal65536.vienna.utils.http.routing.Router;
import micheal65536.vienna.utils.http.routing.ServerErrorException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;

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
				.option("resourcePackPath")
				.hasArg()
				.argName("path")
				.desc("Resource pack path, defaults to /availableresourcepack/resourcepacks/dba38e59-091a-4826-b76a-a08d7de5a9e2-1301b0c257a311678123b9e7325d0d6c61db3c35")
				.build());
		options.addOption(Option.builder()
				.option("resourcePackFile")
				.hasArg()
				.required()
				.argName("file")
				.desc("Resource pack file")
				.build());
		CommandLine commandLine;
		int httpPort;
		String resourcePackPath;
		String resourcePackFile;
		try
		{
			commandLine = new DefaultParser().parse(options, args);
			httpPort = commandLine.hasOption("port") ? (int) (long) commandLine.getParsedOptionValue("port") : 8080;
			resourcePackPath = commandLine.hasOption("resourcePackPath") ? commandLine.getOptionValue("resourcePackPath") : "/availableresourcepack/resourcepacks/dba38e59-091a-4826-b76a-a08d7de5a9e2-1301b0c257a311678123b9e7325d0d6c61db3c35";
			resourcePackFile = commandLine.getOptionValue("resourcePackFile");
		}
		catch (ParseException exception)
		{
			LogManager.getLogger().fatal(exception);
			System.exit(1);
			return;
		}

		Application application = buildApplication(resourcePackPath, resourcePackFile);

		startServer(httpPort, application);
	}

	@NotNull
	private static Application buildApplication(@NotNull String resourcePackPath, @NotNull String resourcePackFile)
	{
		Application application = new Application();

		application.router.addHandler(new Router.Route.Builder(Request.Method.HEAD, resourcePackPath).build(), request ->
		{
			try
			{
				BasicFileAttributes basicFileAttributes = Files.readAttributes(new File(resourcePackFile).toPath(), BasicFileAttributes.class);
				return Response.create(200).contentType("application/zip").header("Content-Length", Long.toString(basicFileAttributes.size()));
			}
			catch (IOException exception)
			{
				throw new ServerErrorException(exception);
			}
		});
		application.router.addHandler(new Router.Route.Builder(Request.Method.GET, resourcePackPath).build(), request ->
		{
			byte[] data;
			try (FileInputStream fileInputStream = new FileInputStream(new File(resourcePackFile)))
			{
				data = fileInputStream.readAllBytes();
			}
			catch (IOException exception)
			{
				throw new ServerErrorException(exception);
			}
			return Response.ok(data, "application/zip").header("Content-Length", Integer.toString(data.length));
		});

		return application;
	}

	private static void startServer(int port, @NotNull Application application)
	{
		LogManager.getLogger().info("Starting embedded Tomcat server");
		File tomcatDir = null;
		try
		{
			tomcatDir = Files.createTempDirectory("vienna-utils-cdn-tomcat-").toFile();
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