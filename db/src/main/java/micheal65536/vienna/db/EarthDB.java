package micheal65536.vienna.db;

import com.google.gson.Gson;
import org.jetbrains.annotations.NotNull;

import micheal65536.vienna.db.model.player.ActivityLog;
import micheal65536.vienna.db.model.player.Tokens;

import java.lang.reflect.Constructor;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.function.Function;

public final class EarthDB implements AutoCloseable
{
	private static final int TRANSACTION_TIMEOUT = 60000;

	@NotNull
	public static EarthDB open(@NotNull String connectionString) throws DatabaseException
	{
		return new EarthDB(connectionString);
	}

	private final String connectionString;
	private final Properties properties;
	private final LinkedHashSet<Transaction> transactions = new LinkedHashSet<>();

	private EarthDB(@NotNull String connectionString) throws DatabaseException
	{
		this.connectionString = connectionString;
		this.properties = new Properties();
		this.properties.put("busy_timeout", Integer.toString(TRANSACTION_TIMEOUT));

		try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + this.connectionString, this.properties))
		{
			Statement statement = connection.createStatement();
			statement.execute("CREATE TABLE IF NOT EXISTS objects (type STRING NOT NULL, id STRING NOT NULL, value STRING NOT NULL, version INTEGER NOT NULL, PRIMARY KEY (type, id))");
			statement.close();
		}
		catch (SQLException exception)
		{
			throw new DatabaseException(exception);
		}
	}

	@Override
	public void close()
	{
		synchronized (this)
		{
			for (Transaction transaction : this.transactions.toArray(Transaction[]::new))
			{
				try
				{
					transaction.close();
				}
				catch (DatabaseException exception)
				{
					// empty
				}
			}
		}
	}

	@NotNull
	private Transaction transaction(boolean write) throws DatabaseException
	{
		synchronized (this)
		{
			try
			{
				Connection connection = DriverManager.getConnection("jdbc:sqlite:" + this.connectionString, this.properties);
				Transaction transaction = new Transaction(connection, write);
				this.transactions.add(transaction);
				return transaction;
			}
			catch (SQLException exception)
			{
				throw new DatabaseException(exception);
			}
		}
	}

	private final class Transaction implements AutoCloseable
	{
		public final Connection connection;
		private boolean committed = false;

		public Transaction(@NotNull Connection connection, boolean write) throws DatabaseException
		{
			this.connection = connection;

			try
			{
				Statement statement = this.connection.createStatement();
				statement.execute(write ? "BEGIN IMMEDIATE TRANSACTION" : "BEGIN DEFERRED TRANSACTION");
				statement.close();
			}
			catch (SQLException exception)
			{
				try
				{
					this.connection.close();
				}
				catch (SQLException exception1)
				{
					// empty
				}
				throw new DatabaseException(exception);
			}
		}

		@Override
		public void close() throws DatabaseException
		{
			if (!this.committed)
			{
				try
				{
					Statement statement = this.connection.createStatement();
					statement.execute("ROLLBACK TRANSACTION");
					statement.close();
				}
				catch (SQLException exception)
				{
					throw new DatabaseException(exception);
				}
			}

			synchronized (EarthDB.this)
			{
				EarthDB.this.transactions.remove(this);
				try
				{
					this.connection.close();
				}
				catch (SQLException exception)
				{
					// empty
				}
			}
		}

		public void commit() throws DatabaseException
		{
			try
			{
				Statement statement = this.connection.createStatement();
				statement.execute("COMMIT TRANSACTION");
				statement.close();
			}
			catch (SQLException exception)
			{
				throw new DatabaseException(exception);
			}
			this.committed = true;
		}
	}

	public static class Query
	{
		private final boolean write;
		private final LinkedList<WriteObjectsEntry> writeObjects = new LinkedList<>();
		private final LinkedList<BumpEntry> bumps = new LinkedList<>();
		private final LinkedList<ReadObjectsEntry> readObjects = new LinkedList<>();
		private final LinkedList<ExtrasEntry> extras = new LinkedList<>();
		private final LinkedList<ThenFunctionEntry> thenFunctions = new LinkedList<>();

		private record WriteObjectsEntry(@NotNull String type, @NotNull String id, @NotNull Object value)
		{
		}

		private record BumpEntry(@NotNull String type, @NotNull String id, @NotNull Class<?> valueClass)
		{
		}

		private record ReadObjectsEntry(@NotNull String type, @NotNull String id, @NotNull Class<?> valueClass)
		{
		}

		private record ExtrasEntry(@NotNull String name, @NotNull Object value)
		{
		}

		private record ThenFunctionEntry(@NotNull Function<Results, Query> function, boolean replaceResults)
		{
		}

		public Query(boolean write)
		{
			this.write = write;
		}

		@NotNull
		public <T> Query update(@NotNull String type, @NotNull String id, @NotNull T value)
		{
			if (!this.write)
			{
				throw new UnsupportedOperationException();
			}
			this.writeObjects.add(new WriteObjectsEntry(type, id, value));
			return this;
		}

		@NotNull
		public <T> Query bump(@NotNull String type, @NotNull String id, @NotNull Class<T> valueClass)
		{
			if (!this.write)
			{
				throw new UnsupportedOperationException();
			}
			this.bumps.add(new BumpEntry(type, id, valueClass));
			return this;
		}

		@NotNull
		public <T> Query get(@NotNull String type, @NotNull String id, @NotNull Class<T> valueClass)
		{
			this.readObjects.add(new ReadObjectsEntry(type, id, valueClass));
			return this;
		}

		@NotNull
		public <T> Query extra(@NotNull String name, @NotNull T value)
		{
			this.extras.add(new ExtrasEntry(name, value));
			return this;
		}

		@NotNull
		public Query then(@NotNull Function<Results, Query> function, boolean replaceResults)
		{
			this.thenFunctions.add(new ThenFunctionEntry(function, replaceResults));
			return this;
		}

		@NotNull
		public Query then(@NotNull Function<Results, Query> function)
		{
			return this.then(function, true);
		}

		@NotNull
		public Query then(@NotNull Query query, boolean replaceResults)
		{
			return this.then(results -> query, replaceResults);
		}

		@NotNull
		public Query then(@NotNull Query query)
		{
			return this.then(query, true);
		}

		@NotNull
		public Results execute(@NotNull EarthDB earthDB) throws DatabaseException
		{
			try (Transaction transaction = earthDB.transaction(this.write))
			{
				HashMap<String, Integer> updates = new HashMap<>();
				Results results = this.executeInternal(transaction, this.write, updates);
				transaction.commit();
				results.updates.putAll(updates);
				return results;
			}
			catch (SQLException exception)
			{
				throw new DatabaseException(exception);
			}
		}

		@NotNull
		private Results executeInternal(@NotNull Transaction transaction, boolean write, @NotNull HashMap<String, Integer> updates) throws DatabaseException, SQLException
		{
			if (this.write && !write)
			{
				throw new UnsupportedOperationException();
			}

			Results results = new Results();

			for (WriteObjectsEntry entry : this.writeObjects)
			{
				String json = toJson(entry.value);
				PreparedStatement statement = transaction.connection.prepareStatement("INSERT OR REPLACE INTO objects(type, id, value, version) VALUES (?, ?, ?, COALESCE((SELECT version FROM objects WHERE type == ? AND id == ?), 1) + 1)");
				statement.setString(1, entry.type);
				statement.setString(2, entry.id);
				statement.setString(3, json);
				statement.setString(4, entry.type);
				statement.setString(5, entry.id);
				statement.execute();

				statement = transaction.connection.prepareStatement("SELECT version FROM objects WHERE type == ? AND id == ?");
				statement.setString(1, entry.type);
				statement.setString(2, entry.id);
				statement.execute();
				ResultSet resultSet = statement.getResultSet();
				if (resultSet.next())
				{
					int version = resultSet.getInt("version");
					updates.put(entry.type, version);
				}
				else
				{
					throw new DatabaseException("Could not query updated object");
				}
			}

			for (BumpEntry entry : this.bumps)
			{
				Integer version;
				try (PreparedStatement statement = transaction.connection.prepareStatement("SELECT version FROM objects WHERE type == ? AND id == ?"))
				{
					statement.setString(1, entry.type);
					statement.setString(2, entry.id);
					statement.execute();
					ResultSet resultSet = statement.getResultSet();
					if (resultSet.next())
					{
						version = resultSet.getInt("version");
					}
					else
					{
						version = null;
					}
				}

				int resultVersion;
				if (version != null)
				{
					try (PreparedStatement statement = transaction.connection.prepareStatement("UPDATE objects SET version = ? WHERE type == ? AND id == ?"))
					{
						statement.setInt(1, version + 1);
						statement.setString(2, entry.type);
						statement.setString(3, entry.id);
						statement.execute();
					}
					resultVersion = version + 1;
				}
				else
				{
					Object value = createNewInstance(entry.valueClass);
					String json = toJson(value);
					try (PreparedStatement statement = transaction.connection.prepareStatement("INSERT INTO objects(type, id, value, version) VALUES (?, ?, ?, 2)"))
					{
						statement.setString(1, entry.type);
						statement.setString(2, entry.id);
						statement.setString(3, json);
						statement.execute();
					}
					resultVersion = 2;
				}
				updates.put(entry.type, resultVersion);
			}

			for (ReadObjectsEntry entry : this.readObjects)
			{
				try (PreparedStatement statement = transaction.connection.prepareStatement("SELECT value, version FROM objects WHERE type == ? AND id == ?"))
				{
					statement.setString(1, entry.type);
					statement.setString(2, entry.id);
					statement.execute();
					ResultSet resultSet = statement.getResultSet();
					if (resultSet.next())
					{
						String json = resultSet.getString("value");
						int version = resultSet.getInt("version");
						Object value = fromJson(json, entry.valueClass);
						results.getValues.put(entry.type, new Results.Result<>(value, version));
					}
					else
					{
						results.getValues.put(entry.type, new Results.Result<>(createNewInstance(entry.valueClass), 1));
					}
				}
			}

			for (ExtrasEntry entry : this.extras)
			{
				results.extras.put(entry.name, entry.value);
			}

			for (ThenFunctionEntry entry : this.thenFunctions)
			{
				Query query = entry.function.apply(results);
				Results innerResults = query.executeInternal(transaction, write, updates);
				if (entry.replaceResults)
				{
					results = innerResults;
				}
			}

			return results;
		}

		@NotNull
		private static <T> T fromJson(@NotNull String json, @NotNull Class<T> valueClass)
		{
			return new Gson().newBuilder()
					.registerTypeAdapter(Tokens.Token.class, new Tokens.Token.Deserializer())
					.registerTypeAdapter(ActivityLog.Entry.class, new ActivityLog.Entry.Deserializer())
					.create().fromJson(json, valueClass);
		}

		@NotNull
		private static String toJson(@NotNull Object value)
		{
			return new Gson().newBuilder().serializeNulls().create().toJson(value);
		}

		@NotNull
		private static <T> T createNewInstance(@NotNull Class<T> valueClass) throws DatabaseException
		{
			try
			{
				Constructor<T> constructor = valueClass.getDeclaredConstructor();
				T value = constructor.newInstance();
				return value;
			}
			catch (ReflectiveOperationException exception)
			{
				throw new DatabaseException(exception);
			}
		}
	}

	public static class Results
	{
		private final HashMap<String, Result<?>> getValues = new HashMap<>();
		private final HashMap<String, Object> extras = new HashMap<>();
		private final HashMap<String, Integer> updates = new HashMap<>();

		private Results()
		{
			// empty
		}

		@NotNull
		public <T> Result<T> get(@NotNull String name)
		{
			Result<T> value = (Result<T>) this.getValues.getOrDefault(name, null);
			if (value == null)
			{
				throw new NoSuchElementException();
			}
			return value;
		}

		@NotNull
		public HashMap<String, Integer> getUpdates()
		{
			return new HashMap<>(this.updates);
		}

		@NotNull
		public <T> T getExtra(@NotNull String name)
		{
			T value = (T) this.extras.getOrDefault(name, null);
			if (value == null)
			{
				throw new NoSuchElementException();
			}
			return value;
		}

		public record Result<T>(
				@NotNull T value,
				int version
		)
		{
		}
	}
}