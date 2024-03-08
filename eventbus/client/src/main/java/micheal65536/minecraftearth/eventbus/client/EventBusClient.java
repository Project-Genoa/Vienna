package micheal65536.minecraftearth.eventbus.client;

import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class EventBusClient
{
	@NotNull
	public static EventBusClient create(@NotNull String connectionString) throws ConnectException
	{
		String[] parts = connectionString.split(":", 2);
		String host = parts[0];
		int port;
		try
		{
			port = parts.length > 1 ? Integer.parseInt(parts[1]) : 5532;
		}
		catch (NumberFormatException exception)
		{
			throw new IllegalArgumentException("Invalid port number \"%s\"".formatted(parts[1]));
		}
		if (port <= 0 || port > 65535)
		{
			throw new IllegalArgumentException("Port number out of range");
		}

		Socket socket;
		try
		{
			socket = new Socket(host, port);
		}
		catch (IOException exception)
		{
			throw new ConnectException("Could not create socket", exception);
		}

		return new EventBusClient(socket);
	}

	public static final class ConnectException extends EventBusClientException
	{
		private ConnectException(String message)
		{
			super(message);
		}

		private ConnectException(String message, Throwable cause)
		{
			super(message, cause);
		}
	}

	private final Socket socket;
	private final LinkedBlockingQueue<String> outgoingMessageQueue = new LinkedBlockingQueue<>();
	private final Thread outgoingThread;
	private final Thread incomingThread;
	private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);

	private boolean closed = false;
	private boolean error = false;

	private final HashMap<Integer, Publisher> publishers = new HashMap<>();
	private final HashMap<Integer, Subscriber> subscribers = new HashMap<>();
	private int nextChannelId = 1;

	private EventBusClient(@NotNull Socket socket)
	{
		this.socket = socket;

		this.outgoingThread = new Thread(() ->
		{
			try (OutputStream outputStream = this.socket.getOutputStream())
			{
				for (; ; )
				{
					String message = this.outgoingMessageQueue.take();
					outputStream.write(message.getBytes(StandardCharsets.US_ASCII));
				}
			}
			catch (InterruptedException exception)
			{
				// empty
			}
			catch (IOException exception)
			{
				this.lock.writeLock().lock();
				this.error = true;
				this.lock.writeLock().unlock();
			}
			this.initiateClose();

			this.publishers.forEach((channelId, publisher) ->
			{
				publisher.closed();
			});
			this.publishers.clear();
		});

		this.incomingThread = new Thread(() ->
		{
			try (InputStream inputStream = this.socket.getInputStream())
			{
				byte[] readBuffer = new byte[1024];
				ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(1024);
				for (; ; )
				{
					int readLength = inputStream.read(readBuffer);
					if (readLength > 0)
					{
						int startOffset = 0;
						for (int offset = 0; offset < readLength; offset++)
						{
							if (readBuffer[offset] == '\n')
							{
								byteArrayOutputStream.write(readBuffer, startOffset, offset - startOffset);
								String message = byteArrayOutputStream.toString(StandardCharsets.US_ASCII);
								this.lock.readLock().lock();
								boolean suppress = this.closed || this.error;
								this.lock.readLock().unlock();
								if (!suppress)
								{
									if (!this.dispatchReceivedMessage(message))
									{
										this.lock.writeLock().lock();
										this.error = true;
										this.lock.writeLock().unlock();
										this.initiateClose();
									}
								}
								byteArrayOutputStream = new ByteArrayOutputStream(1024);
								startOffset = offset + 1;
							}
						}
						byteArrayOutputStream.write(readBuffer, startOffset, readLength - startOffset);
					}
					else if (readLength == -1)
					{
						break;
					}
					else
					{
						throw new AssertionError();
					}
				}
			}
			catch (IOException exception)
			{
				this.lock.writeLock().lock();
				this.error = true;
				this.lock.writeLock().unlock();
			}
			this.initiateClose();

			this.subscribers.forEach((channelId, subscriber) ->
			{
				subscriber.error();
			});
			this.subscribers.clear();
		});

		this.outgoingThread.start();
		this.incomingThread.start();
	}

	public void close()
	{
		this.initiateClose();

		for (; ; )
		{
			try
			{
				this.incomingThread.join();
				break;
			}
			catch (InterruptedException exception)
			{
				// empty
			}
		}

		for (; ; )
		{
			try
			{
				this.outgoingThread.join();
				break;
			}
			catch (InterruptedException exception)
			{
				// empty
			}
		}
	}

	private void initiateClose()
	{
		this.lock.writeLock().lock();
		if (!this.error)
		{
			this.closed = true;
		}
		this.lock.writeLock().unlock();

		try
		{
			this.socket.close();
		}
		catch (IOException exception)
		{
			// empty
		}

		this.outgoingThread.interrupt();
	}

	public Publisher addPublisher()
	{
		this.lock.writeLock().lock();
		int channelId = this.getUnusedChannelId();
		Publisher publisher = new Publisher(this, channelId);
		if (this.sendMessage(channelId, "PUB"))
		{
			this.publishers.put(channelId, publisher);
		}
		else
		{
			publisher.closed();
		}
		this.lock.writeLock().unlock();
		return publisher;
	}

	public Subscriber addSubscriber(@NotNull String queueName, @NotNull Subscriber.SubscriberListener listener)
	{
		this.lock.writeLock().lock();
		int channelId = this.getUnusedChannelId();
		Subscriber subscriber = new Subscriber(this, channelId, queueName, listener);
		if (this.sendMessage(channelId, "SUB " + queueName))
		{
			this.subscribers.put(channelId, subscriber);
		}
		else
		{
			subscriber.error();
		}
		this.lock.writeLock().unlock();
		return subscriber;
	}

	void removePublisher(int channelId)
	{
		this.lock.writeLock().lock();
		this.publishers.remove(channelId);
		this.lock.writeLock().unlock();
	}

	void removeSubscriber(int channelId)
	{
		this.lock.writeLock().lock();
		this.subscribers.remove(channelId);
		this.lock.writeLock().unlock();
	}

	private int getUnusedChannelId()
	{
		return this.nextChannelId++;
	}

	private boolean dispatchReceivedMessage(@NotNull String message)
	{
		String[] parts = message.split(" ", 2);
		if (parts.length != 2)
		{
			return false;
		}
		int channelId;
		try
		{
			channelId = Integer.parseInt(parts[0]);
		}
		catch (NumberFormatException exception)
		{
			return false;
		}
		if (channelId <= 0)
		{
			return false;
		}

		Publisher publisher = this.publishers.getOrDefault(channelId, null);
		if (publisher != null)
		{
			return publisher.handleMessage(parts[1]);
		}

		Subscriber subscriber = this.subscribers.getOrDefault(channelId, null);
		if (subscriber != null)
		{
			return subscriber.handleMessage(parts[1]);
		}

		return channelId < this.nextChannelId;
	}

	boolean sendMessage(int channelId, @NotNull String message)
	{
		try
		{
			this.lock.readLock().lock();
			if (this.closed || this.error)
			{
				return false;
			}
		}
		finally
		{
			this.lock.readLock().unlock();
		}

		for (; ; )
		{
			try
			{
				this.outgoingMessageQueue.put(channelId + " " + message + "\n");
				break;
			}
			catch (InterruptedException exception)
			{
				// empty
			}
		}
		return true;
	}
}