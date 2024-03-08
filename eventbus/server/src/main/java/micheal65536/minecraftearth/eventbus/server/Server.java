package micheal65536.minecraftearth.eventbus.server;

import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class Server
{
	private final ReentrantReadWriteLock subscribersLock = new ReentrantReadWriteLock(true);
	private final HashMap<String, LinkedHashSet<Subscriber>> subscribers = new HashMap<>();

	public Server()
	{
		// empty
	}

	@Nullable
	public Subscriber addSubscriber(@NotNull String queueName, @NotNull Consumer<Subscriber.Message> consumer)
	{
		if (!validateQueueName(queueName))
		{
			return null;
		}

		LogManager.getLogger().debug("Adding subscriber for {}", queueName);

		this.subscribersLock.writeLock().lock();

		Subscriber subscriber = new Subscriber(queueName, consumer);
		this.subscribers.computeIfAbsent(queueName, name -> new LinkedHashSet<>()).add(subscriber);

		this.subscribersLock.writeLock().unlock();

		return subscriber;
	}

	public final class Subscriber
	{
		private final String queueName;
		private final Consumer<Message> consumer;
		private boolean ended = false;

		private Subscriber(String queueName, Consumer<Message> consumer)
		{
			this.queueName = queueName;
			this.consumer = consumer;
		}

		public synchronized void remove()
		{
			this.ended = true;

			new Thread(() ->
			{
				LogManager.getLogger().debug("Removing subscriber");
				Server.this.subscribersLock.writeLock().lock();
				LinkedHashSet<Subscriber> subscribers = Server.this.subscribers.getOrDefault(queueName, null);
				if (subscribers != null)
				{
					subscribers.remove(Subscriber.this);
				}
				Server.this.subscribersLock.writeLock().unlock();
			}).start();
		}

		private synchronized void push(@NotNull EntryMessage entryMessage)
		{
			if (!this.ended)
			{
				this.consumer.accept(entryMessage);
			}
		}

		private synchronized void error()
		{
			if (!this.ended)
			{
				this.consumer.accept(new ErrorMessage());
				this.ended = true;
			}
		}

		public static abstract class Message
		{
			private Message()
			{
				// empty
			}
		}

		public static final class EntryMessage extends Message
		{
			public final long timestamp;
			public final String type;
			public final String data;

			private EntryMessage(long timestamp, String type, String data)
			{
				this.timestamp = timestamp;
				this.type = type;
				this.data = data;
			}
		}

		public static final class ErrorMessage extends Message
		{
			private ErrorMessage()
			{
				// empty
			}
		}
	}

	private Stream<Subscriber> getSubscribers(@NotNull String queueName)
	{
		LinkedHashSet<Subscriber> subscribers = this.subscribers.getOrDefault(queueName, null);
		if (subscribers != null)
		{
			return subscribers.stream();
		}
		else
		{
			return Stream.empty();
		}
	}

	@NotNull
	public Publisher addPublisher()
	{
		LogManager.getLogger().debug("Adding publisher");
		return new Publisher();
	}

	public final class Publisher
	{
		private boolean closed = false;

		private Publisher()
		{
			// empty
		}

		public void remove()
		{
			LogManager.getLogger().debug("Removing publisher");
			this.closed = true;
		}

		public boolean publish(@NotNull String queueName, long timestamp, @NotNull String type, @NotNull String data)
		{
			if (this.closed)
			{
				throw new IllegalStateException();
			}

			if (!validateQueueName(queueName))
			{
				return false;
			}
			if (!validateType(type))
			{
				return false;
			}
			if (!validateData(data))
			{
				return false;
			}

			Server.this.subscribersLock.readLock().lock();

			Subscriber.EntryMessage message = new Subscriber.EntryMessage(timestamp, type, data);
			Server.this.getSubscribers(queueName).forEach(subscriber -> subscriber.push(message));

			Server.this.subscribersLock.readLock().unlock();

			return true;
		}
	}

	private static boolean validateQueueName(String queueName)
	{
		if (queueName.chars().anyMatch(character -> character < 32 || character >= 127) || queueName.isEmpty() || queueName.matches("[^A-Za-z0-9_\\-]") || queueName.matches("^[^A-Za-z0-9]"))
		{
			return false;
		}
		return true;
	}

	private static boolean validateType(String type)
	{
		if (type.chars().anyMatch(character -> character < 32 || character >= 127) || type.isEmpty() || type.matches("[^A-Za-z0-9_\\-]") || type.matches("^[^A-Za-z0-9]"))
		{
			return false;
		}
		return true;
	}

	private static boolean validateData(@NotNull String string)
	{
		return string.chars().noneMatch(character -> character < 32 || character >= 127);
	}
}