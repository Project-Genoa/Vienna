package micheal65536.vienna.apiserver.utils;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import micheal65536.vienna.apiserver.types.common.Effect;
import micheal65536.vienna.db.model.player.Boosts;
import micheal65536.vienna.staticdata.Catalog;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.UUID;

public final class BoostUtils
{
	@Nullable
	public static String activatePotion(@NotNull Boosts boosts, @NotNull String itemId, long currentTime, @NotNull Catalog.ItemsCatalog itemsCatalog)
	{
		Catalog.ItemsCatalog.Item item = itemsCatalog.getItem(itemId);
		if (item == null)
		{
			throw new IllegalArgumentException();
		}
		Catalog.ItemsCatalog.Item.BoostInfo boostInfo = item.boostInfo();
		if (boostInfo == null || boostInfo.type() != Catalog.ItemsCatalog.Item.BoostInfo.Type.POTION)
		{
			throw new IllegalArgumentException();
		}

		boosts.prune(currentTime);

		String instanceId = UUID.randomUUID().toString();
		long duration = boostInfo.duration() != null ? boostInfo.duration() : Arrays.stream(boostInfo.effects()).mapToLong(Catalog.ItemsCatalog.Item.BoostInfo.Effect::duration).max().orElse(0);

		int newIndex = -1;
		for (int index = 0; index < boosts.activeBoosts.length; index++)
		{
			if (boosts.activeBoosts[index] == null)
			{
				newIndex = index;
				break;
			}
		}
		if (newIndex == -1)
		{
			return null;
		}
		boosts.activeBoosts[newIndex] = new Boosts.ActiveBoost(instanceId, itemId, currentTime, duration);

		return instanceId;
	}

	@NotNull
	public static Catalog.ItemsCatalog.Item.BoostInfo.Effect[] getActiveEffects(@NotNull Boosts boosts, long currentTime, @NotNull Catalog.ItemsCatalog itemsCatalog)
	{
		LinkedList<Catalog.ItemsCatalog.Item.BoostInfo.Effect> effects = new LinkedList<>();
		for (Boosts.ActiveBoost activeBoost : boosts.activeBoosts)
		{
			if (activeBoost == null)
			{
				continue;
			}
			if (activeBoost.startTime() + activeBoost.duration() > currentTime)
			{
				continue;
			}
			Catalog.ItemsCatalog.Item item = itemsCatalog.getItem(activeBoost.itemId());
			if (item == null || item.boostInfo() == null)
			{
				continue;
			}
			Arrays.stream(item.boostInfo().effects())
					.filter(effect -> switch (effect.activation())
					{
						case INSTANT -> false;
						case TRIGGERED -> true;
						case TIMED -> activeBoost.startTime() + effect.duration() <= currentTime;
					})
					.forEach(effects::add);
		}
		return effects.toArray(Catalog.ItemsCatalog.Item.BoostInfo.Effect[]::new);
	}

	@NotNull
	public static Effect boostEffectToApiResponse(@NotNull Catalog.ItemsCatalog.Item.BoostInfo.Effect effect)
	{
		String effectTypeString = switch (effect.type())
		{
			case ADVENTURE_XP -> "ItemExperiencePoints";
			case CRAFTING -> "CraftingSpeed";
			case DEFENSE -> "PlayerDefense";
			case EATING -> "FoodHealth";
			case HEALING -> "Health";
			case HEALTH -> "MaximumPlayerHealth";
			case ITEM_XP -> "ItemExperiencePoints";
			case MINING_SPEED -> "BlockDamage";
			case RETENTION_BACKPACK -> "RetainBackpack";
			case RETENTION_HOTBAR -> "RetainHotbar";
			case RETENTION_XP -> "RetainExperiencePoints";
			case SMELTING -> "SmeltingFuelIntensity";
			case STRENGTH -> "AttackDamage";
			case TAPPABLE_RADIUS -> "TappableInteractionRadius";
		};

		String activationString = switch (effect.activation())
		{
			case INSTANT -> "Instant";
			case TIMED -> "Timed";
			case TRIGGERED -> "Triggered";
		};

		return new Effect(
				effectTypeString,
				effect.activation() == Catalog.ItemsCatalog.Item.BoostInfo.Effect.Activation.TIMED ? TimeFormatter.formatDuration(effect.duration()) : null,
				effect.type() == Catalog.ItemsCatalog.Item.BoostInfo.Effect.Type.RETENTION_BACKPACK || effect.type() == Catalog.ItemsCatalog.Item.BoostInfo.Effect.Type.RETENTION_HOTBAR || effect.type() == Catalog.ItemsCatalog.Item.BoostInfo.Effect.Type.RETENTION_XP ? null : effect.value(),
				switch (effect.type())
				{
					case HEALING, TAPPABLE_RADIUS -> "Increment";
					case ADVENTURE_XP, CRAFTING, DEFENSE, EATING, HEALTH, ITEM_XP, MINING_SPEED, SMELTING, STRENGTH -> "Percentage";
					case RETENTION_BACKPACK, RETENTION_HOTBAR, RETENTION_XP -> null;
				},
				effect.type() == Catalog.ItemsCatalog.Item.BoostInfo.Effect.Type.CRAFTING || effect.type() == Catalog.ItemsCatalog.Item.BoostInfo.Effect.Type.SMELTING ? "UtilityBlock" : "Player",
				effect.applicableItemIds(),
				switch (effect.type())
				{
					case ITEM_XP -> new String[]{"Tappable"};
					case ADVENTURE_XP -> new String[]{"Encounter"};
					default -> new String[0];
				},
				activationString,
				effect.type() == Catalog.ItemsCatalog.Item.BoostInfo.Effect.Type.EATING ? "Health" : null
		);
	}
}