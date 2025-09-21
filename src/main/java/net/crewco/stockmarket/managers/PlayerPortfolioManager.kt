package net.crewco.stockmarket.managers

import net.crewco.stockmarket.StockMarketPlugin
import net.crewco.stockmarket.StockMarketPlugin.Companion.databaseManager
import net.crewco.stockmarket.StockMarketPlugin.Companion.marketManager
import net.crewco.stockmarket.StockMarketPlugin.Companion.stockManager
import net.crewco.stockmarket.data.Portfolio
import net.crewco.stockmarket.managers.subclasses.PortfolioMetrics
import java.math.BigDecimal
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class PlayerPortfolioManager(private val plugin: StockMarketPlugin) {

	private val portfolios = ConcurrentHashMap<UUID, Portfolio>()

	init {
		loadPortfoliosFromDatabase()
	}

	/**
	 * Get or create portfolio for player
	 */
	fun getPortfolio(playerId: UUID): Portfolio {
		return portfolios.getOrPut(playerId) {
			val startingBalance = BigDecimal.valueOf(plugin.config.getDouble("economy.starting-balance", 10000.0))
			val portfolio = Portfolio(playerId, startingBalance)
			databaseManager.savePortfolio(portfolio)
			portfolio
		}
	}

	/**
	 * Get player balance
	 */
	fun getBalance(playerId: UUID): BigDecimal {
		return getPortfolio(playerId).balance
	}

	/**
	 * Add balance to player
	 */
	fun addBalance(playerId: UUID, amount: BigDecimal): Boolean {
		if (amount <= BigDecimal.ZERO) return false

		val portfolio = getPortfolio(playerId)
		val updatedPortfolio = portfolio.copy(balance = portfolio.balance + amount)
		portfolios[playerId] = updatedPortfolio
		databaseManager.savePortfolio(updatedPortfolio)
		return true
	}

	/**
	 * Remove balance from player
	 */
	fun removeBalance(playerId: UUID, amount: BigDecimal): Boolean {
		if (amount <= BigDecimal.ZERO) return false

		val portfolio = getPortfolio(playerId)
		if (portfolio.balance < amount) return false

		val updatedPortfolio = portfolio.copy(balance = portfolio.balance - amount)
		portfolios[playerId] = updatedPortfolio
		databaseManager.savePortfolio(updatedPortfolio)
		return true
	}

	/**
	 * Set player balance
	 */
	fun setBalance(playerId: UUID, amount: BigDecimal): Boolean {
		if (amount < BigDecimal.ZERO) return false

		val portfolio = getPortfolio(playerId)
		val updatedPortfolio = portfolio.copy(balance = amount)
		portfolios[playerId] = updatedPortfolio
		databaseManager.savePortfolio(updatedPortfolio)
		return true
	}

	/**
	 * Get player holdings for specific stock
	 */
	fun getHoldings(playerId: UUID, symbol: String): Int {
		return getPortfolio(playerId).holdings[symbol.uppercase()] ?: 0
	}

	/**
	 * Get all player holdings
	 */
	fun getAllHoldings(playerId: UUID): Map<String, Int> {
		return getPortfolio(playerId).holdings.toMap()
	}

	/**
	 * Add holdings to player
	 */
	fun addHoldings(playerId: UUID, symbol: String, quantity: Int): Boolean {
		if (quantity <= 0) return false

		val portfolio = getPortfolio(playerId)
		val currentHoldings = portfolio.holdings[symbol.uppercase()] ?: 0

		portfolio.holdings[symbol.uppercase()] = currentHoldings + quantity
		databaseManager.savePortfolio(portfolio)
		return true
	}

	/**
	 * Remove holdings from player
	 */
	fun removeHoldings(playerId: UUID, symbol: String, quantity: Int): Boolean {
		if (quantity <= 0) return false

		val portfolio = getPortfolio(playerId)
		val currentHoldings = portfolio.holdings[symbol.uppercase()] ?: 0

		if (currentHoldings < quantity) return false

		val newHoldings = currentHoldings - quantity
		if (newHoldings == 0) {
			portfolio.holdings.remove(symbol.uppercase())
		} else {
			portfolio.holdings[symbol.uppercase()] = newHoldings
		}

		databaseManager.savePortfolio(portfolio)
		return true
	}

	/**
	 * Set holdings for player
	 */
	fun setHoldings(playerId: UUID, symbol: String, quantity: Int): Boolean {
		if (quantity < 0) return false

		val portfolio = getPortfolio(playerId)

		if (quantity == 0) {
			portfolio.holdings.remove(symbol.uppercase())
		} else {
			portfolio.holdings[symbol.uppercase()] = quantity
		}

		databaseManager.savePortfolio(portfolio)
		return true
	}

	/**
	 * Calculate total portfolio value (balance + stock holdings)
	 */
	fun calculateTotalValue(playerId: UUID): BigDecimal {
		val portfolio = getPortfolio(playerId)
		var totalValue = portfolio.balance

		portfolio.holdings.forEach { (symbol, quantity) ->
			val stock = stockManager.getStock(symbol)
			if (stock != null) {
				totalValue += stock.currentPrice * BigDecimal.valueOf(quantity.toLong())
			}
		}

		return totalValue
	}

	/**
	 * Calculate unrealized gains/losses
	 */
	fun calculateUnrealizedPnL(playerId: UUID): Map<String, BigDecimal> {
		val portfolio = getPortfolio(playerId)
		val pnlMap = mutableMapOf<String, BigDecimal>()

		portfolio.holdings.forEach { (symbol, quantity) ->
			val stock = stockManager.getStock(symbol)
			if (stock != null) {
				val transactions = marketManager.getTransactionHistory(playerId, 1000)
					.filter { it.symbol == symbol && it.type.name in listOf("BUY", "SELL") }

				// Calculate average buy price
				var totalBought = 0
				var totalCost = BigDecimal.ZERO

				transactions.forEach { transaction ->
					when (transaction.type.name) {
						"BUY" -> {
							totalBought += transaction.quantity
							totalCost += transaction.totalAmount
						}
						"SELL" -> {
							totalBought -= transaction.quantity
							val avgPrice = if (totalBought > 0) totalCost / BigDecimal.valueOf(totalBought.toLong()) else BigDecimal.ZERO
							totalCost -= avgPrice * BigDecimal.valueOf(transaction.quantity.toLong())
						}
					}
				}

				if (totalBought > 0 && quantity > 0) {
					val avgBuyPrice = totalCost / BigDecimal.valueOf(totalBought.toLong())
					val currentValue = stock.currentPrice * BigDecimal.valueOf(quantity.toLong())
					val bookValue = avgBuyPrice * BigDecimal.valueOf(quantity.toLong())
					pnlMap[symbol] = currentValue - bookValue
				}
			}
		}

		return pnlMap
	}

	/**
	 * Get portfolio performance metrics
	 */
	fun getPortfolioMetrics(playerId: UUID): PortfolioMetrics {
		val portfolio = getPortfolio(playerId)
		val totalValue = calculateTotalValue(playerId)
		val unrealizedPnL = calculateUnrealizedPnL(playerId)
		val totalUnrealized = unrealizedPnL.values.fold(BigDecimal.ZERO) { acc, pnl -> acc + pnl }

		val transactions = marketManager.getTransactionHistory(playerId, 1000)
		val realizedPnL = transactions
			.filter { it.type.name == "SELL" }
			.map { it.totalAmount - it.fees }
			.fold(BigDecimal.ZERO) { acc, amount -> acc + amount }

		val totalPnL = totalUnrealized + realizedPnL
		val roi = if (portfolio.totalInvested > BigDecimal.ZERO) {
			(totalPnL / portfolio.totalInvested * BigDecimal.valueOf(100)).toDouble()
		} else 0.0

		return PortfolioMetrics(
			totalValue = totalValue,
			cashBalance = portfolio.balance,
			totalInvested = portfolio.totalInvested,
			unrealizedPnL = totalUnrealized,
			realizedPnL = realizedPnL,
			totalPnL = totalPnL,
			roi = roi,
			diversity = portfolio.getDiversity(),
			totalStocks = portfolio.getTotalStocks()
		)
	}

	/**
	 * Get shareholders for a specific stock
	 */
	fun getShareholders(symbol: String): Map<UUID, Int> {
		val shareholders = mutableMapOf<UUID, Int>()

		portfolios.forEach { (playerId, portfolio) ->
			val holdings = portfolio.holdings[symbol.uppercase()]
			if (holdings != null && holdings > 0) {
				shareholders[playerId] = holdings
			}
		}

		return shareholders
	}

	/**
	 * Get top portfolios by value
	 */
	fun getTopPortfolios(limit: Int = 10): List<Pair<UUID, BigDecimal>> {
		return portfolios.keys.map { playerId ->
			playerId to calculateTotalValue(playerId)
		}.sortedByDescending { it.second }.take(limit)
	}

	/**
	 * Reset player portfolio
	 */
	fun resetPortfolio(playerId: UUID): Boolean {
		val startingBalance = BigDecimal.valueOf(plugin.config.getDouble("economy.starting-balance", 10000.0))
		val newPortfolio = Portfolio(playerId, startingBalance)

		portfolios[playerId] = newPortfolio
		databaseManager.savePortfolio(newPortfolio)
		return true
	}

	/**
	 * Load portfolios from database
	 */
	private fun loadPortfoliosFromDatabase() {
		val loadedPortfolios = databaseManager.loadAllPortfolios()
		loadedPortfolios.forEach { portfolio ->
			portfolios[portfolio.playerId] = portfolio
		}
		plugin.logger.info("Loaded ${loadedPortfolios.size} portfolios from database")
	}

	/**
	 * Save all portfolios to database
	 */
	fun saveAllPortfolios() {
		portfolios.values.forEach { portfolio ->
			databaseManager.savePortfolio(portfolio)
		}
	}
}
