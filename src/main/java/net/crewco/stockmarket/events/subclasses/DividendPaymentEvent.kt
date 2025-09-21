package net.crewco.stockmarket.events.subclasses

import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import java.math.BigDecimal

/**
 * Event fired when dividends are paid
 */
class DividendPaymentEvent(
	val symbol: String,
	val dividendPerShare: BigDecimal,
	val totalShareholders: Int,
	val totalPayout: BigDecimal
) : Event() {

	companion object {
		private val handlers = HandlerList()

		@JvmStatic
		fun getHandlerList(): HandlerList = handlers
	}

	override fun getHandlers(): HandlerList = Companion.handlers
}