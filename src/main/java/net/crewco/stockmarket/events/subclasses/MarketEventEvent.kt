package net.crewco.stockmarket.events.subclasses

import net.crewco.stockmarket.data.Stock
import net.crewco.stockmarket.managers.subclasses.MarketEventTypeManager
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * Event fired during major market events
 */
class MarketEventEvent(
	val eventType: MarketEventTypeManager,
	val intensity: Double,
	val affectedStocks: List<Stock>,
	val description: String
) : Event() {

	companion object {
		private val handlers = HandlerList()

		@JvmStatic
		fun getHandlerList(): HandlerList = handlers
	}

	override fun getHandlers(): HandlerList = Companion.handlers
}