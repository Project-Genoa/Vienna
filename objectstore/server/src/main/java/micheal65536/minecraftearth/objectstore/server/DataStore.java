package micheal65536.minecraftearth.objectstore.server;

import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;

public class DataStore
{
	private final File rootDirectory;

	public DataStore(@NotNull File rootDirectory) throws DataStoreException
	{
		this.rootDirectory = rootDirectory;
		if (!this.rootDirectory.isDirectory() || !this.rootDirectory.canRead())
		{
			throw new DataStoreException("Data root directory %s is not a directory or cannot be read".formatted(this.rootDirectory.getPath()));
		}
		LogManager.getLogger().info("Opened data store from {}", this.rootDirectory.getPath());
	}

	@NotNull
	public String store(byte[] data) throws DataStoreException
	{
		String id = UUID.randomUUID().toString();

		File file = new File(this.rootDirectory, id.substring(0, 2));
		if (!file.isDirectory())
		{
			file.mkdir();
		}
		file = new File(file, id);

		try (FileOutputStream fileOutputStream = new FileOutputStream(file))
		{
			fileOutputStream.write(data);
		}
		catch (IOException exception)
		{
			file.delete();
			throw new DataStoreException(exception);
		}

		return id;
	}

	public byte[] load(@NotNull String id) throws DataStoreException
	{
		File file = new File(new File(this.rootDirectory, id.substring(0, 2)), id);
		if (!file.exists())
		{
			return null;
		}

		ByteArrayOutputStream byteArrayOutputStream;
		try
		{
			byteArrayOutputStream = new ByteArrayOutputStream((int) Files.size(file.toPath()));
		}
		catch (IOException exception)
		{
			throw new DataStoreException(exception);
		}

		try (FileInputStream fileInputStream = new FileInputStream(file))
		{
			fileInputStream.transferTo(byteArrayOutputStream);
		}
		catch (IOException exception)
		{
			throw new DataStoreException(exception);
		}

		byte[] data = byteArrayOutputStream.toByteArray();

		return data;
	}

	public void delete(@NotNull String id)
	{
		File file = new File(new File(this.rootDirectory, id.substring(0, 2)), id);
		file.delete();
	}

	public static final class DataStoreException extends Exception
	{
		private DataStoreException(String message)
		{
			super(message);
		}

		private DataStoreException(Throwable cause)
		{
			super(cause);
		}
	}
}