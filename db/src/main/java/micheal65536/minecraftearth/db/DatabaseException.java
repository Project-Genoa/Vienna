package micheal65536.minecraftearth.db;

public final class DatabaseException extends Exception
{
	DatabaseException(String message)
	{
		super(message);
	}

	DatabaseException(String message, Throwable cause)
	{
		super(message, cause);
	}

	DatabaseException(Throwable cause)
	{
		super(cause);
	}
}