package net.crewco.stockmarket.data

import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

/**
 * Represents a stock transaction
 */
data class StockTransaction(
	val id: UUID = UUID.randomUUID(),
	val playerId: UUID,
	val symbol: String,
	val type: TransactionType,
	val quantity: Int,
	val pricePerShare: BigDecimal,
	val totalAmount: BigDecimal,
	val timestamp: LocalDateTime = LocalDateTime.now(),
	val fees: BigDecimal = BigDecimal.ZERO
) {

	/**
	 * Get net amount (considering fees)
	 */
	fun getNetAmount(): BigDecimal {
		return when (type) {
			TransactionType.BUY -> totalAmount + fees
			TransactionType.SELL -> totalAmount - fees
			TransactionType.DIVIDEND -> totalAmount
		}
	}
}
