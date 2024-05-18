package micheal65536.vienna.eventbus.client;

import org.jetbrains.annotations.NotNull;

import java.util.LinkedList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;

public final class Publisher
{
	private final EventBusClient client;
	private final int channelId;

	private final ReentrantLock lock = new ReentrantLock(true);

	private boolean closed = false;

	private final LinkedList<String> queuedEvents = new LinkedList<>();
	private final LinkedList<CompletableFuture<Boolean>> queuedEventResults = new LinkedList<>();
	private CompletableFuture<Boolean> currentPendingEventResult = null;

	Publisher(@NotNull EventBusClient client, int channelId)
	{
		this.client = client;
		this.channelId = channelId;
	}

	public void close()
	{
		this.client.removePublisher(this.channelId);
		this.client.sendMessage(this.channelId, "CLOSE");
		this.closed();
	}

	public CompletableFuture<Boolean> publish(@NotNull String queueName, @NotNull String type, @NotNull String data)
	{
		if (!validateQueueName(queueName))
		{
			throw new IllegalArgumentException("Queue name contains invalid characters");
		}
		if (!validateType(type))
		{
			throw new IllegalArgumentException("Type contains invalid characters");
		}
		if (!validateData(data))
		{
			throw new IllegalArgumentException("Data contains invalid characters");
		}

		String eventMessage = "SEND " + queueName + ":" + type + ":" + data;

		CompletableFuture<Boolean> completableFuture = new CompletableFuture<>();

		this.lock.lock();
		if (this.closed)
		{
			completableFuture.complete(false);
		}
		else
		{
			this.queuedEvents.add(eventMessage);
			this.queuedEventResults.add(completableFuture);
			if (this.currentPendingEventResult == null)
			{
				this.sendNextEvent();
			}
		}
		this.lock.unlock();

		return completableFuture;
	}

	public void flush()
	{
		this.lock.lock();
		CompletableFuture<Boolean> completableFuture = this.queuedEventResults.isEmpty() ? this.currentPendingEventResult : this.queuedEventResults.getLast();
		this.lock.unlock();

		if (completableFuture != null)
		{
			completableFuture.join();
		}
	}

	boolean handleMessage(@NotNull String message)
	{
		if (message.equals("ACK"))
		{
			try
			{
				this.lock.lock();
				if (this.currentPendingEventResult != null)
				{
					this.currentPendingEventResult.complete(true);
					this.currentPendingEventResult = null;
					if (!this.queuedEvents.isEmpty())
					{
						this.sendNextEvent();
					}
					return true;
				}
				else
				{
					return false;
				}
			}
			finally
			{
				this.lock.unlock();
			}
		}
		else if (message.equals("ERR"))
		{
			this.close();
			return true;
		}
		else
		{
			return false;
		}
	}

	private void sendNextEvent()
	{
		String message = this.queuedEvents.removeFirst();
		this.client.sendMessage(this.channelId, message);
		this.currentPendingEventResult = this.queuedEventResults.removeFirst();
	}

	void closed()
	{
		this.lock.lock();

		this.closed = true;

		if (this.currentPendingEventResult != null)
		{
			this.currentPendingEventResult.complete(false);
			this.currentPendingEventResult = null;
		}
		this.queuedEventResults.forEach(completableFuture -> completableFuture.complete(false));
		this.queuedEventResults.clear();
		this.queuedEvents.clear();

		this.lock.unlock();
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