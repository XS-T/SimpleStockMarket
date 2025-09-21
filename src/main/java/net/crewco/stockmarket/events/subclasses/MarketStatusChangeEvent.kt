package net.crewco.stockmarket.events.subclasses

import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * Event fired when market opens or closes
 */
class MarketStatusChangeEvent(
	val isOpen: Boolean,
	val reason: String = "Scheduled"
) : Event() {

	companion object {
		private val handlers = HandlerList()

		@JvmStatic
		fun getHandlerList(): HandlerList = handlers
	}

	override fun getHandlers(): HandlerList = Companion.handlers
}