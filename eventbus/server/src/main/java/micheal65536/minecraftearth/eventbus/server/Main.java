package micheal65536.minecraftearth.eventbus.server;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.config.Configurator;

import java.io.IOException;

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
				.desc("Port to listen on, defaults to 5532")
				.build());
		CommandLine commandLine;
		int port;
		try
		{
			commandLine = new DefaultParser().parse(options, args);
			port = commandLine.hasOption("port") ? (int) commandLine.getParsedOptionValue("port") : 5532;
		}
		catch (ParseException exception)
		{
			LogManager.getLogger().fatal(exception);
			System.exit(1);
			return;
		}

		NetworkServer server;
		try
		{
			server = new NetworkServer(new Server(), port);
		}
		catch (IOException exception)
		{
			LogManager.getLogger().fatal(exception);
			System.exit(1);
			return;
		}
		server.run();
	}
}