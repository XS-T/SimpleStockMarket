package net.crewco.stockmarket.data

import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Business metrics for business-tied stocks
 */

data class BusinessMetrics(
	val businessId: String,
	val revenue: BigDecimal,
	val profit: BigDecimal,
	val employeeCount: Int,
	val lastUpdated: LocalDateTime = LocalDateTime.now()
)