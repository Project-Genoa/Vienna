package micheal65536.vienna.utils.http.routing;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface Filter
{
	@Nullable
	Response filter(@NotNull Request request) throws Request.BadRequestException, ServerErrorException;
}