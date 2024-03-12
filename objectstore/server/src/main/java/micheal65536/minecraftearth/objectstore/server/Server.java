package micheal65536.minecraftearth.objectstore.server;

import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Server
{
	private final DataStore dataStore;

	public Server(@NotNull DataStore dataStore)
	{
		this.dataStore = dataStore;
	}

	@Nullable
	public String store(byte[] data)
	{
		try
		{
			String id = this.dataStore.store(data);
			LogManager.getLogger().info("Stored new object {}", id);
			return id;
		}
		catch (DataStore.DataStoreException exception)
		{
			LogManager.getLogger().error("Could not store object", exception);
			return null;
		}
	}

	public byte[] load(@NotNull String id)
	{
		LogManager.getLogger().info("Request for object {}", id);
		try
		{
			byte[] data = this.dataStore.load(id);
			if (data == null)
			{
				LogManager.getLogger().info("Requested object {} does not exist", id);
			}
			return data;
		}
		catch (DataStore.DataStoreException exception)
		{
			LogManager.getLogger().error("Could not load object {}", id, exception);
			return null;
		}
	}

	public boolean delete(@NotNull String id)
	{
		LogManager.getLogger().info("Request to delete object {}", id);
		this.dataStore.delete(id);
		return true;
	}
}