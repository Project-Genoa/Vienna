package micheal65536.vienna.staticdata;

import org.jetbrains.annotations.NotNull;

import java.io.File;

public final class StaticData
{
	public final Catalog catalog;

	public StaticData(@NotNull File dir) throws StaticDataException
	{
		this.catalog = new Catalog(new File(dir, "catalog"));
	}
}