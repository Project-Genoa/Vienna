package micheal65536.vienna.apiserver.types.workshop;

import com.google.gson.annotations.SerializedName;

public enum State
{
	@SerializedName("Empty") EMPTY,
	@SerializedName("Active") ACTIVE,
	@SerializedName("Completed") COMPLETED,
	@SerializedName("Locked") LOCKED
}