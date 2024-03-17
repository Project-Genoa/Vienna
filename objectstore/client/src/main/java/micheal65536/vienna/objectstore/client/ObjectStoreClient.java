package micheal65536.vienna.objectstore.client;

import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.ReentrantLock;

public class ObjectStoreClient
{
	@NotNull
	public static ObjectStoreClient create(@NotNull String connectionString) throws ConnectException
	{
		String[] parts = connectionString.split(":", 2);
		String host = parts[0];
		int port;
		try
		{
			port = parts.length > 1 ? Integer.parseInt(parts[1]) : 5396;
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

		return new ObjectStoreClient(socket);
	}

	public static final class ConnectException extends ObjectStoreClientException
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
	private final LinkedBlockingQueue<Object> outgoingMessageQueue = new LinkedBlockingQueue<>();
	private final Thread outgoingThread;
	private final Thread incomingThread;
	private final ReentrantLock lock = new ReentrantLock(true);

	private boolean closed = false;

	private Command currentCommand = null;
	private final LinkedList<Command> queuedCommands = new LinkedList<>();

	private ObjectStoreClient(@NotNull Socket socket)
	{
		this.socket = socket;

		this.outgoingThread = new Thread(() ->
		{
			try (OutputStream outputStream = this.socket.getOutputStream())
			{
				for (; ; )
				{
					Object message = this.outgoingMessageQueue.take();
					if (message instanceof String command)
					{
						outputStream.write(command.getBytes(StandardCharsets.US_ASCII));
					}
					else if (message instanceof byte[] data)
					{
						outputStream.write(data);
					}
					else
					{
						throw new AssertionError();
					}
				}
			}
			catch (InterruptedException exception)
			{
				// empty
			}
			catch (IOException exception)
			{
				this.lock.lock();
				this.closed = true;
				this.lock.unlock();
			}
			this.initiateClose();
		});

		this.incomingThread = new Thread(() ->
		{
			try (InputStream inputStream = this.socket.getInputStream())
			{
				byte[] readBuffer = new byte[65536];
				ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(128);
				String lastMessage = null;
				int binaryReadLength = 0;
				for (; ; )
				{
					this.lock.lock();
					if (this.closed)
					{
						this.lock.unlock();
						break;
					}
					this.lock.unlock();

					int readLength = inputStream.read(readBuffer);
					if (readLength > 0)
					{
						int startOffset = 0;
						while (startOffset < readLength)
						{
							this.lock.lock();
							if (this.closed)
							{
								this.lock.unlock();
								break;
							}
							this.lock.unlock();

							if (binaryReadLength > 0)
							{
								if (startOffset + binaryReadLength > readLength)
								{
									byteArrayOutputStream.write(readBuffer, startOffset, readLength - startOffset);
									binaryReadLength -= readLength - startOffset;
									startOffset += readLength - startOffset;
								}
								else
								{
									byteArrayOutputStream.write(readBuffer, startOffset, binaryReadLength);
									if (!this.handleBinaryData(lastMessage, byteArrayOutputStream.toByteArray()))
									{
										this.initiateClose();
										break;
									}
									lastMessage = null;
									byteArrayOutputStream = new ByteArrayOutputStream(128);
									startOffset += binaryReadLength;
									binaryReadLength = 0;
								}
							}
							else
							{
								for (int offset = startOffset; offset < readLength; offset++)
								{
									if (readBuffer[offset] == '\n')
									{
										byteArrayOutputStream.write(readBuffer, startOffset, offset - startOffset);
										lastMessage = byteArrayOutputStream.toString(StandardCharsets.US_ASCII);
										binaryReadLength = this.handleMessage(lastMessage);
										if (binaryReadLength == -1)
										{
											this.initiateClose();
											break;
										}
										byteArrayOutputStream = new ByteArrayOutputStream(128);
										startOffset = offset + 1;
										break;
									}
									else if (offset == readLength - 1)
									{
										byteArrayOutputStream.write(readBuffer, startOffset, readLength - startOffset);
										startOffset = readLength;
									}
								}
							}
						}
					}
					else if (readLength == -1)
					{
						this.initiateClose();
					}
					else
					{
						throw new AssertionError();
					}
				}
			}
			catch (IOException exception)
			{
				this.lock.lock();
				this.closed = true;
				this.lock.unlock();
			}
			this.initiateClose();

			this.lock.lock();
			if (this.currentCommand != null)
			{
				this.currentCommand.completableFuture.complete(this.currentCommand.type == Command.Type.DELETE ? false : null);
				this.currentCommand = null;
			}
			this.queuedCommands.forEach(command -> command.completableFuture.complete(command.type == Command.Type.DELETE ? false : null));
			this.queuedCommands.clear();
			this.lock.unlock();
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
		this.lock.lock();
		this.closed = true;
		this.lock.unlock();

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

	@NotNull
	public CompletableFuture<String> store(byte[] data)
	{
		CompletableFuture<String> completableFuture = new CompletableFuture<>();
		this.queueCommand(new Command(Command.Type.STORE, data, completableFuture));
		return completableFuture;
	}

	@NotNull
	public CompletableFuture<byte[]> get(@NotNull String id)
	{
		CompletableFuture<byte[]> completableFuture = new CompletableFuture<>();
		this.queueCommand(new Command(Command.Type.GET, id, completableFuture));
		return completableFuture;
	}

	@NotNull
	public CompletableFuture<Boolean> delete(@NotNull String id)
	{
		CompletableFuture<Boolean> completableFuture = new CompletableFuture<>();
		this.queueCommand(new Command(Command.Type.DELETE, id, completableFuture));
		return completableFuture;
	}

	private void queueCommand(@NotNull Command command)
	{
		this.lock.lock();
		if (this.closed)
		{
			command.completableFuture.complete(command.type == Command.Type.DELETE ? false : null);
		}
		else
		{
			this.queuedCommands.add(command);
			if (this.currentCommand == null)
			{
				this.sendNextCommand();
			}
		}
		this.lock.unlock();
	}

	private void sendNextCommand()
	{
		this.lock.lock();

		this.currentCommand = null;

		if (this.closed)
		{
			this.lock.unlock();
			return;
		}

		if (!this.queuedCommands.isEmpty())
		{
			this.currentCommand = this.queuedCommands.removeFirst();
			switch (this.currentCommand.type)
			{
				case STORE ->
				{
					this.sendMessage("STORE " + Integer.toString(((byte[]) this.currentCommand.data).length) + "\n");
					this.sendMessage(this.currentCommand.data);
				}
				case GET ->
				{
					this.sendMessage("GET " + ((String) this.currentCommand.data) + "\n");
				}
				case DELETE ->
				{
					this.sendMessage("DEL " + ((String) this.currentCommand.data) + "\n");
				}
			}
		}

		this.lock.unlock();
	}

	private int handleMessage(@NotNull String message)
	{
		try
		{
			this.lock.lock();

			if (this.closed)
			{
				return -1;
			}
			if (this.currentCommand == null)
			{
				return -1;
			}

			String[] parts = message.split(" ", 2);
			switch (this.currentCommand.type)
			{
				case STORE ->
				{
					if (parts[0].equals("OK"))
					{
						if (parts.length != 2)
						{
							return -1;
						}
						this.currentCommand.completableFuture.complete(parts[1]);
						this.sendNextCommand();
						return 0;
					}
					else if (parts[0].equals("ERR"))
					{
						this.currentCommand.completableFuture.complete(null);
						this.sendNextCommand();
						return 0;
					}
					else
					{
						return -1;
					}
				}
				case GET ->
				{
					if (parts[0].equals("OK"))
					{
						if (parts.length != 2)
						{
							return -1;
						}
						try
						{
							int length = Integer.parseInt(parts[1]);
							if (length < 0)
							{
								return -1;
							}
							return length;
						}
						catch (NumberFormatException exception)
						{
							return -1;
						}
					}
					else if (parts[0].equals("ERR"))
					{
						this.currentCommand.completableFuture.complete(null);
						this.sendNextCommand();
						return 0;
					}
					else
					{
						return -1;
					}
				}
				case DELETE ->
				{
					if (parts[0].equals("OK"))
					{
						this.currentCommand.completableFuture.complete(true);
						this.sendNextCommand();
						return 0;
					}
					else if (parts[0].equals("ERR"))
					{
						this.currentCommand.completableFuture.complete(false);
						this.sendNextCommand();
						return 0;
					}
					else
					{
						return -1;
					}
				}
				default ->
				{
					throw new AssertionError();
				}
			}
		}
		finally
		{
			this.lock.unlock();
		}
	}

	private boolean handleBinaryData(@NotNull String message, byte[] data)
	{
		try
		{
			this.lock.lock();

			if (this.closed)
			{
				return false;
			}
			if (this.currentCommand == null)
			{
				throw new AssertionError();
			}

			String[] parts = message.split(" ", 2);
			if (parts.length != 2)
			{
				throw new AssertionError();
			}

			switch (this.currentCommand.type)
			{
				case GET ->
				{
					if (parts[0].equals("OK"))
					{
						this.currentCommand.completableFuture.complete(data);
						this.sendNextCommand();
						return true;
					}
					else
					{
						throw new AssertionError();
					}
				}
				default ->
				{
					throw new AssertionError();
				}
			}
		}
		finally
		{
			this.lock.unlock();
		}
	}

	private void sendMessage(@NotNull Object message)
	{
		try
		{
			this.lock.lock();
			if (this.closed)
			{
				throw new AssertionError();
			}
		}
		finally
		{
			this.lock.unlock();
		}

		for (; ; )
		{
			try
			{
				this.outgoingMessageQueue.put(message);
				break;
			}
			catch (InterruptedException exception)
			{
				// empty
			}
		}
	}

	private static class Command
	{
		public final Type type;
		public final Object data;
		public final CompletableFuture completableFuture;

		public enum Type
		{
			STORE,
			GET,
			DELETE
		}

		public Command(Type type, Object data, CompletableFuture completableFuture)
		{
			this.type = type;
			this.data = data;
			this.completableFuture = completableFuture;
		}
	}
}