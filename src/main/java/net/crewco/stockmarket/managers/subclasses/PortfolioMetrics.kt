package net.crewco.stockmarket.managers.subclasses

import java.math.BigDecimal

/**
 * Portfolio performance metrics
 */
data class PortfolioMetrics(
	val totalValue: BigDecimal,
	val cashBalance: BigDecimal,
	val totalInvested: BigDecimal,
	val unrealizedPnL: BigDecimal,
	val realizedPnL: BigDecimal,
	val totalPnL: BigDecimal,
	val roi: Double, // Return on Investment percentage
	val diversity: Int,
	val totalStocks: Int
)