package micheal65536.minecraftearth.apiserver.routing;

import org.jetbrains.annotations.NotNull;

public interface Handler
{
	@NotNull
	Response handle(@NotNull Request request) throws Request.BadRequestException, ServerErrorException;
}