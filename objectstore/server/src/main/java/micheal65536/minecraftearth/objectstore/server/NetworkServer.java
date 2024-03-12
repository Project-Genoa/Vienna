package micheal65536.minecraftearth.objectstore.server;

import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

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

		public Connection(@NotNull Socket socket) throws IOException
		{
			this.socket = socket;
			this.outputStream = this.socket.getOutputStream();
		}

		public void run()
		{
			try (InputStream inputStream = this.socket.getInputStream())
			{
				byte[] readBuffer = new byte[65536];
				ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(128);
				boolean close = false;
				String lastCommand = null;
				int binaryReadLength = 0;
				while (!close)
				{
					int readLength = inputStream.read(readBuffer);
					if (readLength > 0)
					{
						int startOffset = 0;
						while (startOffset < readLength && !close)
						{
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
									if (!this.handleBinaryData(lastCommand, byteArrayOutputStream.toByteArray()))
									{
										close = true;
										break;
									}
									lastCommand = null;
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
										lastCommand = byteArrayOutputStream.toString(StandardCharsets.US_ASCII);
										binaryReadLength = this.handleCommand(lastCommand);
										if (binaryReadLength == -1)
										{
											close = true;
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
			LogManager.getLogger().info("Connection closed");
		}

		private void sendMessage(@NotNull String message)
		{
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
		}

		private void sendData(byte[] data)
		{
			try
			{
				this.socket.getOutputStream().write(data);
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
		}

		private int handleCommand(@NotNull String command)
		{
			String[] parts = command.split(" ", 2);
			if (parts.length != 2)
			{
				return -1;
			}

			switch (parts[0])
			{
				case "STORE" ->
				{
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
				case "GET" ->
				{
					String id = parts[1];
					if (!validateObjectId(id))
					{
						return -1;
					}
					byte[] data = NetworkServer.this.server.load(id);
					if (data != null)
					{
						this.sendMessage("OK " + Integer.toString(data.length));
						this.sendData(data);
					}
					else
					{
						this.sendMessage("ERR");
					}
					return 0;
				}
				case "DEL" ->
				{
					String id = parts[1];
					if (!validateObjectId(id))
					{
						return -1;
					}
					if (NetworkServer.this.server.delete(id))
					{
						this.sendMessage("OK");
					}
					else
					{
						this.sendMessage("ERR");
					}
					return 0;
				}
				default ->
				{
					return -1;
				}
			}
		}

		private boolean handleBinaryData(@NotNull String command, byte[] data)
		{
			String[] parts = command.split(" ", 2);
			if (parts.length != 2)
			{
				throw new AssertionError();
			}

			switch (parts[0])
			{
				case "STORE" ->
				{
					String id = NetworkServer.this.server.store(data);
					if (id != null)
					{
						this.sendMessage("OK " + id);
					}
					else
					{
						this.sendMessage("ERR");
					}
					return true;
				}
				default ->
				{
					throw new AssertionError();
				}
			}
		}
	}

	private static boolean validateObjectId(String id)
	{
		if (!id.matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"))
		{
			return false;
		}
		return true;
	}
}