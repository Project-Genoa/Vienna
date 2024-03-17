package micheal65536.vienna.apiserver.routing;

import org.jetbrains.annotations.NotNull;

public interface Handler
{
	@NotNull
	Response handle(@NotNull Request request) throws Request.BadRequestException, ServerErrorException;
}