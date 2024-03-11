package micheal65536.minecraftearth.apiserver.types.common;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;

public record Token(
		@NotNull Type clientType,
		@NotNull HashMap<String, String> clientProperties,
		@NotNull Rewards rewards,
		@NotNull Lifetime lifetime
)
{
	public enum Type
	{
		@SerializedName("adv_zyki") LEVEL_UP,
		@SerializedName("redeemtappable") TAPPABLE
	}

	public enum Lifetime
	{
		@SerializedName("Persistent") PERSISTENT,
		@SerializedName("Transient") TRANSIENT
	}
}