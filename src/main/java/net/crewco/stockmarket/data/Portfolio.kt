package net.crewco.stockmarket.data

import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

/**
 * Player's portfolio
 */
data class Portfolio(
	val playerId: UUID,
	val balance: BigDecimal,
	val holdings: MutableMap<String, Int> = mutableMapOf(),
	val totalInvested: BigDecimal = BigDecimal.ZERO,
	val totalRealized: BigDecimal = BigDecimal.ZERO,
	val lastUpdated: LocalDateTime = LocalDateTime.now()
) {

	/**
	 * Get total number of stocks owned
	 */
	fun getTotalStocks(): Int {
		return holdings.values.sum()
	}

	/**
	 * Check if player owns a specific stock
	 */
	fun ownsStock(symbol: String): Boolean {
		return holdings[symbol] != null && holdings[symbol]!! > 0
	}

	/**
	 * Get portfolio diversity (number of different stocks)
	 */
	fun getDiversity(): Int {
		return holdings.count { it.value > 0 }
	}
}