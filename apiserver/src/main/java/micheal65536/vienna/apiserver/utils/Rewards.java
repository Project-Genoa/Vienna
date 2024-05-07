package micheal65536.vienna.apiserver.utils;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import micheal65536.vienna.apiserver.Catalog;
import micheal65536.vienna.apiserver.types.catalog.ItemsCatalog;
import micheal65536.vienna.db.EarthDB;
import micheal65536.vienna.db.model.common.NonStackableItemInstance;
import micheal65536.vienna.db.model.player.Inventory;
import micheal65536.vienna.db.model.player.Journal;
import micheal65536.vienna.db.model.player.Profile;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

public final class Rewards
{
	private int rubies;
	private int experiencePoints;
	@Nullable
	private Integer level;
	private final HashMap<String, Integer> items = new HashMap<>();
	private final LinkedHashSet<String> buildplates = new LinkedHashSet<>();
	private final LinkedHashSet<String> challenges = new LinkedHashSet<>();

	public Rewards()
	{
		// empty
	}

	@NotNull
	public Rewards setLevel(int level)
	{
		this.level = level;
		return this;
	}

	@NotNull
	public Rewards addItem(@NotNull String id, int count)
	{
		this.items.put(id, this.items.getOrDefault(id, 0) + count);
		return this;
	}

	@NotNull
	public Rewards addBuildplate(@NotNull String id)
	{
		this.buildplates.add(id);
		return this;
	}

	@NotNull
	public Rewards addChallenge(@NotNull String id)
	{
		this.challenges.add(id);
		return this;
	}

	@NotNull
	public Rewards addRubies(int rubies)
	{
		this.rubies += rubies;
		return this;
	}

	@NotNull
	public Rewards addExperiencePoints(int experiencePoints)
	{
		this.experiencePoints += experiencePoints;
		return this;
	}

	@NotNull
	public EarthDB.Query toRedeemQuery(@NotNull String playerId, long currentTime, @NotNull Catalog catalog)
	{
		EarthDB.Query getQuery = new EarthDB.Query(true);
		if (this.rubies > 0 || this.experiencePoints > 0)
		{
			getQuery.get("profile", playerId, Profile.class);
		}
		if (!this.items.isEmpty())
		{
			getQuery.get("inventory", playerId, Inventory.class);
			getQuery.get("journal", playerId, Journal.class);
		}
		if (!this.buildplates.isEmpty())
		{
			// TODO
		}
		if (!this.challenges.isEmpty())
		{
			// TODO
		}

		EarthDB.Query updateQuery = new EarthDB.Query(true);
		getQuery.then(results ->
		{
			if (this.rubies > 0 || this.experiencePoints > 0)
			{
				Profile profile = (Profile) results.get("profile").value();
				if (this.rubies > 0)
				{
					profile.rubies.earned += this.rubies;
				}
				if (this.experiencePoints > 0)
				{
					profile.experience += this.experiencePoints;
				}
				updateQuery.update("profile", playerId, profile);

				if (this.experiencePoints > 0)
				{
					updateQuery.then(LevelUtils.checkAndHandlePlayerLevelUp(playerId, currentTime, catalog));
				}
			}

			if (!this.items.isEmpty())
			{
				Inventory inventory = (Inventory) results.get("inventory").value();
				Journal journal = (Journal) results.get("journal").value();
				for (Map.Entry<String, Integer> entry : this.items.entrySet())
				{
					String id = entry.getKey();
					int quantity = entry.getValue();
					if (quantity > 0)
					{
						ItemsCatalog.Item item = Arrays.stream(catalog.itemsCatalog.items()).filter(item1 -> item1.id().equals(id)).findFirst().orElseThrow();
						if (item.stacks())
						{
							inventory.addItems(id, quantity);
						}
						else
						{
							inventory.addItems(id, IntStream.range(0, quantity).mapToObj(index -> new NonStackableItemInstance(UUID.randomUUID().toString(), 0)).toArray(NonStackableItemInstance[]::new));
						}
						journal.touchItem(id, currentTime);
						journal.addCollectedItem(id, quantity);
					}
				}
				updateQuery.update("inventory", playerId, inventory);
				updateQuery.update("journal", playerId, journal);
			}

			if (!this.buildplates.isEmpty())
			{
				// TODO
			}

			if (!this.challenges.isEmpty())
			{
				// TODO
			}

			return updateQuery;
		});
		getQuery.then(new EarthDB.Query(false).extra("rewards", this));

		return getQuery;
	}

	@NotNull
	public micheal65536.vienna.apiserver.types.common.Rewards toApiResponse()
	{
		return new micheal65536.vienna.apiserver.types.common.Rewards(
				this.rubies,
				this.experiencePoints,
				this.level,
				this.items.entrySet().stream().map(entry -> new micheal65536.vienna.apiserver.types.common.Rewards.Item(entry.getKey(), entry.getValue())).toArray(micheal65536.vienna.apiserver.types.common.Rewards.Item[]::new),
				this.buildplates.stream().map(buildplate -> new micheal65536.vienna.apiserver.types.common.Rewards.Buildplate(buildplate)).toArray(micheal65536.vienna.apiserver.types.common.Rewards.Buildplate[]::new),
				this.challenges.stream().map(challenge -> new micheal65536.vienna.apiserver.types.common.Rewards.Challenge(challenge)).toArray(micheal65536.vienna.apiserver.types.common.Rewards.Challenge[]::new),
				new String[0],
				new micheal65536.vienna.apiserver.types.common.Rewards.UtilityBlock[0]
		);
	}

	@NotNull
	public static Rewards fromDBRewardsModel(@NotNull micheal65536.vienna.db.model.common.Rewards rewardsModel)
	{
		Rewards rewards = new Rewards();
		rewards.addRubies(rewardsModel.rubies());
		rewards.addExperiencePoints(rewardsModel.experiencePoints());
		if (rewardsModel.level() != null)
		{
			rewards.setLevel(rewardsModel.level());
		}
		rewardsModel.items().forEach(rewards::addItem);
		Arrays.stream(rewardsModel.buildplates()).forEach(rewards::addBuildplate);
		Arrays.stream(rewardsModel.challenges()).forEach(rewards::addChallenge);
		return rewards;
	}

	@NotNull
	public micheal65536.vienna.db.model.common.Rewards toDBRewardsModel()
	{
		return new micheal65536.vienna.db.model.common.Rewards(
				this.rubies,
				this.experiencePoints,
				this.level,
				new HashMap<>(this.items),
				this.buildplates.toArray(String[]::new),
				this.challenges.toArray(String[]::new)
		);
	}
}