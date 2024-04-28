package micheal65536.vienna.buildplate.launcher;

import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public final class JavaLocator
{
	@NotNull
	public static String locateJava()
	{
		LogManager.getLogger().info("Trying to locate Java");

		boolean windows = System.getProperty("os.name").startsWith("Windows");

		String javaName = windows ? "java.exe" : "java";

		Map<String, String> env = System.getenv();

		String javaHome = env.getOrDefault("JAVA_HOME", "");
		if (!javaHome.isEmpty())
		{
			LogManager.getLogger().info("Trying JAVA_HOME");
			try
			{
				File file = new File(new File(new File(javaHome), "bin"), javaName).getCanonicalFile();
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

		LogManager.getLogger().info("Using \"%s\"".formatted(javaName));
		return javaName;
	}
}