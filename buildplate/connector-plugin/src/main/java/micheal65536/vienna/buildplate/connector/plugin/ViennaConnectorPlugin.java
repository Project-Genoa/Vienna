package micheal65536.vienna.buildplate.connector.plugin;

import org.jetbrains.annotations.NotNull;

import micheal65536.fountain.connector.plugin.ConnectorPlugin;
import micheal65536.fountain.connector.plugin.DisconnectResponse;
import micheal65536.fountain.connector.plugin.Inventory;
import micheal65536.fountain.connector.plugin.Logger;
import micheal65536.fountain.connector.plugin.PlayerLoginInfo;
import micheal65536.vienna.buildplate.connector.model.InventoryAddItemMessage;
import micheal65536.vienna.buildplate.connector.model.InventoryRemoveItemRequest;
import micheal65536.vienna.buildplate.connector.model.InventoryResponse;
import micheal65536.vienna.buildplate.connector.model.InventorySetHotbarMessage;
import micheal65536.vienna.buildplate.connector.model.InventoryUpdateItemWearMessage;
import micheal65536.vienna.buildplate.connector.model.PlayerConnectedRequest;
import micheal65536.vienna.buildplate.connector.model.PlayerConnectedResponse;
import micheal65536.vienna.buildplate.connector.model.PlayerDisconnectedRequest;
import micheal65536.vienna.buildplate.connector.model.PlayerDisconnectedResponse;
import micheal65536.vienna.buildplate.connector.model.WorldSavedMessage;
import micheal65536.vienna.eventbus.client.EventBusClient;
import micheal65536.vienna.eventbus.client.Publisher;
import micheal65536.vienna.eventbus.client.RequestSender;

import java.util.Arrays;
import java.util.Base64;

public final class ViennaConnectorPlugin implements ConnectorPlugin
{
	private String queueName;
	private EventBusClient eventBusClient;
	private Publisher publisher;
	private RequestSender requestSender;

	@Override
	public void init(@NotNull String arg, @NotNull Logger logger) throws ConnectorPluginException
	{
		try
		{
			String[] parts = arg.split("/", 2);
			if (parts.length != 2)
			{
				throw new IllegalArgumentException();
			}
			String address = parts[0];
			this.queueName = parts[1];
			try
			{
				this.eventBusClient = EventBusClient.create(!address.isEmpty() ? address : "localhost:5532");
			}
			catch (EventBusClient.ConnectException exception)
			{
				throw new ConnectorPluginException(exception);
			}
		}
		catch (IllegalArgumentException exception)
		{
			throw new ConnectorPluginException("Invalid address string \"%s\"".formatted(arg));
		}
		this.publisher = this.eventBusClient.addPublisher();
		this.requestSender = this.eventBusClient.addRequestSender();
	}

	@Override
	public void shutdown() throws ConnectorPluginException
	{
		this.requestSender.close();
		this.publisher.close();
		this.eventBusClient.close();
	}

	@Override
	public void onServerReady() throws ConnectorPluginException
	{
		EventBusHelper.publishJson(this.publisher, this.queueName, "started", null);
	}

	@Override
	public void onServerStopping() throws ConnectorPluginException
	{
		EventBusHelper.publishJson(this.publisher, this.queueName, "stopping", null);
	}

	@Override
	public void onWorldSaved(byte[] data) throws ConnectorPluginException
	{
		EventBusHelper.publishJson(this.publisher, this.queueName, "saved", new WorldSavedMessage(Base64.getEncoder().encodeToString(data)));
	}

	@Override
	public boolean onPlayerConnected(@NotNull PlayerLoginInfo playerLoginInfo) throws ConnectorPluginException
	{
		PlayerConnectedResponse playerConnectedResponse = EventBusHelper.doRequestResponseSync(this.requestSender, this.queueName, "playerConnected", new PlayerConnectedRequest(playerLoginInfo.uuid, ""), PlayerConnectedResponse.class); // TODO: join code
		if (!playerConnectedResponse.accepted())
		{
			return false;
		}
		return true;
	}

