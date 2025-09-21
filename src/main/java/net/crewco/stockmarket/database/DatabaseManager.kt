package net.crewco.stockmarket.database

import net.crewco.stockmarket.StockMarketPlugin
import net.crewco.stockmarket.data.*
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.math.BigDecimal
import java.sql.*
import java.util.*

class DatabaseManager(private val plugin: StockMarketPlugin) {

	private var dataSource: HikariDataSource? = null

	fun initialize() {
		setupDatabase()
		createTables()
	}

	private fun setupDatabase() {
		val config = HikariConfig()
		val dbType = plugin.config.getString("database.type", "sqlite")

		when (dbType?.lowercase()) {
			"mysql" -> {
				config.jdbcUrl = "jdbc:mysql://${plugin.config.getString("database.host")}:${plugin.config.getInt("database.port")}/${plugin.config.getString("database.name")}"
				config.username = plugin.config.getString("database.username")
				config.password = plugin.config.getString("database.password")
				config.driverClassName = "com.mysql.cj.jdbc.Driver"
			}
			"postgresql" -> {
				config.jdbcUrl = "jdbc:postgresql://${plugin.config.getString("database.host")}:${plugin.config.getInt("database.port")}/${plugin.config.getString("database.name")}"
				config.username = plugin.config.getString("database.username")
				config.password = plugin.config.getString("database.password")
				config.driverClassName = "org.postgresql.Driver"
			}
			else -> {
				// SQLite (default)
				config.jdbcUrl = "jdbc:sqlite:${plugin.dataFolder}/stockmarket.db"
				config.driverClassName = "org.sqlite.JDBC"
			}
		}

		config.maximumPoolSize = 10
		config.minimumIdle = 5
		config.connectionTimeout = 30000
		config.idleTimeout = 600000
		config.maxLifetime = 1800000

		dataSource = HikariDataSource(config)
	}

	private fun createTables() {
		getConnection()?.use { connection ->
			// Stocks table
			connection.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS stocks (
                    symbol VARCHAR(10) PRIMARY KEY,
                    name VARCHAR(100) NOT NULL,
                    current_price DECIMAL(15,4) NOT NULL,
                    previous_price DECIMAL(15,4) NOT NULL,
                    category VARCHAR(20) NOT NULL,
                    volatility DOUBLE NOT NULL,
                    market_cap DECIMAL(20,4) DEFAULT 0,
                    volume BIGINT DEFAULT 0,
                    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    business_id VARCHAR(50),
                    shares_issued BIGINT DEFAULT 1000000,
                    dividend_yield DOUBLE DEFAULT 0
                )
            """)

			// Portfolios table
			connection.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS portfolios (
                    player_id VARCHAR(36) PRIMARY KEY,
                    balance DECIMAL(15,4) NOT NULL DEFAULT 0,
                    total_invested DECIMAL(15,4) DEFAULT 0,
                    total_realized DECIMAL(15,4) DEFAULT 0,
                    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """)

			// Holdings table
			connection.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS holdings (
                    player_id VARCHAR(36) NOT NULL,
                    symbol VARCHAR(10) NOT NULL,
                    quantity INT NOT NULL,
                    PRIMARY KEY (player_id, symbol),
                    FOREIGN KEY (symbol) REFERENCES stocks(symbol) ON DELETE CASCADE
                )
            """)

			// Transactions table
			connection.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS transactions (
                    id VARCHAR(36) PRIMARY KEY,
                    player_id VARCHAR(36) NOT NULL,
                    symbol VARCHAR(10) NOT NULL,
                    type VARCHAR(10) NOT NULL,
                    quantity INT NOT NULL,
                    price_per_share DECIMAL(15,4) NOT NULL,
                    total_amount DECIMAL(15,4) NOT NULL,
                    fees DECIMAL(15,4) DEFAULT 0,
                    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (symbol) REFERENCES stocks(symbol) ON DELETE CASCADE
                )
            """)

