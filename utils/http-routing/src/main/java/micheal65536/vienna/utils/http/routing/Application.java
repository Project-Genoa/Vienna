package micheal65536.vienna.utils.http.routing;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public final class Application
{
	public final Router router = new Router();

	public Application()
	{
		// empty
	}

	public void handleRequest(@NotNull HttpServletRequest httpServletRequest, @NotNull HttpServletResponse httpServletResponse)
	{
		LogManager.getLogger().info("Request: {} {}", httpServletRequest.getMethod(), httpServletRequest.getRequestURI() + (httpServletRequest.getQueryString() != null ? "?" + httpServletRequest.getQueryString() : ""));
		if (!this.router.handleRequest(httpServletRequest, httpServletResponse))
		{
			httpServletResponse.setStatus(404);
			try
			{
				httpServletResponse.getOutputStream().close();
			}
			catch (IOException exception)
			{
				LogManager.getLogger().warn("IOException while sending response for unmatched request", exception);
			}
		}
	}
}