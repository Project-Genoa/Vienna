package micheal65536.vienna.apiserver.utils;

import com.google.gson.Gson;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import micheal65536.vienna.db.model.player.Buildplates;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;

public final class BuildplatePreviewGenerator
{
	private final String command;

	public BuildplatePreviewGenerator(@NotNull String command)
	{
		this.command = command;
	}

	@Nullable
	public String generatePreview(@NotNull Buildplates.Buildplate buildplate, byte[] serverData)
	{
		boolean isNight = buildplate.night;

		byte[] previewBytes;
		try
		{
			Process process = Runtime.getRuntime().exec(this.command);
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