			// Stock history table
			connection.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS stock_history (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    symbol VARCHAR(10) NOT NULL,
                    price DECIMAL(15,4) NOT NULL,
                    volume BIGINT NOT NULL,
                    reason VARCHAR(50) NOT NULL,
                    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (symbol) REFERENCES stocks(symbol) ON DELETE CASCADE
                )
            """)

			// Business metrics table
			connection.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS business_metrics (
                    business_id VARCHAR(50) PRIMARY KEY,
                    revenue DECIMAL(15,4) NOT NULL,
                    profit DECIMAL(15,4) NOT NULL,
                    employee_count INT NOT NULL,
                    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """)
		}
	}

	// ============= STOCK OPERATIONS =============

	fun saveStock(stock: Stock) {
		getConnection()?.use { connection ->
			val sql = """
                REPLACE INTO stocks (symbol, name, current_price, previous_price, category, volatility, 
                                   market_cap, volume, last_updated, business_id, shares_issued, dividend_yield)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """
			connection.prepareStatement(sql).use { stmt ->
				stmt.setString(1, stock.symbol)
				stmt.setString(2, stock.name)
				stmt.setBigDecimal(3, stock.currentPrice)
				stmt.setBigDecimal(4, stock.previousPrice)
				stmt.setString(5, stock.category.name)
				stmt.setDouble(6, stock.volatility)
				stmt.setBigDecimal(7, stock.marketCap)
				stmt.setLong(8, stock.volume)
				stmt.setTimestamp(9, Timestamp.valueOf(stock.lastUpdated))
				stmt.setString(10, stock.businessId)
				stmt.setLong(11, stock.sharesIssued)
				stmt.setDouble(12, stock.dividendYield)
				stmt.executeUpdate()
			}
		}
	}

	fun loadAllStocks(): List<Stock> {
		val stocks = mutableListOf<Stock>()
		getConnection()?.use { connection ->
			val sql = "SELECT * FROM stocks"
			connection.createStatement().executeQuery(sql).use { rs ->
				while (rs.next()) {
					stocks.add(Stock(
						symbol = rs.getString("symbol"),
						name = rs.getString("name"),
						currentPrice = rs.getBigDecimal("current_price"),
						previousPrice = rs.getBigDecimal("previous_price"),
						category = StockCategory.valueOf(rs.getString("category")),
						volatility = rs.getDouble("volatility"),
						marketCap = rs.getBigDecimal("market_cap") ?: BigDecimal.ZERO,
						volume = rs.getLong("volume"),
						lastUpdated = rs.getTimestamp("last_updated").toLocalDateTime(),
						businessId = rs.getString("business_id"),
						sharesIssued = rs.getLong("shares_issued"),
						dividendYield = rs.getDouble("dividend_yield")
					))
				}
			}
		}
		return stocks
	}

	// ============= PORTFOLIO OPERATIONS =============

	fun savePortfolio(portfolio: Portfolio) {
		getConnection()?.use { connection ->
			// Save portfolio info
			val portfolioSql = """
                REPLACE INTO portfolios (player_id, balance, total_invested, total_realized, last_updated)
                VALUES (?, ?, ?, ?, ?)
            """
			connection.prepareStatement(portfolioSql).use { stmt ->
				stmt.setString(1, portfolio.playerId.toString())
				stmt.setBigDecimal(2, portfolio.balance)
				stmt.setBigDecimal(3, portfolio.totalInvested)
				stmt.setBigDecimal(4, portfolio.totalRealized)
				stmt.setTimestamp(5, Timestamp.valueOf(portfolio.lastUpdated))
				stmt.executeUpdate()
			}

			// Clear existing holdings
			val deleteSql = "DELETE FROM holdings WHERE player_id = ?"
			connection.prepareStatement(deleteSql).use { stmt ->
				stmt.setString(1, portfolio.playerId.toString())
				stmt.executeUpdate()
			}

			// Save holdings
			if (portfolio.holdings.isNotEmpty()) {
				val holdingsSql = "INSERT INTO holdings (player_id, symbol, quantity) VALUES (?, ?, ?)"
				connection.prepareStatement(holdingsSql).use { stmt ->
					portfolio.holdings.forEach { (symbol, quantity) ->
						stmt.setString(1, portfolio.playerId.toString())
						stmt.setString(2, symbol)
						stmt.setInt(3, quantity)
						stmt.addBatch()
					}
					stmt.executeBatch()
				}
			}
		}
	}

	fun loadAllPortfolios(): List<Portfolio> {
		val portfolios = mutableListOf<Portfolio>()
		getConnection()?.use { connection ->
			val sql = """
                SELECT p.*, h.symbol, h.quantity 
                FROM portfolios p 
                LEFT JOIN holdings h ON p.player_id = h.player_id
            """

			val portfolioMap = mutableMapOf<UUID, Portfolio>()

			connection.createStatement().executeQuery(sql).use { rs ->
				while (rs.next()) {
					val playerId = UUID.fromString(rs.getString("player_id"))

					val portfolio = portfolioMap.getOrPut(playerId) {
						Portfolio(
							playerId = playerId,
							balance = rs.getBigDecimal("balance"),
							totalInvested = rs.getBigDecimal("total_invested") ?: BigDecimal.ZERO,
							totalRealized = rs.getBigDecimal("total_realized") ?: BigDecimal.ZERO,
							lastUpdated = rs.getTimestamp("last_updated").toLocalDateTime()
						)
					}

					val symbol = rs.getString("symbol")
					if (symbol != null) {
						portfolio.holdings[symbol] = rs.getInt("quantity")
					}
				}
			}

			portfolios.addAll(portfolioMap.values)
		}
		return portfolios
	}

	// ============= TRANSACTION OPERATIONS =============

	fun saveTransaction(transaction: StockTransaction) {
		getConnection()?.use { connection ->
			val sql = """
                INSERT INTO transactions (id, player_id, symbol, type, quantity, price_per_share, 
                                        total_amount, fees, timestamp)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """
			connection.prepareStatement(sql).use { stmt ->
				stmt.setString(1, transaction.id.toString())
				stmt.setString(2, transaction.playerId.toString())
				stmt.setString(3, transaction.symbol)
				stmt.setString(4, transaction.type.name)
				stmt.setInt(5, transaction.quantity)
				stmt.setBigDecimal(6, transaction.pricePerShare)
				stmt.setBigDecimal(7, transaction.totalAmount)
				stmt.setBigDecimal(8, transaction.fees)
				stmt.setTimestamp(9, Timestamp.valueOf(transaction.timestamp))
				stmt.executeUpdate()
			}
		}
	}

	fun getTransactionHistory(playerId: UUID, limit: Int): List<StockTransaction> {
		val transactions = mutableListOf<StockTransaction>()
		getConnection()?.use { connection ->
			val sql = """
                SELECT * FROM transactions 
                WHERE player_id = ? 
                ORDER BY timestamp DESC 
                LIMIT ?
            """
			connection.prepareStatement(sql).use { stmt ->
				stmt.setString(1, playerId.toString())
				stmt.setInt(2, limit)
				stmt.executeQuery().use { rs ->
					while (rs.next()) {
						transactions.add(StockTransaction(
							id = UUID.fromString(rs.getString("id")),
							playerId = UUID.fromString(rs.getString("player_id")),
							symbol = rs.getString("symbol"),
							type = TransactionType.valueOf(rs.getString("type")),
							quantity = rs.getInt("quantity"),
							pricePerShare = rs.getBigDecimal("price_per_share"),
							totalAmount = rs.getBigDecimal("total_amount"),
							timestamp = rs.getTimestamp("timestamp").toLocalDateTime(),
							fees = rs.getBigDecimal("fees") ?: BigDecimal.ZERO
						))
					}
				}
			}
		}
		return transactions
	}

	// ============= STOCK HISTORY OPERATIONS =============

	fun saveStockHistory(symbol: String, price: BigDecimal, volume: Long, reason: String) {
		getConnection()?.use { connection ->
			val sql = "INSERT INTO stock_history (symbol, price, volume, reason) VALUES (?, ?, ?, ?)"
			connection.prepareStatement(sql).use { stmt ->
				stmt.setString(1, symbol)
				stmt.setBigDecimal(2, price)
				stmt.setLong(3, volume)
				stmt.setString(4, reason)
				stmt.executeUpdate()
			}
		}
	}

	fun getStockHistory(symbol: String, days: Int): List<Stock.PriceHistory> {
		val history = mutableListOf<Stock.PriceHistory>()
		getConnection()?.use { connection ->
			val sql = """
                SELECT * FROM stock_history 
                WHERE symbol = ? AND timestamp >= DATE_SUB(NOW(), INTERVAL ? DAY)
                ORDER BY timestamp DESC
            """
			connection.prepareStatement(sql).use { stmt ->
				stmt.setString(1, symbol)
				stmt.setInt(2, days)
				stmt.executeQuery().use { rs ->
					while (rs.next()) {
						history.add(Stock.PriceHistory(
							timestamp = rs.getTimestamp("timestamp").toLocalDateTime(),
							price = rs.getBigDecimal("price"),
							volume = rs.getLong("volume"),
							reason = rs.getString("reason")
						))
					}
				}
			}
		}
		return history
	}

	// ============= BUSINESS METRICS OPERATIONS =============

	fun saveBusinessMetrics(metrics: BusinessMetrics) {
		getConnection()?.use { connection ->
			val sql = """
                REPLACE INTO business_metrics (business_id, revenue, profit, employee_count, last_updated)
                VALUES (?, ?, ?, ?, ?)
            """
			connection.prepareStatement(sql).use { stmt ->
				stmt.setString(1, metrics.businessId)
				stmt.setBigDecimal(2, metrics.revenue)
				stmt.setBigDecimal(3, metrics.profit)
				stmt.setInt(4, metrics.employeeCount)
				stmt.setTimestamp(5, Timestamp.valueOf(metrics.lastUpdated))
				stmt.executeUpdate()
			}
		}
	}

	fun getBusinessMetrics(businessId: String): BusinessMetrics? {
		getConnection()?.use { connection ->
			val sql = "SELECT * FROM business_metrics WHERE business_id = ?"
			connection.prepareStatement(sql).use { stmt ->
				stmt.setString(1, businessId)
				stmt.executeQuery().use { rs ->
					if (rs.next()) {
						return BusinessMetrics(
							businessId = rs.getString("business_id"),
							revenue = rs.getBigDecimal("revenue"),
							profit = rs.getBigDecimal("profit"),
							employeeCount = rs.getInt("employee_count"),
							lastUpdated = rs.getTimestamp("last_updated").toLocalDateTime()
						)
					}
				}
			}
		}
		return null
	}

	// ============= UTILITY METHODS =============

	private fun getConnection(): Connection? {
		return try {
			dataSource?.connection
		} catch (e: SQLException) {
			plugin.logger.severe("Failed to get database connection: ${e.message}")
			null
		}
	}

	fun close() {
		dataSource?.close()
	}

	/**
	 * Execute database cleanup (remove old data)
	 */
	fun cleanup() {
		getConnection()?.use { connection ->
			// Remove stock history older than 90 days
			val cleanupHistorySql = "DELETE FROM stock_history WHERE timestamp < DATE_SUB(NOW(), INTERVAL 90 DAY)"
			connection.createStatement().execute(cleanupHistorySql)

			// Remove old transactions (keep last 1000 per player)
			val cleanupTransactionsSql = """
                DELETE t1 FROM transactions t1
                INNER JOIN (
                    SELECT player_id, 
                           ROW_NUMBER() OVER (PARTITION BY player_id ORDER BY timestamp DESC) as rn
                    FROM transactions
                ) t2 ON t1.player_id = t2.player_id
                WHERE t2.rn > 1000
            """
			try {
				connection.createStatement().execute(cleanupTransactionsSql)
			} catch (e: SQLException) {
				// Ignore if database doesn't support this syntax
				plugin.logger.info("Transaction cleanup not supported on this database type")
			}

			plugin.logger.info("Database cleanup completed")
		}
	}

	/**
	 * Get database statistics
	 */
	fun getDatabaseStats(): Map<String, Int> {
		val stats = mutableMapOf<String, Int>()
		getConnection()?.use { connection ->
			listOf("stocks", "portfolios", "holdings", "transactions", "stock_history", "business_metrics").forEach { table ->
				try {
					val sql = "SELECT COUNT(*) as count FROM $table"
					connection.createStatement().executeQuery(sql).use { rs ->
						if (rs.next()) {
							stats[table] = rs.getInt("count")
						}
					}
				} catch (e: SQLException) {
					stats[table] = 0
				}
			}
		}
		return stats
	}
}