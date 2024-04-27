package micheal65536.vienna.buildplate.launcher;

import com.google.gson.Gson;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;

public final class PreviewGenerator
{
	private final String javaCmd;
	private final File fountainJar;

	public PreviewGenerator(@NotNull String javaCmd, @NotNull String fountainJar)
	{
		this.javaCmd = javaCmd;
		this.fountainJar = new File(fountainJar).getAbsoluteFile();
	}

	@Nullable
	public String generatePreview(byte[] serverData, boolean isNight)
	{
		byte[] previewBytes;
		try
		{
			Process process = new ProcessBuilder()
					.command(this.javaCmd, "-cp", this.fountainJar.getAbsolutePath(), "micheal65536.fountain.preview.PreviewGenerator")
					.redirectInput(ProcessBuilder.Redirect.PIPE)
					.redirectOutput(ProcessBuilder.Redirect.PIPE)
					.redirectError(ProcessBuilder.Redirect.DISCARD)
					.start();
			LogManager.getLogger().debug("Started preview generator subprocess with PID {}", process.pid());
			process.getOutputStream().write(serverData);
			process.getOutputStream().flush();
			previewBytes = process.getInputStream().readAllBytes();
			if (process.isAlive())
			{
				LogManager.getLogger().warn("Preview generator subprocess is still running, waiting for it to exit");
			}
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
			LogManager.getLogger().debug("Preview generator subprocess finished with exit code {}", exitCode);
		}
		catch (IOException exception)
		{
			LogManager.getLogger().error("Error while running buildplate preview generator subprocess", exception);
			return null;
		}

		HashMap<String, Object> previewObject;
		try
		{
			previewObject = new Gson().fromJson(new String(previewBytes, StandardCharsets.UTF_8), HashMap.class);
		}
		catch (Exception exception)
		{
			LogManager.getLogger().error("Error while processing buildplate preview generator response", exception);
			return null;
		}

		previewObject.put("isNight", isNight);

		String previewJson = new Gson().newBuilder().serializeNulls().create().toJson(previewObject);

		String previewBase64 = new String(Base64.getEncoder().encode(previewJson.getBytes(StandardCharsets.UTF_8)), StandardCharsets.US_ASCII);

		return previewBase64;
	}
}