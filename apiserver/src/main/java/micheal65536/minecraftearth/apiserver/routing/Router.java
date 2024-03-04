package micheal65536.minecraftearth.apiserver.routing;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Locale;
import java.util.stream.Collectors;

public class Router
{
	private final LinkedList<RegisteredHandler> handlers = new LinkedList<>();
	private final LinkedList<RegisteredFilter> filters = new LinkedList<>();
	private final LinkedList<RegisteredSubRouter> subRouters = new LinkedList<>();

	public Router()
	{
		// empty
	}

	@NotNull
	public Router addHandler(@NotNull Route route, @NotNull Handler handler)
	{
		this.handlers.add(new RegisteredHandler(route, handler));
		return this;
	}

	@NotNull
	public Router addFilter(@NotNull Route route, @NotNull Filter filter)
	{
		this.filters.add(new RegisteredFilter(route, filter));
		return this;
	}

	@NotNull
	public Router addSubRouter(@NotNull String pattern, int strip, @NotNull Router router)
	{
		if (!pattern.startsWith("/") || (pattern.length() > 1 && pattern.endsWith("/")))
		{
			throw new IllegalArgumentException();
		}
		Path path = new Path(pattern);
		if (Arrays.stream(path.parts).anyMatch(part -> part.contains("$")))
		{
			throw new IllegalArgumentException();
		}
		if (Arrays.stream(path.parts).anyMatch(part -> part.contains("?") && !part.equals("?")))
		{
			throw new IllegalArgumentException();
		}
		if (path.parts.length > 0 && Arrays.stream(path.parts, 0, path.parts.length - 1).anyMatch(part -> part.contains("*")))
		{
			throw new IllegalArgumentException();
		}
		if (path.parts.length > 0 && path.parts[path.parts.length - 1].contains("*") && !path.parts[path.parts.length - 1].equals("*"))
		{
			throw new IllegalArgumentException();
		}

		if (strip < 0 || strip > path.parts.length)
		{
			throw new IllegalArgumentException();
		}

		this.subRouters.add(new RegisteredSubRouter(path, strip, router));

		return this;
	}

	boolean handleRequest(@NotNull HttpServletRequest httpServletRequest, @NotNull HttpServletResponse httpServletResponse)
	{
		return this.handleRequest(httpServletRequest, httpServletResponse, null, null, 0);
	}

	private boolean handleRequest(@NotNull HttpServletRequest httpServletRequest, @NotNull HttpServletResponse httpServletResponse, @Nullable String requestBody, @Nullable HashMap<String, Object> contextData, int pathStrip)
	{
		try
		{
			if (requestBody == null)
			{
				requestBody = httpServletRequest.getReader().lines().collect(Collectors.joining("\n"));
			}

			Path path = new Path(httpServletRequest.getPathInfo());
			if (pathStrip > 0)
			{
				path = path.strip(pathStrip);
			}

			for (RegisteredHandler handler : this.handlers)
			{
				Request handlerRequest = matchRouteAndMakeRequest(httpServletRequest, handler.route, path, requestBody);
				if (handlerRequest != null)
				{
					HashMap<String, Object> collectedContextData = contextData != null ? contextData : new HashMap<>();
					for (RegisteredFilter filter : this.filters)
					{
						Request filterRequest = matchRouteAndMakeRequest(httpServletRequest, filter.route, path, requestBody);
						if (filterRequest != null)
						{
							filterRequest.contextData.putAll(collectedContextData);
							Response response = filter.filter.filter(filterRequest);
							if (response != null)
							{
								setResponse(httpServletResponse, response);
								return true;
							}
							collectedContextData = filterRequest.contextData;
						}
					}

					handlerRequest.contextData.putAll(collectedContextData);
					Response response = handler.handler.handle(handlerRequest);
					setResponse(httpServletResponse, response);
					return true;
				}
			}

			for (RegisteredSubRouter subRouter : this.subRouters)
			{
				if (matchPathPattern(path, subRouter.pattern) != null)
				{
					HashMap<String, Object> collectedContextData = contextData != null ? contextData : new HashMap<>();
					for (RegisteredFilter filter : this.filters)
					{
						Request filterRequest = matchRouteAndMakeRequest(httpServletRequest, filter.route, path, requestBody);
						if (filterRequest != null)
						{
							filterRequest.contextData.putAll(collectedContextData);
							Response response = filter.filter.filter(filterRequest);
							if (response != null)
							{
								setResponse(httpServletResponse, response);
								return true;
							}
							collectedContextData = filterRequest.contextData;
						}
					}

					if (subRouter.router.handleRequest(httpServletRequest, httpServletResponse, requestBody, collectedContextData, pathStrip + subRouter.pathStrip))
					{
						return true;
					}
				}
			}

			return false;
		}
		catch (Request.BadRequestException exception)
		{
			LogManager.getLogger().info("Bad request", exception);
			setResponse(httpServletResponse, Response.badRequest());
			return true;
		}
		catch (Exception exception)
		{
			LogManager.getLogger().error("Exception occurred while handling request", exception);
			setResponse(httpServletResponse, Response.serverError());
			return true;
		}
	}

