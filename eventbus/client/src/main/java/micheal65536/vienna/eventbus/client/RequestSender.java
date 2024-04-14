package micheal65536.vienna.eventbus.client;

import org.jetbrains.annotations.NotNull;

import java.util.LinkedList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;

public final class RequestSender
{
	private final EventBusClient client;
	private final int channelId;

	private final ReentrantLock lock = new ReentrantLock(true);

	private boolean closed = false;

	private final LinkedList<String> queuedRequests = new LinkedList<>();
	private final LinkedList<CompletableFuture<String>> queuedRequestResponses = new LinkedList<>();
	private CompletableFuture<String> currentPendingResponse = null;

	RequestSender(@NotNull EventBusClient client, int channelId)
	{
		this.client = client;
		this.channelId = channelId;
	}

	public void close()
	{
		this.client.removeRequestSender(this.channelId);
		this.client.sendMessage(this.channelId, "CLOSE");
		this.closed();
	}

	public CompletableFuture<String> request(@NotNull String queueName, @NotNull String type, @NotNull String data)
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

		String requestMessage = "REQ " + queueName + ":" + type + ":" + data;

		CompletableFuture<String> completableFuture = new CompletableFuture<>();

		this.lock.lock();
		if (this.closed)
		{
			completableFuture.complete(null);
		}
		else
		{
			this.queuedRequests.add(requestMessage);
			this.queuedRequestResponses.add(completableFuture);
			if (this.currentPendingResponse == null)
			{
				this.sendNextRequest();
			}
		}
		this.lock.unlock();

		return completableFuture;
	}

	boolean handleMessage(@NotNull String message)
	{
		if (message.equals("ERR"))
		{
			this.close();
			return true;
		}
		else if (message.equals("ACK"))
		{
			return true;
		}
		else
		{
			String response;

			String[] parts = message.split(" ", 2);
			if (parts[0].equals("NREP"))
			{
				if (parts.length != 1)
				{
					return false;
				}
				response = null;
			}
			else if (parts[0].equals("REP"))
			{
				if (parts.length != 2)
				{
					return false;
				}
				response = parts[1];
			}
			else
			{
				return false;
			}

			try
			{
				this.lock.lock();
				if (this.currentPendingResponse != null)
				{
					this.currentPendingResponse.complete(response);
					this.currentPendingResponse = null;
					if (!this.queuedRequests.isEmpty())
					{
						this.sendNextRequest();
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
	}

	private void sendNextRequest()
	{
		String message = this.queuedRequests.removeFirst();
		this.client.sendMessage(this.channelId, message);
		this.currentPendingResponse = this.queuedRequestResponses.removeFirst();
	}

	void closed()
	{
		this.lock.lock();

		this.closed = true;

		if (this.currentPendingResponse != null)
		{
			this.currentPendingResponse.complete(null);
			this.currentPendingResponse = null;
		}
		this.queuedRequestResponses.forEach(completableFuture -> completableFuture.complete(null));
		this.queuedRequestResponses.clear();
		this.queuedRequests.clear();

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