	@Override
	@NotNull
	public DisconnectResponse onPlayerDisconnected(@NotNull String playerId) throws ConnectorPluginException
	{
		PlayerDisconnectedResponse playerDisconnectedResponse = EventBusHelper.doRequestResponseSync(this.requestSender, this.queueName, "playerDisconnected", new PlayerDisconnectedRequest(playerId), PlayerDisconnectedResponse.class);
		return new DisconnectResponse();
	}

	@Override
	@NotNull
	public Inventory onPlayerGetInventory(@NotNull String playerId) throws ConnectorPluginException
	{
		InventoryResponse inventoryResponse = EventBusHelper.doRequestResponseSync(this.requestSender, this.queueName, "getInventory", playerId, InventoryResponse.class);
		Inventory inventory;
		try
		{
			inventory = new Inventory(
					Arrays.stream(inventoryResponse.items()).filter(item -> item.instanceId() == null).map(item -> new Inventory.StackableItem(item.id(), item.count())).toArray(Inventory.StackableItem[]::new),
					Arrays.stream(inventoryResponse.items()).filter(item -> item.instanceId() != null).map(item -> new Inventory.NonStackableItem(item.id(), item.instanceId(), item.wear())).toArray(Inventory.NonStackableItem[]::new),
					Arrays.stream(inventoryResponse.hotbar()).map(hotbarItem -> hotbarItem != null ? (hotbarItem.instanceId() != null ? new Inventory.HotbarItem(hotbarItem.id(), hotbarItem.instanceId()) : new Inventory.HotbarItem(hotbarItem.id(), hotbarItem.count())) : null).toArray(Inventory.HotbarItem[]::new)
			);
		}
		catch (IllegalArgumentException exception)
		{
			throw new ConnectorPluginException("Bad inventory data", exception);
		}
		return inventory;
	}

	@Override
	public void onPlayerInventoryAddItem(@NotNull String playerId, @NotNull String itemId, int count) throws ConnectorPluginException
	{
		EventBusHelper.publishJson(this.publisher, this.queueName, "inventoryAdd", new InventoryAddItemMessage(playerId, itemId, count, null, 0));
	}

	@Override
	public void onPlayerInventoryAddItem(@NotNull String playerId, @NotNull String itemId, @NotNull String instanceId, int wear) throws ConnectorPluginException
	{
		EventBusHelper.publishJson(this.publisher, this.queueName, "inventoryAdd", new InventoryAddItemMessage(playerId, itemId, 1, instanceId, wear));
	}

	@Override
	public int onPlayerInventoryRemoveItem(@NotNull String playerId, @NotNull String itemId, int count) throws ConnectorPluginException
	{
		return EventBusHelper.doRequestResponseSync(this.requestSender, this.queueName, "inventoryRemove", new InventoryRemoveItemRequest(playerId, itemId, count, null), Integer.class);
	}

	@Override
	public boolean onPlayerInventoryRemoveItem(@NotNull String playerId, @NotNull String itemId, @NotNull String instanceId) throws ConnectorPluginException
	{
		return EventBusHelper.doRequestResponseSync(this.requestSender, this.queueName, "inventoryRemove", new InventoryRemoveItemRequest(playerId, itemId, 1, instanceId), Boolean.class);
	}

	@Override
	public void onPlayerInventoryUpdateItemWear(@NotNull String playerId, @NotNull String itemId, @NotNull String instanceId, int wear) throws ConnectorPluginException
	{
		EventBusHelper.publishJson(this.publisher, this.queueName, "inventoryUpdateWear", new InventoryUpdateItemWearMessage(playerId, itemId, instanceId, wear));
	}

	@Override
	public void onPlayerInventorySetHotbar(@NotNull String playerId, Inventory.HotbarItem[] hotbar) throws ConnectorPluginException
	{
		EventBusHelper.publishJson(this.publisher, this.queueName, "inventorySetHotbar", new InventorySetHotbarMessage(playerId, Arrays.stream(hotbar).map(item -> item != null ? new InventorySetHotbarMessage.Item(item.uuid, item.count, item.instanceId) : null).toArray(InventorySetHotbarMessage.Item[]::new)));
	}
}