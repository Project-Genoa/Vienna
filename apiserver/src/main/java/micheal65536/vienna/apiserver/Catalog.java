package micheal65536.vienna.apiserver;

import com.google.gson.Gson;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;

import micheal65536.vienna.apiserver.types.catalog.ItemsCatalog;
import micheal65536.vienna.apiserver.types.catalog.JournalCatalog;
import micheal65536.vienna.apiserver.types.catalog.NFCBoost;
import micheal65536.vienna.apiserver.types.catalog.RecipesCatalog;

import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.LinkedList;

public final class Catalog
{
	public final ItemsCatalog itemsCatalog;
	public final RecipesCatalog recipesCatalog;
	public final JournalCatalog journalCatalog;
	public final NFCBoost[] nfcBoostsCatalog;

	public Catalog()
	{
		// TODO: use own data format rather than using the Project Earth files
		try
		{
			LogManager.getLogger().info("Loading catalog data");
			File catalogDataDir = new File("data", "catalog");
			LinkedList<ItemsCatalog.Item> items = new LinkedList<>();
			HashMap<String, ItemsCatalog.EfficiencyCategory> efficiencyCategories = new HashMap<>();
			for (File file : new File(catalogDataDir, "items").listFiles())
			{
				items.add(new Gson().fromJson(new FileReader(file), ItemsCatalog.Item.class));
			}
			for (File file : new File(catalogDataDir, "efficiency_categories").listFiles())
			{
				String name = file.getName().replace(".json", "");
				ItemsCatalog.EfficiencyCategory.EfficiencyMap efficiencyMap = new Gson().fromJson(new FileReader(file), ItemsCatalog.EfficiencyCategory.EfficiencyMap.class);
				efficiencyCategories.put(name, new ItemsCatalog.EfficiencyCategory(efficiencyMap));
			}
			this.itemsCatalog = new ItemsCatalog(items.toArray(ItemsCatalog.Item[]::new), efficiencyCategories);
			record RecipesCatalogFile(@NotNull RecipesCatalog result)
			{
			}
			this.recipesCatalog = new Gson().fromJson(new FileReader(new File(catalogDataDir, "recipes.json")), RecipesCatalogFile.class).result;
			record JournalCatalogFile(@NotNull JournalCatalog result)
			{
			}
			this.journalCatalog = new Gson().fromJson(new FileReader(new File(catalogDataDir, "journalCatalog.json")), JournalCatalogFile.class).result;
			record NFCBoostsCatalogFile(@NotNull NFCBoost[] result)
			{
			}
			this.nfcBoostsCatalog = new Gson().fromJson(new FileReader(new File(catalogDataDir, "productCatalog.json")), NFCBoostsCatalogFile.class).result;
		}
		catch (Exception exception)
		{
			LogManager.getLogger().fatal("Failed to load catalog data", exception);
			System.exit(1);
			throw new AssertionError();
		}
	}
}