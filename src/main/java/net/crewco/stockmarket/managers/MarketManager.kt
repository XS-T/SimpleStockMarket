package net.crewco.stockmarket.managers

import com.google.inject.Inject
import net.crewco.stockmarket.StockMarketPlugin
import net.crewco.stockmarket.StockMarketPlugin.Companion.databaseManager
import net.crewco.stockmarket.StockMarketPlugin.Companion.portfolioManager
import net.crewco.stockmarket.StockMarketPlugin.Companion.stockManager
import net.crewco.stockmarket.data.Stock
import net.crewco.stockmarket.data.StockTransaction
import net.crewco.stockmarket.data.TransactionType
import net.crewco.stockmarket.data.BusinessMetrics
import net.crewco.stockmarket.events.StockPriceChangeEvent
import net.crewco.stockmarket.events.StockTransactionEvent
import net.crewco.stockmarket.events.subclasses.MarketEventTypeEvent
import net.crewco.stockmarket.managers.subclasses.MarketEventTypeManager
import org.bukkit.Bukkit
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalTime
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class MarketManager @Inject constructor(private val plugin: StockMarketPlugin) {

	private var marketOpen = true
	private val businessMetrics = ConcurrentHashMap<String, BusinessMetrics>()
	private val priceChangeCallbacks = mutableListOf<(StockPriceChangeEvent) -> Unit>()
	private val transactionCallbacks = mutableListOf<(StockTransactionEvent) -> Unit>()

	// Trading fees configuration
	private val tradingFeePercent = plugin.config.getDouble("trading.fee-percent", 0.1)
	private val minimumFee = BigDecimal.valueOf(plugin.config.getDouble("trading.minimum-fee", 1.0))

	init {
		// Set market hours from config
		updateMarketStatus()
	}

	/**
	 * Buy stocks for a player
	 */
	fun buyStock(playerId: UUID, symbol: String, quantity: Int): StockTransaction? {
		if (!marketOpen) return null
		if (quantity <= 0) return null
		val stock = stockManager.getStock(symbol) ?: return null
		val portfolio = portfolioManager.getPortfolio(playerId) ?: return null

		val totalCost = stock.currentPrice * BigDecimal.valueOf(quantity.toLong())
		val fees = calculateTradingFees(totalCost)
		val totalWithFees = totalCost + fees

		// Check if player has enough balance
		if (portfolio.balance < totalWithFees) {
			return null
		}

		// Execute transaction
		if (portfolioManager.removeBalance(playerId, totalWithFees) &&
			portfolioManager.addHoldings(playerId, symbol, quantity)) {

			val transaction = StockTransaction(
				playerId = playerId,
				symbol = symbol,
				type = TransactionType.BUY,
				quantity = quantity,
				pricePerShare = stock.currentPrice,
				totalAmount = totalCost,
				fees = fees
			)

			// Save transaction
			databaseManager.saveTransaction(transaction)

			// Update stock volume
			updateStockVolume(symbol, quantity.toLong())

			// Fire event
			val event = StockTransactionEvent(transaction, stock)
			Bukkit.getPluginManager().callEvent(event)
			transactionCallbacks.forEach { it(event) }

			return transaction
		}

		return null
	}

	/**
	 * Sell stocks for a player
	 */
	fun sellStock(playerId: UUID, symbol: String, quantity: Int): StockTransaction? {
		if (!marketOpen) return null
		if (quantity <= 0) return null

		val stock = stockManager.getStock(symbol) ?: return null
		val holdings = portfolioManager.getHoldings(playerId, symbol)

		// Check if player has enough stocks
		if (holdings < quantity) {
			return null
		}

		val totalRevenue = stock.currentPrice * BigDecimal.valueOf(quantity.toLong())
		val fees = calculateTradingFees(totalRevenue)
		val netRevenue = totalRevenue - fees

		// Execute transaction
		if (portfolioManager.removeHoldings(playerId, symbol, quantity) &&
			portfolioManager.addBalance(playerId, netRevenue)) {

			val transaction = StockTransaction(
				playerId = playerId,
				symbol = symbol,
				type = TransactionType.SELL,
				quantity = quantity,
				pricePerShare = stock.currentPrice,
				totalAmount = totalRevenue,
				fees = fees
			)

			// Save transaction
			databaseManager.saveTransaction(transaction)

			// Update stock volume
			updateStockVolume(symbol, quantity.toLong())

			// Fire event
			val event = StockTransactionEvent(transaction, stock)
			Bukkit.getPluginManager().callEvent(event)
			transactionCallbacks.forEach { it(event) }

			return transaction
		}

		return null
	}

	/**
	 * Get transaction history for a player
	 */
	fun getTransactionHistory(playerId: UUID, limit: Int): List<StockTransaction> {
		return databaseManager.getTransactionHistory(playerId, limit)
	}

	/**
	 * Calculate market cap for a stock
	 */
	fun getMarketCap(symbol: String): BigDecimal {
		val stock = stockManager.getStock(symbol) ?: return BigDecimal.ZERO
		return stock.currentPrice * BigDecimal.valueOf(stock.sharesIssued)
	}

	/**
	 * Get top performing stocks
	 */
	fun getTopPerformers(limit: Int): List<Stock> {
		return stockManager.getAllStocks()
			.sortedByDescending { it.getPercentageChange() }
			.take(limit)
	}

	/**
	 * Get worst performing stocks
	 */
	fun getWorstPerformers(limit: Int): List<Stock> {
		return stockManager.getAllStocks()
			.sortedBy { it.getPercentageChange() }
			.take(limit)
	}

	/**
	 * Check if market is open
	 */
	fun isMarketOpen(): Boolean {
		return marketOpen
	}

	/**
	 * Set market status
	 */
	fun setMarketOpen(open: Boolean): Boolean {
		marketOpen = open
		plugin.logger.info("Market is now ${if (open) "OPEN" else "CLOSED"}")
		return true
	}

	/**
	 * Update market status based on time
	 */
	private fun updateMarketStatus() {
		val now = LocalTime.now()
		val openTime = LocalTime.parse(plugin.config.getString("market.open-time", "09:00"))
		val closeTime = LocalTime.parse(plugin.config.getString("market.close-time", "17:00"))

		marketOpen = now.isAfter(openTime) && now.isBefore(closeTime)
	}

	/**
	 * Calculate trading fees
	 */
	private fun calculateTradingFees(amount: BigDecimal): BigDecimal {
		val feeAmount = amount * BigDecimal.valueOf(tradingFeePercent / 100)
		return feeAmount.max(minimumFee).setScale(2, RoundingMode.HALF_UP)
	}

	/**
	 * Update stock volume
	 */
	private fun updateStockVolume(symbol: String, volume: Long) {
		val stock = stockManager.getStock(symbol) ?: return
		val updatedStock = stock.copy(volume = stock.volume + volume)
		databaseManager.saveStock(updatedStock)
	}

	/**
	 * Update business metrics (affects stock price)
	 */
	fun updateBusinessMetrics(
		businessId: String,
		revenue: BigDecimal,
		profit: BigDecimal,
		employeeCount: Int
	): Boolean {
		val metrics = BusinessMetrics(businessId, revenue, profit, employeeCount)
		businessMetrics[businessId] = metrics

		// Find stock associated with this business
		val stock = stockManager.getAllStocks()
			.find { it.businessId == businessId } ?: return false

		// Calculate new stock price based on performance
		val profitMargin = if (revenue > BigDecimal.ZERO) {
			(profit / revenue).toDouble()
		} else 0.0

		val performanceMultiplier = 1.0 + (profitMargin * 0.5) // Profit affects price
		val employeeGrowthFactor = 1.0 + (employeeCount * 0.001) // More employees = slight price increase

		val newPrice = stock.currentPrice * BigDecimal.valueOf(performanceMultiplier * employeeGrowthFactor)
			.setScale(2, RoundingMode.HALF_UP)

		return stockManager.updateStockPrice(stock.symbol, newPrice, "Business Performance")
	}

	/**
	 * Pay dividends to shareholders
	 */
	fun payDividends(symbol: String, dividendPerShare: BigDecimal): Boolean {
		val stock = stockManager.getStock(symbol) ?: return false
		val shareholders = portfolioManager.getShareholders(symbol)

		shareholders.forEach { (playerId, shares) ->
			val dividendAmount = dividendPerShare * BigDecimal.valueOf(shares.toLong())

			// Add dividend to player balance
			portfolioManager.addBalance(playerId, dividendAmount)

			// Record dividend transaction
			val transaction = StockTransaction(
				playerId = playerId,
				symbol = symbol,
				type = TransactionType.DIVIDEND,
				quantity = shares,
				pricePerShare = dividendPerShare,
				totalAmount = dividendAmount
			)

			databaseManager.saveTransaction(transaction)
		}

		plugin.logger.info("Paid dividends for $symbol: ${formatCurrency(dividendPerShare)} per share")
		return true
	}

	/**
	 * Register price change callback
	 */
	fun registerPriceChangeCallback(callback: (StockPriceChangeEvent) -> Unit) {
		priceChangeCallbacks.add(callback)
	}

	/**
	 * Register transaction callback
	 */
	fun registerTransactionCallback(callback: (StockTransactionEvent) -> Unit) {
		transactionCallbacks.add(callback)
	}

	/**
	 * Format currency for display
	 */
	fun formatCurrency(amount: BigDecimal): String {
		val symbol = plugin.config.getString("currency.symbol", "$")
		return "$symbol${String.format("%,.2f", amount.toDouble())}"
	}

	/**
	 * Calculate percentage change
	 */
	fun calculatePercentageChange(oldValue: BigDecimal, newValue: BigDecimal): Double {
		return if (oldValue != BigDecimal.ZERO) {
			((newValue - oldValue) / oldValue * BigDecimal.valueOf(100)).toDouble()
		} else 0.0
	}

	/**
	 * Get business metrics
	 */
	fun getBusinessMetrics(businessId: String): BusinessMetrics? {
		return businessMetrics[businessId]
	}

	/**
	 * Execute market events (crashes, booms, etc.)
	 */
	fun executeMarketEvent(eventType: MarketEventTypeEvent, intensity: Double = 1.0) {
		when (eventType) {
			MarketEventTypeEvent.BULL_MARKET -> {
				stockManager.getAllStocks().forEach { stock ->
					val increase = stock.currentPrice * BigDecimal.valueOf(0.05 * intensity)
					val newPrice = stock.currentPrice + increase
					stockManager.updateStockPrice(stock.symbol, newPrice, "Bull Market")
				}
			}
			MarketEventTypeEvent.BEAR_MARKET -> {
				stockManager.getAllStocks().forEach { stock ->
					val decrease = stock.currentPrice * BigDecimal.valueOf(0.05 * intensity)
					val newPrice = (stock.currentPrice - decrease).max(BigDecimal.valueOf(0.01))
					stockManager.updateStockPrice(stock.symbol, newPrice, "Bear Market")
				}
			}
			MarketEventTypeEvent.SECTOR_BOOM -> {
				// Randomly select a sector and boost it
				val categories = stockManager.getAllStocks()
					.map { it.category }.distinct().shuffled().take(1)

				categories.forEach { category ->
					stockManager.getStocksByCategory(category).forEach { stock ->
						val increase = stock.currentPrice * BigDecimal.valueOf(0.15 * intensity)
						val newPrice = stock.currentPrice + increase
						stockManager.updateStockPrice(stock.symbol, newPrice, "Sector Boom")
					}
				}
			}

			MarketEventTypeEvent.MARKET_CRASH -> TODO()
			MarketEventTypeEvent.ECONOMIC_STIMULUS -> TODO()
			MarketEventTypeEvent.INTEREST_RATE_CHANGE -> TODO()
		}
	}
}