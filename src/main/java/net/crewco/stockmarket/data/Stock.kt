package net.crewco.stockmarket.data

import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

/**
 * Represents a stock in the market
 */
data class Stock(
	val symbol: String,
	val name: String,
	val currentPrice: BigDecimal,
	val previousPrice: BigDecimal,
	val category: StockCategory,
	val volatility: Double,
	val marketCap: BigDecimal = BigDecimal.ZERO,
	val volume: Long = 0,
	val lastUpdated: LocalDateTime = LocalDateTime.now(),
	val businessId: String? = null,
	val sharesIssued: Long = 1000000L,
	val dividendYield: Double = 0.0
) {

	/**
	 * Calculate percentage change from previous price
	 */
	fun getPercentageChange(): Double {
		return if (previousPrice != BigDecimal.ZERO) {
			((currentPrice - previousPrice) / previousPrice * BigDecimal.valueOf(100)).toDouble()
		} else 0.0
	}

	/**
	 * Get price change amount
	 */
	fun getPriceChange(): BigDecimal {
		return currentPrice - previousPrice
	}

	/**
	 * Check if stock is trending up
	 */
	fun isTrendingUp(): Boolean = currentPrice > previousPrice

	/**
	 * Check if stock is trending down
	 */
	fun isTrendingDown(): Boolean = currentPrice < previousPrice

	/**
	 * Price history entry
	 */
	data class PriceHistory(
		val timestamp: LocalDateTime,
		val price: BigDecimal,
		val volume: Long,
		val reason: String = "Market"
	)
}
