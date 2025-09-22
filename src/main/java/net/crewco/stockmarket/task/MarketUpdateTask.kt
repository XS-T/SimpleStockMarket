package net.crewco.stockmarket.task


import com.google.inject.Inject
import net.crewco.stockmarket.StockMarketPlugin
import net.crewco.stockmarket.StockMarketPlugin.Companion.databaseManager
import net.crewco.stockmarket.events.subclasses.MarketEventEvent
import org.bukkit.Bukkit
import org.bukkit.scheduler.BukkitRunnable
import kotlin.random.Random
import net.crewco.stockmarket.StockMarketPlugin.Companion.marketManager
import net.crewco.stockmarket.StockMarketPlugin.Companion.portfolioManager
import net.crewco.stockmarket.StockMarketPlugin.Companion.stockManager
import net.crewco.stockmarket.events.subclasses.MarketEventTypeEvent
import net.crewco.stockmarket.managers.subclasses.MarketEventTypeManager

class MarketUpdateTask @Inject constructor(private val plugin: StockMarketPlugin) : BukkitRunnable() {

	private var tickCount = 0
	private val updateInterval = plugin.config.getInt("market.fluctuation-interval", 5)
	private val eventChance = plugin.config.getDouble("market.event-chance", 0.02) // 2% chance per cycle

	override fun run() {
		try {
			tickCount++
			// Only update if market is open
			if (!marketManager.isMarketOpen()) {
				return
			}

			// Regular market fluctuations every few ticks
			if (tickCount % updateInterval == 0) {
				stockManager.simulateMarketFluctuations()
			}

			// Random market events
			if (Random.nextDouble() < eventChance) {
				triggerRandomMarketEvent()
			}

			// Cleanup database every hour (3600 ticks at 20 TPS)
			if (tickCount % 3600 == 0) {
				databaseManager.cleanup()
			}

			// Save portfolios every 10 minutes
			if (tickCount % 600 == 0) {
				portfolioManager.saveAllPortfolios()
			}

		} catch (e: Exception) {
			plugin.logger.severe("Error in market update task: ${e.message}")
			e.printStackTrace()
		}
	}

	private fun triggerRandomMarketEvent() {
		val eventTypes = MarketEventTypeManager.entries.toTypedArray()
		val randomEvent = eventTypes.random()
		val intensity = Random.nextDouble(0.5, 2.0)

		when (randomEvent) {
			MarketEventTypeManager.BULL_MARKET -> {
				marketManager.executeMarketEvent(randomEvent, intensity)
				broadcastMarketNews("§a📈 BULL MARKET: All stocks are surging upward!")
			}
			MarketEventTypeManager.BEAR_MARKET -> {
				marketManager.executeMarketEvent(randomEvent, intensity)
				broadcastMarketNews("§c📉 BEAR MARKET: Market experiencing widespread decline!")
			}
			MarketEventTypeManager.SECTOR_BOOM -> {
				marketManager.executeMarketEvent(randomEvent, intensity)
				broadcastMarketNews("§6🚀 SECTOR BOOM: Specific sector experiencing rapid growth!")
			}
		}

		// Fire custom event for other plugins
		val affectedStocks = stockManager.getAllStocks()
		val event = MarketEventEvent(
			eventType = randomEvent,
			intensity = intensity,
			affectedStocks = affectedStocks,
			description = getEventDescription(randomEvent)
		)
		Bukkit.getPluginManager().callEvent(event)
	}

	private fun broadcastMarketNews(message: String) {
		val enableNews = plugin.config.getBoolean("market.broadcast-news", true)
		if (enableNews) {
			Bukkit.getOnlinePlayers().forEach { player ->
				if (player.hasPermission("stockmarket.news")) {
					player.sendMessage("§7[§6Market News§7] $message")
				}
			}
		}

		plugin.logger.info("Market Event: $message")
	}

	private fun getEventDescription(eventType: Any): String {
		return when (eventType) {
			MarketEventTypeManager.BULL_MARKET -> "Strong economic indicators drive widespread optimism"
			MarketEventTypeManager.BEAR_MARKET -> "Economic uncertainty causes market-wide pessimism"
			MarketEventTypeManager.SECTOR_BOOM -> "Technological breakthrough boosts sector performance"
			MarketEventTypeEvent.MARKET_CRASH -> "Unexpected event triggers rapid market decline"
			MarketEventTypeEvent.ECONOMIC_STIMULUS -> "Government stimulus package boosts market confidence"
			MarketEventTypeEvent.INTEREST_RATE_CHANGE -> "Central bank adjusts interest rates affecting all sectors"
			else -> {
				return "Not a Valid Event"
			}
		}
	}
}