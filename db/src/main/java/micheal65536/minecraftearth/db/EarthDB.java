package micheal65536.minecraftearth.db;

import com.google.gson.Gson;
import org.jetbrains.annotations.NotNull;

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
import java.util.function.Function;

public final class EarthDB implements AutoCloseable
{
	@NotNull
	public static EarthDB open(@NotNull String connectionString) throws DatabaseException
	{
		return new EarthDB(connectionString);
	}

	private final String connectionString;
	private final LinkedHashSet<Transaction> transactions = new LinkedHashSet<>();

	private EarthDB(@NotNull String connectionString)
	{
		this.connectionString = connectionString;
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
				Connection connection = DriverManager.getConnection("jdbc:sqlite:" + this.connectionString);
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
		private final LinkedList<ReadObjectsEntry> readObjects = new LinkedList<>();
		private Function<Results, Query> thenFunction = null;

		private record WriteObjectsEntry(@NotNull String type, @NotNull String id, @NotNull Object value)
		{
		}

		private record ReadObjectsEntry(@NotNull String type, @NotNull String id, @NotNull Class<?> valueClass, @NotNull String asName)
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
		public <T> Query get(@NotNull String type, @NotNull String id, @NotNull Class<T> valueClass)
		{
			this.get(type, id, valueClass, type);
			return this;
		}

		@NotNull
		public <T> Query get(@NotNull String type, @NotNull String id, @NotNull Class<T> valueClass, @NotNull String as)
		{
			this.readObjects.add(new ReadObjectsEntry(type, id, valueClass, as));
			return this;
		}

		@NotNull
		public Query then(@NotNull Function<Results, Query> function)
		{
			this.thenFunction = function;
			return this;
		}

		@NotNull
		public Results execute(@NotNull EarthDB earthDB) throws DatabaseException
		{
			try (Transaction transaction = earthDB.transaction(this.write))
			{
				Results results = this.executeInternal(transaction, this.write);
				transaction.commit();
				return results;
			}
			catch (SQLException exception)
			{
				throw new DatabaseException(exception);
			}
		}

		@NotNull
		private Results executeInternal(@NotNull Transaction transaction, boolean write) throws DatabaseException, SQLException
		{
			if (this.write && !write)
			{
				throw new UnsupportedOperationException();
			}

			for (WriteObjectsEntry entry : this.writeObjects)
			{
				String json = new Gson().newBuilder().serializeNulls().create().toJson(entry.value);
				PreparedStatement statement = transaction.connection.prepareStatement("INSERT OR REPLACE INTO objects(type, id, value, version) VALUES (?, ?, ?, COALESCE((SELECT version FROM objects WHERE type == ? AND id == ?), 1) + 1)");
				statement.setString(1, entry.type);
				statement.setString(2, entry.id);
				statement.setString(3, json);
				statement.setString(4, entry.type);
				statement.setString(5, entry.id);
				statement.execute();
			}

			Results results = new Results();
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
						Object value = new Gson().fromJson(json, entry.valueClass);
						results.map.put(entry.asName, new Results.Result<>(value, version));
					}
					else
					{
						try
						{
							Constructor constructor = entry.valueClass.getDeclaredConstructor();
							Object value = constructor.newInstance();
							results.map.put(entry.asName, new Results.Result<>(value, 1));
						}
						catch (ReflectiveOperationException exception)
						{
							throw new DatabaseException(exception);
						}
					}
				}
			}

			if (this.thenFunction != null)
			{
				Query query = this.thenFunction.apply(results);
				results = query.executeInternal(transaction, write);
			}

			return results;
		}
	}

	public static class Results
	{
		private final HashMap<String, Result<?>> map = new HashMap<>();

		private Results()
		{
			// empty
		}

		@NotNull
		public <T> Result<T> get(@NotNull String name)
		{
			Result<T> value = (Result<T>) this.map.getOrDefault(name, null);
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