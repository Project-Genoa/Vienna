package micheal65536.vienna.eventbus.server;

import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

public class Server
{
	private final ReentrantReadWriteLock subscribersLock = new ReentrantReadWriteLock(true);
	private final HashMap<String, LinkedHashSet<Subscriber>> subscribers = new HashMap<>();

	private final ReentrantReadWriteLock requestHandlersLock = new ReentrantReadWriteLock(true);
	private final HashMap<String, LinkedHashSet<RequestHandler>> requestHandlers = new HashMap<>();

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

	@Nullable
	public Server.RequestHandler addRequestHandler(@NotNull String queueName, @NotNull Function<RequestHandler.Request, CompletableFuture<String>> requestHandler, @NotNull Consumer<RequestHandler.ErrorMessage> errorConsumer)
	{
		if (!validateQueueName(queueName))
		{
			return null;
		}

		LogManager.getLogger().debug("Adding request handler for {}", queueName);

		this.requestHandlersLock.writeLock().lock();

		RequestHandler handler = new RequestHandler(queueName, requestHandler, errorConsumer);
		this.requestHandlers.computeIfAbsent(queueName, name -> new LinkedHashSet<>()).add(handler);

		this.requestHandlersLock.writeLock().unlock();

		return handler;
	}

	public final class RequestHandler
	{
		private final String queueName;
		private final Function<Request, CompletableFuture<String>> requestHandler;
		private final Consumer<ErrorMessage> errorConsumer;
		private boolean ended = false;

		private RequestHandler(String queueName, Function<Request, CompletableFuture<String>> requestHandler, Consumer<ErrorMessage> errorConsumer)
		{
			this.queueName = queueName;
			this.requestHandler = requestHandler;
			this.errorConsumer = errorConsumer;
		}

		public synchronized void remove()
		{
			this.ended = true;

			new Thread(() ->
			{
				LogManager.getLogger().debug("Removing handler");
				Server.this.requestHandlersLock.writeLock().lock();
				LinkedHashSet<RequestHandler> requestHandlers = Server.this.requestHandlers.getOrDefault(this.queueName, null);
				if (requestHandlers != null)
				{
					requestHandlers.remove(RequestHandler.this);
				}
				Server.this.requestHandlersLock.writeLock().unlock();
			}).start();
		}

		@Nullable
		private synchronized CompletableFuture<String> request(@NotNull Request request)
		{
			if (!this.ended)
			{
				return this.requestHandler.apply(request);
			}
			else
			{
				return null;
			}
		}

		private synchronized void error()
		{
			if (!this.ended)
			{
				this.errorConsumer.accept(new ErrorMessage());
				this.ended = true;
			}
		}

		public static final class Request
		{
			public final long timestamp;
			public final String type;
			public final String data;

			private Request(long timestamp, String type, String data)
			{
				this.timestamp = timestamp;
				this.type = type;
				this.data = data;
			}
		}

		public static final class ErrorMessage
		{
			private ErrorMessage()
			{
				// empty
			}
		}
	}

	private Stream<RequestHandler> getHandlers(@NotNull String queueName)
	{
		LinkedHashSet<RequestHandler> requestHandlers = this.requestHandlers.getOrDefault(queueName, null);
		if (requestHandlers != null)
		{
			return requestHandlers.stream();
		}
		else
		{
			return Stream.empty();
		}
	}

	@NotNull
	public RequestSender addRequestSender()
	{
		LogManager.getLogger().debug("Adding request sender");
		return new RequestSender();
	}

	public final class RequestSender
	{
		private boolean closed = false;

		private RequestSender()
		{
			// empty
		}

		public void remove()
		{
			LogManager.getLogger().debug("Removing request sender");
			this.closed = true;
		}

		@Nullable
		public CompletableFuture<String> request(@NotNull String queueName, long timestamp, @NotNull String type, @NotNull String data)
		{
			if (this.closed)
			{
				throw new IllegalStateException();
			}

			if (!validateQueueName(queueName))
			{
				return null;
			}
			if (!validateType(type))
			{
				return null;
			}
			if (!validateData(data))
			{
				return null;
			}

			Server.this.requestHandlersLock.readLock().lock();
			LinkedList<RequestHandler> requestHandlers = Server.this.getHandlers(queueName).collect(LinkedList::new, LinkedList::add, LinkedList::addAll);
			Server.this.requestHandlersLock.readLock().unlock();

			RequestHandler.Request request = new RequestHandler.Request(timestamp, type, data);
			CompletableFuture<String> responseCompletableFuture = new CompletableFuture<>();

			new Thread(() ->
			{
				for (RequestHandler requestHandler : requestHandlers)
				{
					CompletableFuture<String> completableFuture = requestHandler.request(request);
					if (completableFuture != null)
					{
						String response = completableFuture.join();
						if (response != null)
						{
							responseCompletableFuture.complete(response);
							break;
						}
					}
				}
				responseCompletableFuture.complete(null);
			}).start();

			return responseCompletableFuture;
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