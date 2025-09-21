package net.crewco.stockmarket.data

import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Market data snapshot
 */
data class MarketSnapshot(
	val timestamp: LocalDateTime,
	val totalMarketCap: BigDecimal,
	val totalVolume: Long,
	val stockCount: Int,
	val topGainer: Stock?,
	val topLoser: Stock?,
	val isOpen: Boolean
)