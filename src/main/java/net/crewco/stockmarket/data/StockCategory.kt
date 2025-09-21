package net.crewco.stockmarket.data

/**
 * Stock categories for organization
 */
enum class StockCategory(val displayName: String, val color: String) {
	TECHNOLOGY("Technology", "§b"),
	FINANCE("Finance", "§a"),
	HEALTHCARE("Healthcare", "§d"),
	ENERGY("Energy", "§6"),
	CONSUMER("Consumer Goods", "§e"),
	INDUSTRIAL("Industrial", "§8"),
	MATERIALS("Materials", "§7"),
	UTILITIES("Utilities", "§3"),
	REAL_ESTATE("Real Estate", "§2"),
	BUSINESS("Business", "§5"),
	CUSTOM("Custom", "§f")
}