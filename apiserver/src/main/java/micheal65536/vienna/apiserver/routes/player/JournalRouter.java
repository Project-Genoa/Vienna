package micheal65536.vienna.apiserver.routes.player;

import org.jetbrains.annotations.NotNull;

import micheal65536.vienna.apiserver.routing.Request;
import micheal65536.vienna.apiserver.routing.Response;
import micheal65536.vienna.apiserver.routing.Router;
import micheal65536.vienna.apiserver.routing.ServerErrorException;
import micheal65536.vienna.apiserver.utils.EarthApiResponse;
import micheal65536.vienna.apiserver.utils.Rewards;
import micheal65536.vienna.apiserver.utils.TimeFormatter;
import micheal65536.vienna.db.DatabaseException;
import micheal65536.vienna.db.EarthDB;
import micheal65536.vienna.db.model.player.ActivityLog;
import micheal65536.vienna.db.model.player.Journal;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;

public class JournalRouter extends Router
{
	public JournalRouter(@NotNull EarthDB earthDB)
	{
		this.addHandler(new Route.Builder(Request.Method.GET, "/player/journal").build(), request ->
		{
			String playerId = request.getContextData("playerId");
			Journal journalModel;
			ActivityLog activityLogModel;
			try
			{
				EarthDB.Results results = new EarthDB.Query(false)
						.get("journal", playerId, Journal.class)
						.get("activityLog", playerId, ActivityLog.class)
						.execute(earthDB);
				journalModel = (Journal) results.get("journal").value();
				activityLogModel = (ActivityLog) results.get("activityLog").value();
			}
			catch (DatabaseException exception)
			{
				throw new ServerErrorException(exception);
			}

			HashMap<String, micheal65536.vienna.apiserver.types.journal.Journal.InventoryJournalEntry> inventoryJournal = new HashMap<>();
			journalModel.getItems().forEach((uuid, itemJournalEntry) -> inventoryJournal.put(uuid, new micheal65536.vienna.apiserver.types.journal.Journal.InventoryJournalEntry(
					TimeFormatter.formatTime(itemJournalEntry.firstSeen()),
					TimeFormatter.formatTime(itemJournalEntry.lastSeen()),
					itemJournalEntry.amountCollected()
			)));

			LinkedList<micheal65536.vienna.apiserver.types.journal.Journal.ActivityLogEntry> activityLogList = Arrays.stream(activityLogModel.getEntries())
					.map(JournalRouter::activityLogEntryToApiResponse)
					.collect(LinkedList::new, LinkedList::add, LinkedList::addAll);
			Collections.reverse(activityLogList);
			micheal65536.vienna.apiserver.types.journal.Journal.ActivityLogEntry[] activityLog = activityLogList.toArray(micheal65536.vienna.apiserver.types.journal.Journal.ActivityLogEntry[]::new);

			return Response.okFromJson(new EarthApiResponse<>(new micheal65536.vienna.apiserver.types.journal.Journal(inventoryJournal, activityLog)), EarthApiResponse.class);
		});
	}

	@NotNull
	private static micheal65536.vienna.apiserver.types.journal.Journal.ActivityLogEntry activityLogEntryToApiResponse(@NotNull ActivityLog.Entry entry)
	{
		Rewards rewards = switch (entry.type)
		{
			case LEVEL_UP -> new Rewards().setLevel(((ActivityLog.LevelUpEntry) entry).level);
			case TAPPABLE -> Rewards.fromDBRewardsModel(((ActivityLog.TappableEntry) entry).rewards);
			case JOURNAL_ITEM_UNLOCKED -> new Rewards().addItem(((ActivityLog.JournalItemUnlockedEntry) entry).itemId, 0);
			case CRAFTING_COMPLETED -> Rewards.fromDBRewardsModel(((ActivityLog.CraftingCompletedEntry) entry).rewards);
			case SMELTING_COMPLETED -> Rewards.fromDBRewardsModel(((ActivityLog.SmeltingCompletedEntry) entry).rewards);
		};

		HashMap<String, String> properties = new HashMap<>();

		return new micheal65536.vienna.apiserver.types.journal.Journal.ActivityLogEntry(
				micheal65536.vienna.apiserver.types.journal.Journal.ActivityLogEntry.Type.valueOf(entry.type.name()),
				TimeFormatter.formatTime(entry.timestamp),
				rewards.toApiResponse(),
				properties
		);
	}
}