	@Nullable
	private static Request matchRouteAndMakeRequest(@NotNull HttpServletRequest httpServletRequest, @NotNull Route route, @NotNull Path path, @NotNull String requestBody)
	{
		Request.Method method = switch (httpServletRequest.getMethod())
		{
			case "GET" -> Request.Method.GET;
			case "POST" -> Request.Method.POST;
			case "PUT" -> Request.Method.PUT;
			default -> null;
		};
		if (method == null)
		{
			return null;
		}
		if (method != route.method)
		{
			return null;
		}

		String[] pathParams = matchPathPattern(path, route.pattern);
		if (pathParams == null)
		{
			return null;
		}

		Request request = new Request(path, method, requestBody);

		httpServletRequest.getParameterNames().asIterator().forEachRemaining(name ->
		{
			String paramName = route.queryParameters.getOrDefault(name, null);
			if (paramName != null)
			{
				request.parameters.put(paramName, httpServletRequest.getParameter(name));
			}
		});
		httpServletRequest.getHeaderNames().asIterator().forEachRemaining(name ->
		{
			String paramName = route.headerParameters.getOrDefault(name, null);
			if (paramName != null)
			{
				request.parameters.put(paramName, httpServletRequest.getHeader(name));
			}
		});
		for (int index = 0; index < pathParams.length; index++)
		{
			String paramName = route.pathParameters.getOrDefault(index, null);
			if (paramName != null)
			{
				request.parameters.put(paramName, pathParams[index]);
			}
		}

		return request;
	}

	private static void setResponse(@NotNull HttpServletResponse httpServletResponse, @NotNull Response response)
	{
		httpServletResponse.setStatus(response.statusCode);
		httpServletResponse.setContentType(response.contentType);
		httpServletResponse.setCharacterEncoding("utf-8");
		response.extraHeaders.forEach(httpServletResponse::setHeader);
		try (ServletOutputStream outputStream = httpServletResponse.getOutputStream())
		{
			outputStream.write(response.body);
		}
		catch (IOException exception)
		{
			LogManager.getLogger().warn("IOException while writing HTTP response body", exception);
		}
	}

	@Nullable
	private static String[] matchPathPattern(@NotNull Path path, @NotNull Path pattern)
	{
		if (pattern.parts.length == 0 || !pattern.parts[pattern.parts.length - 1].equals("*"))
		{
			if (path.parts.length != pattern.parts.length)
			{
				return null;
			}
		}
		else
		{
			if (path.parts.length < pattern.parts.length)
			{
				return null;
			}
		}
		LinkedList<String> params = new LinkedList<>();
		for (int index = 0; index < pattern.parts.length; index++)
		{
			if (pattern.parts[index].equals("$"))
			{
				params.add(path.parts[index]);
			}
			else if (pattern.parts[index].equals("?"))
			{
				continue;
			}
			else if (pattern.parts[index].equals("*"))
			{
				if (index != pattern.parts.length - 1)
				{
					throw new AssertionError();
				}
				break;
			}
			else
			{
				if (!path.parts[index].equals(pattern.parts[index]))
				{
					return null;
				}
			}
		}
		return params.toArray(String[]::new);
	}

