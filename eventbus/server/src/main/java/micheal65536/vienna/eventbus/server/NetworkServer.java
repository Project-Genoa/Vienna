package micheal65536.vienna.eventbus.server;

import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;

public class NetworkServer
{
	private final Server server;
	private final ServerSocket serverSocket;

	public NetworkServer(@NotNull Server server, int port) throws IOException
	{
		this.server = server;
		this.serverSocket = new ServerSocket(port);
		LogManager.getLogger().info("Created server on port {}", port);
	}

	public void run()
	{
		for (; ; )
		{
			try
			{
				Socket socket = this.serverSocket.accept();
				LogManager.getLogger().info("Connection from {}", socket.getInetAddress());
				Connection connection = new Connection(socket);
				new Thread(connection::run).start();
			}
			catch (IOException exception)
			{
				LogManager.getLogger().warn("Exception while accepting connection", exception);
			}
		}
	}

	private final class Connection
	{
		private final Socket socket;

		private final OutputStream outputStream;
		private final ReentrantLock sendLock = new ReentrantLock(true);

		private final HashMap<Integer, Channel> channels = new HashMap<>();

		public Connection(@NotNull Socket socket) throws IOException
		{
			this.socket = socket;
			this.outputStream = this.socket.getOutputStream();
		}

		public void run()
		{
			try (InputStream inputStream = this.socket.getInputStream())
			{
				byte[] readBuffer = new byte[1024];
				ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(1024);
				boolean close = false;
				while (!close)
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
								String command = byteArrayOutputStream.toString(StandardCharsets.US_ASCII);
								if (!this.handleCommand(command))
								{
									close = true;
									break;
								}
								byteArrayOutputStream = new ByteArrayOutputStream(1024);
								startOffset = offset + 1;
							}
						}
						byteArrayOutputStream.write(readBuffer, startOffset, readLength - startOffset);
					}
					else if (readLength == -1)
					{
						close = true;
					}
					else
					{
						throw new AssertionError();
					}
				}
			}
			catch (IOException exception)
			{
				LogManager.getLogger().warn("Exception while reading socket", exception);
			}
			this.handleClose();
		}

		private void sendMessage(@NotNull String message)
		{
			this.sendLock.lock();
			try
			{
				this.socket.getOutputStream().write(message.getBytes(StandardCharsets.US_ASCII));
				this.socket.getOutputStream().write('\n');
			}
			catch (IOException exception)
			{
				LogManager.getLogger().warn("Exception while sending", exception);
				try
				{
					this.socket.close();
				}
				catch (IOException exception1)
				{
					LogManager.getLogger().warn("Exception while closing socket", exception1);
				}
			}
			this.sendLock.unlock();
		}

		private boolean handleCommand(@NotNull String command)
		{
			String[] parts = command.split(" ", 2);
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

			Channel channel = this.channels.getOrDefault(channelId, null);
			if (channel != null)
			{
				if (parts[1].equals("CLOSE"))
				{
					channel.handleClose();
					this.channels.remove(channelId);
				}
				else
				{
					channel.handleCommand(parts[1]);
				}
				return true;
			}
			else
			{
				if (parts[1].equals("CLOSE"))
				{
					return true;
				}
				else
				{
					channel = this.handleChannelOpenCommand(channelId, parts[1]);
					if (channel != null)
					{
						this.channels.put(channelId, channel);
						return true;
					}
					else
					{
						return false;
					}
				}
			}
		}

		private void handleClose()
		{
			LogManager.getLogger().info("Connection closed");

			this.channels.values().forEach(Channel::handleClose);
		}

		@Nullable
		private Channel handleChannelOpenCommand(int channelId, @NotNull String command)
		{
			String[] parts = command.split(" ");
			if (parts.length < 1)
			{
				return null;
			}
			switch (parts[0])
			{
				case "PUB" ->
				{
					PublisherChannel publisherChannel = new PublisherChannel(this, channelId);
					if (!publisherChannel.isValid())
					{
						return null;
					}
					return publisherChannel;
				}
				case "SUB" ->
				{
					if (parts.length < 2)
					{
						return null;
					}
					SubscriberChannel subscriberChannel = new SubscriberChannel(this, channelId, parts[1]);
					if (!subscriberChannel.isValid())
					{
						return null;
					}
					return subscriberChannel;
				}
				case "REQ" ->
				{
					RequestSenderChannel requestSenderChannel = new RequestSenderChannel(this, channelId);
					if (!requestSenderChannel.isValid())
					{
						return null;
					}
					return requestSenderChannel;
				}
				case "HND" ->
				{
					if (parts.length < 2)
					{
						return null;
					}
					RequestHandlerChannel requestHandlerChannel = new RequestHandlerChannel(this, channelId, parts[1]);
					if (!requestHandlerChannel.isValid())
					{
						return null;
					}
					return requestHandlerChannel;
				}
				default ->
				{
					return null;
				}
			}
		}
	}

	private abstract class Channel
	{
		private final Connection connection;
		private final int channelId;

		protected Channel(@NotNull Connection connection, int channelId)
		{
			this.connection = connection;
			this.channelId = channelId;
		}

		public abstract boolean isValid();

		public abstract void handleCommand(@NotNull String command);

		public abstract void handleClose();

		protected final void sendMessage(@NotNull String message)
		{
			this.connection.sendMessage(Integer.toString(channelId) + " " + message);
		}
	}

	private final class PublisherChannel extends Channel
	{
		private final Server.Publisher publisher;
		private boolean error = false;

		public PublisherChannel(@NotNull Connection connection, int channelId)
		{
			super(connection, channelId);
			this.publisher = NetworkServer.this.server.addPublisher();
		}

		@Override
		public boolean isValid()
		{
			return true;
		}

		@Override
		public void handleCommand(@NotNull String command)
		{
			if (this.error)
			{
				this.sendMessage("ERR");
				return;
			}

			String[] parts = command.split(" ", 2);
			if (parts[0].equals("SEND"))
			{
				String entryString = parts[1];
				String[] fields = entryString.split(":", 3);
				if (fields.length != 3)
				{
					this.error();
					return;
				}

				long timestamp = System.currentTimeMillis();
				String queueName = fields[0];
				String type = fields[1];
				String data = fields[2];
				if (this.publisher.publish(queueName, timestamp, type, data))
				{
					this.sendMessage("ACK");
				}
				else
				{
					this.error();
				}
			}
			else
			{
				this.error();
			}
		}

		@Override
		public void handleClose()
		{
			this.publisher.remove();
		}

		private void error()
		{
			this.error = true;
			this.sendMessage("ERR");
		}
	}

	private final class SubscriberChannel extends Channel
	{
		private final Server.Subscriber subscriber;

		public SubscriberChannel(@NotNull Connection connection, int channelId, @NotNull String queueName)
		{
			super(connection, channelId);
			this.subscriber = NetworkServer.this.server.addSubscriber(queueName, this::handleMessage);
		}

		public boolean isValid()
		{
			return this.subscriber != null;
		}

		@Override
		public void handleCommand(@NotNull String command)
		{
			// empty
		}

		@Override
		public void handleClose()
		{
			this.subscriber.remove();
		}

		private void handleMessage(@NotNull Server.Subscriber.Message message)
		{
			if (message instanceof Server.Subscriber.EntryMessage entryMessage)
			{
				StringBuilder stringBuilder = new StringBuilder();
				stringBuilder.append(Long.toString(entryMessage.timestamp));
				stringBuilder.append(":");
				stringBuilder.append(entryMessage.type);
				stringBuilder.append(":");
				stringBuilder.append(entryMessage.data);
				this.sendMessage(stringBuilder.toString());
			}
			else if (message instanceof Server.Subscriber.ErrorMessage)
			{
				this.sendMessage("ERR");
			}
		}
	}

	private final class RequestSenderChannel extends Channel
	{
		private final Server.RequestSender requestSender;
		private volatile CompletableFuture<String> currentPendingResponse = null;
		private volatile boolean error = false;

		public RequestSenderChannel(@NotNull Connection connection, int channelId)
		{
			super(connection, channelId);
			this.requestSender = NetworkServer.this.server.addRequestSender();
		}

		@Override
		public boolean isValid()
		{
			return true;
		}

		@Override
		public void handleCommand(@NotNull String command)
		{
			if (this.error)
			{
				this.sendMessage("ERR");
				return;
			}

			if (this.currentPendingResponse != null)
			{
				this.error();
				return;
			}

			String[] parts = command.split(" ", 2);
			if (parts[0].equals("REQ"))
			{
				String entryString = parts[1];
				String[] fields = entryString.split(":", 3);
				if (fields.length != 3)
				{
					this.error();
					return;
				}

				long timestamp = System.currentTimeMillis();
				String queueName = fields[0];
				String type = fields[1];
				String data = fields[2];

				CompletableFuture<String> completableFuture = this.requestSender.request(queueName, timestamp, type, data);
				if (completableFuture != null)
				{
					this.currentPendingResponse = completableFuture;
					this.sendMessage("ACK");
					completableFuture.thenAccept(response ->
					{
						if (this.currentPendingResponse != null)
						{
							if (this.currentPendingResponse != completableFuture)
							{
								throw new AssertionError();
							}
							this.currentPendingResponse = null;
							if (response != null)
							{
								this.sendMessage("REP " + response);
							}
							else
							{
								this.sendMessage("NREP");
							}
						}
					});
				}
				else
				{
					this.error();
				}
			}
			else
			{
				this.error();
			}
		}

		@Override
		public void handleClose()
		{
			this.requestSender.remove();
			this.currentPendingResponse = null;
		}

		private void error()
		{
			this.error = true;
			this.currentPendingResponse = null;
			this.sendMessage("ERR");
		}
	}

	private final class RequestHandlerChannel extends Channel
	{
		private final Server.RequestHandler requestHandler;
		private final HashMap<Integer, CompletableFuture<String>> pendingResponses = new HashMap<>();
		private int nextRequestId = 1;
		private boolean error = false;

		public RequestHandlerChannel(@NotNull Connection connection, int channelId, @NotNull String queueName)
		{
			super(connection, channelId);
			this.requestHandler = NetworkServer.this.server.addRequestHandler(queueName, this::handleRequest, this::handleError);
		}

		public boolean isValid()
		{
			return this.requestHandler != null;
		}

		@Override
		public void handleCommand(@NotNull String command)
		{
			if (this.error)
			{
				this.sendMessage("ERR");
				return;
			}

			String[] parts = command.split(" ", 2);
			if (parts[0].equals("REP"))
			{
				String entryString = parts[1];
				String[] fields = entryString.split(":", 2);
				if (fields.length != 2)
				{
					this.error();
					return;
				}

				int requestId;
				try
				{
					requestId = Integer.parseInt(fields[0]);
				}
				catch (NumberFormatException exception)
				{
					this.error();
					return;
				}
				String data = fields[1];

				CompletableFuture<String> responseCompletableFuture = this.pendingResponses.remove(requestId);
				if (responseCompletableFuture != null)
				{
					responseCompletableFuture.complete(data);
				}
				else
				{
					this.error();
				}
			}
			else if (parts[0].equals("NREP"))
			{
				int requestId;
				try
				{
					requestId = Integer.parseInt(parts[1]);
				}
				catch (NumberFormatException exception)
				{
					this.error();
					return;
				}

				CompletableFuture<String> responseCompletableFuture = this.pendingResponses.remove(requestId);
				if (responseCompletableFuture != null)
				{
					responseCompletableFuture.complete(null);
				}
				else
				{
					this.error();
				}
			}
			else
			{
				this.error();
			}
		}

		@Override
		public void handleClose()
		{
			this.requestHandler.remove();
			this.pendingResponses.values().forEach(completableFuture -> completableFuture.complete(null));
			this.pendingResponses.clear();
		}

		@NotNull
		private CompletableFuture<String> handleRequest(@NotNull Server.RequestHandler.Request request)
		{
			int requestId = this.nextRequestId++;
			CompletableFuture<String> responseCompletableFuture = new CompletableFuture<>();
			this.pendingResponses.put(requestId, responseCompletableFuture);

			StringBuilder stringBuilder = new StringBuilder();
			stringBuilder.append(requestId);
			stringBuilder.append(":");
			stringBuilder.append(Long.toString(request.timestamp));
			stringBuilder.append(":");
			stringBuilder.append(request.type);
			stringBuilder.append(":");
			stringBuilder.append(request.data);
			this.sendMessage(stringBuilder.toString());

			return responseCompletableFuture;
		}

		private void handleError(@NotNull Server.RequestHandler.ErrorMessage errorMessage)
		{
			this.error();
		}

		private void error()
		{
			this.error = true;
			this.pendingResponses.values().forEach(completableFuture -> completableFuture.complete(null));
			this.pendingResponses.clear();
			this.sendMessage("ERR");
		}
	}
}