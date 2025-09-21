package net.crewco.stockmarket

import net.crewco.common.CrewCoPlugin
import net.crewco.stockmarket.api.StockMarketAPI
import net.crewco.stockmarket.commands.stock
import net.crewco.stockmarket.database.DatabaseManager
import net.crewco.stockmarket.listeners.PlayerListener
import net.crewco.stockmarket.managers.MarketManager
import net.crewco.stockmarket.managers.PlayerPortfolioManager
import net.crewco.stockmarket.managers.StockManager
import net.crewco.stockmarket.task.MarketUpdateTask
import org.bukkit.plugin.ServicePriority

class StockMarketPlugin : CrewCoPlugin() {
	companion object{
		lateinit var plugin:StockMarketPlugin
			private set
		lateinit var databaseManager: DatabaseManager
		lateinit var stockManager: StockManager
		lateinit var marketManager: MarketManager
		lateinit var portfolioManager: PlayerPortfolioManager
		lateinit var api:StockMarketAPI

	}
	override suspend fun onEnableAsync() {
		super.onEnableAsync()

		//Inits
		plugin = this

		//Config
		plugin.config.options().copyDefaults()
		plugin.saveDefaultConfig()

		// Initialize database
		databaseManager = DatabaseManager(this)
		databaseManager.initialize()

		// Initialize managers
		stockManager = StockManager(this)
		marketManager = MarketManager(this)
		portfolioManager = PlayerPortfolioManager(this)

		// Initialize API
		api = StockMarketAPI(this)

		// Register API service
		server.servicesManager.register(StockMarketAPI::class.java, api,this,ServicePriority.Normal)

		// Register commands
		registerCommands(stock::class)

		// Register Listeners
		registerListeners(PlayerListener::class)

		// Start market update task
		MarketUpdateTask(this).runTaskTimer(this, 0L, config.getLong("market.update-interval", 1200L))

		logger.info("StockMarket Plugin has been enabled!")
		logger.info("API registered and available for business plugins")



	}

	override suspend fun onDisableAsync() {
		super.onDisableAsync()

		databaseManager.close()
		logger.info("StockMarket Plugin has been disabled!")
	}

}