	public static final class Route
	{
		private final Request.Method method;
		private final Path pattern;
		private final HashMap<String, String> queryParameters = new HashMap<>();
		private final HashMap<Integer, String> pathParameters = new HashMap<>();
		private final HashMap<String, String> headerParameters = new HashMap<>();

		private Route(Request.Method method, Path pattern)
		{
			this.method = method;
			this.pattern = pattern;
		}

		public static final class Builder
		{
			private final Request.Method method;
			private final Path pattern;
			private final HashMap<String, String> queryParameters = new HashMap<>();
			private final HashMap<Integer, String> pathParameters = new HashMap<>();
			private final HashMap<String, String> headerParameters = new HashMap<>();
			private final HashSet<String> paramNames = new HashSet<>();

			public Builder(@NotNull Request.Method method, @NotNull String pattern)
			{
				this.method = method;

				if (!pattern.startsWith("/") || (pattern.length() > 1 && pattern.endsWith("/")))
				{
					throw new IllegalArgumentException();
				}
				Path path = new Path(pattern);
				int pathParamIndex = 0;
				if (Arrays.stream(path.parts).anyMatch(part -> part.contains("?") && !part.equals("?")))
				{
					throw new IllegalArgumentException();
				}
				if (path.parts.length > 0 && Arrays.stream(path.parts, 0, path.parts.length - 1).anyMatch(part -> part.contains("*")))
				{
					throw new IllegalArgumentException();
				}
				if (path.parts.length > 0 && path.parts[path.parts.length - 1].contains("*") && !path.parts[path.parts.length - 1].equals("*"))
				{
					throw new IllegalArgumentException();
				}
				for (String part : path.parts)
				{
					boolean isVariable = part.startsWith("$");
					if (isVariable)
					{
						part = part.substring(1);
					}
					if (part.contains("$"))
					{
						throw new IllegalArgumentException();
					}
					if (isVariable && (part.contains("*") || part.contains("?")))
					{
						throw new IllegalArgumentException();
					}
					if (isVariable)
					{
						this.checkDuplicateParamName(part);
						this.pathParameters.put(pathParamIndex, part);
						pathParamIndex++;
					}
				}
				this.pattern = new Path(Arrays.stream(path.parts).map(part -> part.startsWith("$") ? "$" : part).toArray(String[]::new));
			}

			@NotNull
			public Builder addQueryParameter(@NotNull String name)
			{
				this.addQueryParameter(name, name);
				return this;
			}

			@NotNull
			public Builder addQueryParameter(@NotNull String paramName, @NotNull String queryName)
			{
				this.checkDuplicateParamName(paramName);
				this.queryParameters.put(queryName, paramName);
				return this;
			}

			@NotNull
			public Builder addHeaderParameter(@NotNull String name)
			{
				this.addHeaderParameter(name, name);
				return this;
			}

			@NotNull
			public Builder addHeaderParameter(@NotNull String paramName, @NotNull String headerName)
			{
				this.checkDuplicateParamName(paramName);
				this.headerParameters.put(headerName.toLowerCase(Locale.ROOT), paramName);
				return this;
			}

			private void checkDuplicateParamName(@NotNull String name)
			{
				if (!this.paramNames.add(name))
				{
					throw new IllegalArgumentException("A parameter with that name already exists");
				}
			}

			@NotNull
			public Route build()
			{
				Route route = new Route(this.method, this.pattern);
				route.queryParameters.putAll(this.queryParameters);
				route.pathParameters.putAll(this.pathParameters);
				route.headerParameters.putAll(this.headerParameters);
				return route;
			}
		}
	}

	private static final class RegisteredHandler
	{
		public final Route route;
		public final Handler handler;

		public RegisteredHandler(Route route, Handler handler)
		{
			this.route = route;
			this.handler = handler;
		}
	}

	private static final class RegisteredFilter
	{
		public final Route route;
		public final Filter filter;

		public RegisteredFilter(Route route, Filter filter)
		{
			this.route = route;
			this.filter = filter;
		}
	}

	private static final class RegisteredSubRouter
	{
		public final Path pattern;
		public final int pathStrip;
		public final Router router;

		public RegisteredSubRouter(Path pattern, int pathStrip, Router router)
		{
			this.pattern = pattern;
			this.pathStrip = pathStrip;
			this.router = router;
		}
	}
}