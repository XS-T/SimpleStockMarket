package net.crewco.stockmarket.events.subclasses

import net.crewco.stockmarket.data.Stock
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * Event fired when a new stock is created
 */
class StockCreationEvent(
	val stock: Stock,
	val creator: String? = null // Could be "SYSTEM" or a business plugin name
) : Event() {

	companion object {
		private val handlers = HandlerList()

		@JvmStatic
		fun getHandlerList(): HandlerList = handlers
	}

	override fun getHandlers(): HandlerList = Companion.handlers
}