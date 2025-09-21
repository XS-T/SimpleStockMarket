package net.crewco.stockmarket.managers

import net.crewco.stockmarket.StockMarketPlugin
import net.crewco.stockmarket.StockMarketPlugin.Companion.databaseManager
import net.crewco.stockmarket.data.Stock
import net.crewco.stockmarket.data.StockCategory
import net.crewco.stockmarket.events.StockPriceChangeEvent
import org.bukkit.Bukkit
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

class StockManager(private val plugin: StockMarketPlugin) {

	private val stocks = ConcurrentHashMap<String, Stock>()
	private val stockHistory = ConcurrentHashMap<String, MutableList<Stock.PriceHistory>>()

	init {
		loadStocksFromDatabase()
		createDefaultStocks()
	}

	/**
	 * Get all stocks
	 */
	fun getAllStocks(): List<Stock> {
		return stocks.values.toList()
	}

	/**
	 * Get a specific stock
	 */
	fun getStock(symbol: String): Stock? {
		return stocks[symbol.uppercase()]
	}

	/**
	 * Create a new stock
	 */
	fun createStock(
		symbol: String,
		name: String,
		initialPrice: BigDecimal,
		category: String,
		volatility: Double
	): Boolean {
		val upperSymbol = symbol.uppercase()
		if (stocks.containsKey(upperSymbol)) {
			return false
		}

		val stockCategory = try {
			StockCategory.valueOf(category.uppercase())
		} catch (e: IllegalArgumentException) {
			StockCategory.CUSTOM
		}

		val stock = Stock(
			symbol = upperSymbol,
			name = name,
			currentPrice = initialPrice,
			previousPrice = initialPrice,
			category = stockCategory,
			volatility = volatility.coerceIn(0.01, 1.0)
		)

		stocks[upperSymbol] = stock
		stockHistory[upperSymbol] = mutableListOf()

		// Save to database
		databaseManager.saveStock(stock)

		return true
	}

	/**
	 * Create a business stock
	 */
	fun createBusinessStock(
		symbol: String,
		name: String,
		initialPrice: BigDecimal,
		businessId: String,
		sharesIssued: Long
	): Boolean {
		val upperSymbol = symbol.uppercase()
		if (stocks.containsKey(upperSymbol)) {
			return false
		}

		val stock = Stock(
			symbol = upperSymbol,
			name = name,
			currentPrice = initialPrice,
			previousPrice = initialPrice,
			category = StockCategory.BUSINESS,
			volatility = 0.03, // Lower volatility for business stocks
			businessId = businessId,
			sharesIssued = sharesIssued
		)

		stocks[upperSymbol] = stock
		stockHistory[upperSymbol] = mutableListOf()

		databaseManager.saveStock(stock)
		return true
	}

	/**
	 * Update stock price
	 */
	fun updateStockPrice(symbol: String, newPrice: BigDecimal, reason: String): Boolean {
		val stock = stocks[symbol.uppercase()] ?: return false
		val oldPrice = stock.currentPrice

		val updatedStock = stock.copy(
			previousPrice = oldPrice,
			currentPrice = newPrice.setScale(2, RoundingMode.HALF_UP),
			lastUpdated = LocalDateTime.now()
		)

		stocks[symbol.uppercase()] = updatedStock

		// Add to history
		addPriceHistory(symbol, newPrice, reason)

		// Save to database
		databaseManager.saveStock(updatedStock)

		// Fire event
		val event = StockPriceChangeEvent(updatedStock, oldPrice, newPrice, reason)
		Bukkit.getPluginManager().callEvent(event)

		return true
	}

	/**
	 * Simulate market fluctuations
	 */
	fun simulateMarketFluctuations() {
		val marketTrend = Random.nextDouble(-0.1, 0.1) // Overall market sentiment

		stocks.values.forEach { stock ->
			if (stock.businessId == null) { // Don't auto-update business stocks
				val volatility = stock.volatility
				val randomChange = Random.nextDouble(-volatility, volatility)
				val trendInfluence = marketTrend * 0.3
				val totalChange = randomChange + trendInfluence

				val newPrice = stock.currentPrice * BigDecimal.valueOf(1 + totalChange)
				val clampedPrice = newPrice.max(BigDecimal.valueOf(0.01)) // Minimum price

				updateStockPrice(stock.symbol, clampedPrice, "Market")
			}
		}
	}

	/**
	 * Get stock history
	 */
	fun getStockHistory(symbol: String, days: Int): List<Stock.PriceHistory> {
		val history = stockHistory[symbol.uppercase()] ?: return emptyList()
		val cutoffDate = LocalDateTime.now().minusDays(days.toLong())

		return history.filter { it.timestamp.isAfter(cutoffDate) }
			.sortedByDescending { it.timestamp }
	}

	/**
	 * Add price history entry
	 */
	private fun addPriceHistory(symbol: String, price: BigDecimal, reason: String) {
		val history = stockHistory.getOrPut(symbol.uppercase()) { mutableListOf() }

		history.add(Stock.PriceHistory(
			timestamp = LocalDateTime.now(),
			price = price,
			volume = Random.nextLong(1000, 100000),
			reason = reason
		))

		// Keep only last 30 days
		val cutoffDate = LocalDateTime.now().minusDays(30)
		history.removeIf { it.timestamp.isBefore(cutoffDate) }
	}

	/**
	 * Load stocks from database
	 */
	private fun loadStocksFromDatabase() {
		val loadedStocks = databaseManager.loadAllStocks()
		loadedStocks.forEach { stock ->
			stocks[stock.symbol] = stock
			stockHistory[stock.symbol] = mutableListOf()
		}
	}

	/**
	 * Create default stocks if none exist
	 */
	private fun createDefaultStocks() {
		if (stocks.isEmpty()) {
			val defaultStocks = listOf(
				Triple("TECH", "TechCorp Industries", StockCategory.TECHNOLOGY),
				Triple("BANK", "National Bank", StockCategory.FINANCE),
				Triple("HEAL", "HealthCare Plus", StockCategory.HEALTHCARE),
				Triple("ENRG", "Energy Solutions", StockCategory.ENERGY),
				Triple("SHOP", "Consumer Goods Co", StockCategory.CONSUMER),
				Triple("MINE", "Mining Corp", StockCategory.MATERIALS),
				Triple("UTIL", "Utilities United", StockCategory.UTILITIES),
				Triple("PROP", "Real Estate Group", StockCategory.REAL_ESTATE)
			)

			defaultStocks.forEach { (symbol, name, category) ->
				val price = BigDecimal.valueOf(Random.nextDouble(10.0, 500.0))
					.setScale(2, RoundingMode.HALF_UP)

				createStock(symbol, name, price, category.name, Random.nextDouble(0.02, 0.15))
			}

			plugin.logger.info("Created ${defaultStocks.size} default stocks")
		}
	}

	/**
	 * Get stocks by category
	 */
	fun getStocksByCategory(category: StockCategory): List<Stock> {
		return stocks.values.filter { it.category == category }
	}

	/**
	 * Search stocks by name or symbol
	 */
	fun searchStocks(query: String): List<Stock> {
		val lowercaseQuery = query.lowercase()
		return stocks.values.filter { stock ->
			stock.symbol.lowercase().contains(lowercaseQuery) ||
					stock.name.lowercase().contains(lowercaseQuery)
		}
	}
}