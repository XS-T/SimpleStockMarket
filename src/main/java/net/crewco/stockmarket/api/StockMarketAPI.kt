package net.crewco.stockmarket.api

import net.crewco.stockmarket.data.Stock
import net.crewco.stockmarket.data.StockTransaction
import net.crewco.stockmarket.data.Portfolio
import net.crewco.stockmarket.events.StockPriceChangeEvent
import net.crewco.stockmarket.events.StockTransactionEvent
import net.crewco.stockmarket.StockMarketPlugin
import net.crewco.stockmarket.StockMarketPlugin.Companion.marketManager
import net.crewco.stockmarket.StockMarketPlugin.Companion.stockManager
import net.crewco.stockmarket.StockMarketPlugin.Companion.portfolioManager
import net.crewco.stockmarket.StockMarketPlugin.Companion.bankingAPI

import org.bukkit.OfflinePlayer
import java.math.BigDecimal
import java.util.*

/**
 * Main API class for StockMarket Plugin
 * Use this to integrate with business plugins
 */
class StockMarketAPI(private val plugin: StockMarketPlugin) {

	// ============= STOCK MANAGEMENT =============

	/**
	 * Get all available stocks
	 */
	fun getAllStocks(): List<Stock> {
		return stockManager.getAllStocks()
	}

	/**
	 * Get a specific stock by symbol
	 */
	fun getStock(symbol: String): Stock? {
		return stockManager.getStock(symbol)
	}

	/**
	 * Create a new stock (for business plugins)
	 */
	fun createStock(
		symbol: String,
		name: String,
		initialPrice: BigDecimal,
		category: String = "CUSTOM",
		volatility: Double = 0.05
	): Boolean {
		return stockManager.createStock(symbol, name, initialPrice, category, volatility)
	}

	/**
	 * Update stock price manually (for business plugins)
	 */
	fun updateStockPrice(symbol: String, newPrice: BigDecimal, reason: String = "Manual"): Boolean {
		return stockManager.updateStockPrice(symbol, newPrice, reason)
	}

	/**
	 * Get stock price history
	 */
	fun getStockHistory(symbol: String, days: Int = 7): List<Stock.PriceHistory> {
		return stockManager.getStockHistory(symbol, days)
	}

	// ============= PORTFOLIO MANAGEMENT =============

	/**
	 * Get player's portfolio
	 */
	fun getPlayerPortfolio(player: OfflinePlayer): Portfolio? {
		return portfolioManager.getPortfolio(player.uniqueId)
	}

	/**
	 * Get player's balance (from Banking Plugin)
	 */
	fun getPlayerBalance(player: OfflinePlayer): BigDecimal {
		return bankingAPI?.getBalance(player) ?: BigDecimal.ZERO
	}

	/**
	 * Add money to player's balance (through Banking Plugin)
	 */
	fun addPlayerBalance(player: OfflinePlayer, amount: BigDecimal): Boolean {
		return bankingAPI?.deposit(player, amount, "Stock Market Credit") ?: false
	}

	/**
	 * Remove money from player's balance (through Banking Plugin)
	 */
	fun removePlayerBalance(player: OfflinePlayer, amount: BigDecimal): Boolean {
		return bankingAPI?.withdraw(player, amount, "Stock Market Transaction") ?: false
	}

	/**
	 * Get player's holdings for a specific stock
	 */
	fun getPlayerHoldings(player: OfflinePlayer, symbol: String): Int {
		return portfolioManager.getHoldings(player.uniqueId, symbol)
	}

	/**
	 * Get all player's holdings
	 */
	fun getAllPlayerHoldings(player: OfflinePlayer): Map<String, Int> {
		return portfolioManager.getAllHoldings(player.uniqueId)
	}

	// ============= TRADING OPERATIONS =============

	/**
	 * Buy stocks for a player
	 */
	fun buyStock(player: OfflinePlayer, symbol: String, quantity: Int): StockTransaction? {
		return marketManager.buyStock(player.uniqueId, symbol, quantity)
	}

	/**
	 * Sell stocks for a player
	 */
	fun sellStock(player: OfflinePlayer, symbol: String, quantity: Int): StockTransaction? {
		return marketManager.sellStock(player.uniqueId, symbol, quantity)
	}

	/**
	 * Get transaction history for a player
	 */
	fun getTransactionHistory(player: OfflinePlayer, limit: Int = 10): List<StockTransaction> {
		return marketManager.getTransactionHistory(player.uniqueId, limit)
	}

	/**
	 * Calculate portfolio value
	 */
	fun calculatePortfolioValue(player: OfflinePlayer): BigDecimal {
		return portfolioManager.calculateTotalValue(player.uniqueId)
	}

	// ============= MARKET OPERATIONS =============

	/**
	 * Get market cap for a stock
	 */
	fun getMarketCap(symbol: String): BigDecimal {
		return marketManager.getMarketCap(symbol)
	}

	/**
	 * Get top performing stocks
	 */
	fun getTopPerformers(limit: Int = 5): List<Stock> {
		return marketManager.getTopPerformers(limit)
	}

	/**
	 * Get worst performing stocks
	 */
	fun getWorstPerformers(limit: Int = 5): List<Stock> {
		return marketManager.getWorstPerformers(limit)
	}

	/**
	 * Get market status
	 */
	fun isMarketOpen(): Boolean {
		return marketManager.isMarketOpen()
	}

	/**
	 * Set market status (for business plugins)
	 */
	fun setMarketOpen(open: Boolean): Boolean {
		return marketManager.setMarketOpen(open)
	}

	// ============= BUSINESS INTEGRATION =============

	/**
	 * Create a business stock tied to a company
	 */
	fun createBusinessStock(
		businessId: String,
		businessName: String,
		initialPrice: BigDecimal,
		sharesIssued: Long = 1000000L
	): Boolean {
		val symbol = "BIZ_${businessId.take(6).uppercase()}"
		return stockManager.createBusinessStock(
			symbol, businessName, initialPrice, businessId, sharesIssued
		)
	}

	/**
	 * Update business performance (affects stock price)
	 */
	fun updateBusinessPerformance(
		businessId: String,
		revenue: BigDecimal,
		profit: BigDecimal,
		employeeCount: Int
	): Boolean {
		return marketManager.updateBusinessMetrics(businessId, revenue, profit, employeeCount)
	}

	/**
	 * Pay dividends to shareholders
	 */
	fun payDividends(symbol: String, dividendPerShare: BigDecimal): Boolean {
		return marketManager.payDividends(symbol, dividendPerShare)
	}

	/**
	 * Get shareholders of a stock
	 */
	fun getShareholders(symbol: String): Map<UUID, Int> {
		return portfolioManager.getShareholders(symbol)
	}

	// ============= EVENTS & CALLBACKS =============

	/**
	 * Register a callback for stock price changes
	 */
	fun onStockPriceChange(callback: (StockPriceChangeEvent) -> Unit) {
		marketManager.registerPriceChangeCallback(callback)
	}

	/**
	 * Register a callback for transactions
	 */
	fun onStockTransaction(callback: (StockTransactionEvent) -> Unit) {
		marketManager.registerTransactionCallback(callback)
	}

	// ============= UTILITY METHODS =============

	/**
	 * Format currency for display
	 */
	fun formatCurrency(amount: BigDecimal): String {
		return marketManager.formatCurrency(amount)
	}

	/**
	 * Calculate percentage change
	 */
	fun calculatePercentageChange(oldValue: BigDecimal, newValue: BigDecimal): Double {
		return marketManager.calculatePercentageChange(oldValue, newValue)
	}
}