package net.crewco.stockmarket.events

import net.crewco.stockmarket.data.Stock
import net.crewco.stockmarket.data.StockTransaction
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import java.math.BigDecimal

/**
 * Event fired when a stock price changes
 */
class StockPriceChangeEvent(
	val stock: Stock,
	val oldPrice: BigDecimal,
	val newPrice: BigDecimal,
	val reason: String
) : Event() {

	companion object {
		private val handlers = HandlerList()

		@JvmStatic
		fun getHandlerList(): HandlerList = handlers
	}

	override fun getHandlers(): HandlerList = Companion.handlers

	fun getPriceChange(): BigDecimal = newPrice - oldPrice

	fun getPercentageChange(): Double {
		return if (oldPrice != BigDecimal.ZERO) {
			((newPrice - oldPrice) / oldPrice * BigDecimal.valueOf(100)).toDouble()
		} else 0.0
	}

	fun isIncrease(): Boolean = newPrice > oldPrice
	fun isDecrease(): Boolean = newPrice < oldPrice
}