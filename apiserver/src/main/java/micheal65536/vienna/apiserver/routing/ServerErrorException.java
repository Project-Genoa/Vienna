package micheal65536.vienna.apiserver.routing;

public final class ServerErrorException extends Exception
{
	public ServerErrorException(String message, Throwable cause)
	{
		super(message, cause);
	}

	public ServerErrorException(Throwable cause)
	{
		super(cause);
	}
}