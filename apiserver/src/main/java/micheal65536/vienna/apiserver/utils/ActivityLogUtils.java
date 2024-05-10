package micheal65536.vienna.apiserver.utils;

import org.jetbrains.annotations.NotNull;

import micheal65536.vienna.db.EarthDB;
import micheal65536.vienna.db.model.player.ActivityLog;

public final class ActivityLogUtils
{
	@NotNull
	public static EarthDB.Query addEntry(@NotNull String playerId, @NotNull ActivityLog.Entry entry)
	{
		EarthDB.Query getQuery = new EarthDB.Query(true);
		getQuery.get("activityLog", playerId, ActivityLog.class);
		getQuery.then(results ->
		{
			ActivityLog activityLog = (ActivityLog) results.get("activityLog").value();
			activityLog.addEntry(entry);
			EarthDB.Query updateQuery = new EarthDB.Query(true);
			updateQuery.update("activityLog", playerId, activityLog);
			return updateQuery;
		});
		return getQuery;
	}
}