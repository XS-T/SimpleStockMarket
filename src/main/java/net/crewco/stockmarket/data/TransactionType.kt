package net.crewco.stockmarket.data

/**
 * Transaction types
 */
enum class TransactionType(val displayName: String, val color: String) {
	BUY("Buy", "§a"),
	SELL("Sell", "§c"),
	DIVIDEND("Dividend", "§6")
}