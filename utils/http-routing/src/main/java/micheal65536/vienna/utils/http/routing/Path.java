package micheal65536.vienna.utils.http.routing;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public final class Path
{
	final String[] parts;

	public Path(@NotNull String path)
	{
		this.parts = Arrays.stream(path.split("/")).filter(part -> !part.isEmpty()).toArray(String[]::new);
	}

	Path(String[] parts)
	{
		this.parts = parts;
	}

	@Override
	@NotNull
	public String toString()
	{
		return String.join("/", this.parts);
	}

	public String[] getParts()
	{
		return Arrays.copyOf(this.parts, this.parts.length);
	}

	@NotNull
	public Path strip(int count)
	{
		if (count < 0 || count > this.parts.length)
		{
			throw new IllegalArgumentException();
		}
		return new Path(Arrays.copyOfRange(this.parts, count, this.parts.length));
	}
}