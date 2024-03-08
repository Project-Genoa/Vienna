package micheal65536.minecraftearth.apiserver.types.common;

import com.google.gson.annotations.SerializedName;

public enum Rarity
{
	@SerializedName("Common") COMMON,
	@SerializedName("Uncommon") UNCOMMON,
	@SerializedName("Rare") RARE,
	@SerializedName("Epic") EPIC,
	@SerializedName("Legendary") LEGENDARY,
	@SerializedName("oobe") OOBE
}