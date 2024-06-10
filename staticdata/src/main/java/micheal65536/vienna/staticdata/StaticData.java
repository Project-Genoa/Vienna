package micheal65536.vienna.staticdata;

import org.jetbrains.annotations.NotNull;

import java.io.File;

public final class StaticData
{
	public final Catalog catalog;
	public final Levels levels;
	public final TappablesConfig tappablesConfig;
	public final EncountersConfig encountersConfig;

	public StaticData(@NotNull File dir) throws StaticDataException
	{
		this.catalog = new Catalog(new File(dir, "catalog"));
		this.levels = new Levels(new File(dir, "levels"));
		this.tappablesConfig = new TappablesConfig(new File(dir, "tappables"));
		this.encountersConfig = new EncountersConfig(new File(dir, "encounters"));
	}
}