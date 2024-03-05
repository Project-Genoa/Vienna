package micheal65536.minecraftearth.apiserver.routing;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface Filter
{
	@Nullable
	Response filter(@NotNull Request request) throws Request.BadRequestException, ServerErrorException;
}