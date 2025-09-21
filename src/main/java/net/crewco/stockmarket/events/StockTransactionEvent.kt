package net.crewco.stockmarket.events

import net.crewco.stockmarket.data.Stock
import net.crewco.stockmarket.data.StockTransaction
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * Event fired when a stock transaction occurs
 */
class StockTransactionEvent(
	val transaction: StockTransaction,
	val stock: Stock
) : Event() {

	companion object {
		private val handlers = HandlerList()

		@JvmStatic
		fun getHandlerList(): HandlerList = handlers
	}

	override fun getHandlers(): HandlerList = Companion.handlers

	fun isBuy(): Boolean = transaction.type.name == "BUY"
	fun isSell(): Boolean = transaction.type.name == "SELL"
	fun isDividend(): Boolean = transaction.type.name == "DIVIDEND"
}