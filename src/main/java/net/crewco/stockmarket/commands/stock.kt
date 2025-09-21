package net.crewco.stockmarket.commands

import net.crewco.stockmarket.StockMarketPlugin.Companion.databaseManager
import net.crewco.stockmarket.StockMarketPlugin.Companion.marketManager
import net.crewco.stockmarket.StockMarketPlugin.Companion.plugin
import net.crewco.stockmarket.StockMarketPlugin.Companion.portfolioManager
import net.crewco.stockmarket.StockMarketPlugin.Companion.stockManager
import net.crewco.stockmarket.data.StockCategory
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.ComponentBuilder
import net.md_5.bungee.api.chat.HoverEvent
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.incendo.cloud.annotations.Argument
import org.incendo.cloud.annotations.Command
import org.incendo.cloud.annotations.CommandDescription
import org.incendo.cloud.annotations.Permission
import org.incendo.cloud.annotations.suggestion.Suggestions
import org.incendo.cloud.context.CommandContext
import java.math.BigDecimal
import java.util.stream.Stream

class stock {
	@Command("stock <args>")
	@CommandDescription("Is the commmands for stocks")
	@Permission("stock.command.use")
	fun onExecute(player:Player,@Argument("args", suggestions = "args") args:Array<String>){
		if (args.isEmpty()){
			sendHelp(player)
			return
		}

		when (args[0].lowercase()){
			"list" -> handleList(player,args)
			"info" -> handleInfo(player,args)
			"buy" -> handleBuy(player,args)
			"sell" -> handleSell(player,args)
			"portfolio" -> handlePortfolio(player,args)
			"balance" -> handleBalance(player,args)
			"history" -> handleHistory(player,args)
			"top" -> handleTop(player,args)
			"market" -> handleMarket(player)
			"search" -> handleSearch(player,args)
			"chart" -> handleChart(player,args)
			"admin" -> if (player.hasPermission("stock.command.admin.use")){
				handleAdmin(player,args)
			}else{
				player.sendMessage(ChatColor.translateAlternateColorCodes('&',"&7[&6System&]> &4You Do not have permission to run this command"))
			}
			"help" -> sendHelp(player)
			else -> sendHelp(player)
		}
	}

	private fun handleList(player: Player, args: Array<String>) {
		val category = if (args.size > 1) {
			try {
				StockCategory.valueOf(args[1].uppercase())
			} catch (e: IllegalArgumentException) {
				player.sendMessage("§cInvalid category! Available: ${StockCategory.entries.joinToString(", ")}")
				return
			}
		} else null

		val stocks = if (category != null) {
			stockManager.getStocksByCategory(category)
		} else {
			stockManager.getAllStocks()
		}

		if (stocks.isEmpty()) {
			player.sendMessage("§cNo stocks found!")
			return
		}

		player.sendMessage("§6=== Stock Market ${category?.displayName ?: "All Stocks"} ===")
		player.sendMessage("§7Symbol | Name | Price | Change | Volume")

		stocks.sortedBy { it.symbol }.forEach { stock ->
			val change = stock.getPercentageChange()
			val changeColor = when {
				change > 0 -> "§a"
				change < 0 -> "§c"
				else -> "§7"
			}

			val message = TextComponent("§f${stock.symbol} §8| §7${stock.name.take(20)} §8| " +
					"§6${marketManager.formatCurrency(stock.currentPrice)} §8| " +
					"$changeColor${String.format("%.2f", change)}% §8| §7${stock.volume}")

			message.clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, "/stock info ${stock.symbol}")
			message.hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT,
				ComponentBuilder("§7Click to view detailed info").create())

			player.spigot().sendMessage(message)
		}
	}

	private fun handleInfo(player: Player, args: Array<String>) {
		if (args.size < 2) {
			player.sendMessage("§cUsage: /stock info <symbol>")
			return
		}

		val stock = stockManager.getStock(args[1])
		if (stock == null) {
			player.sendMessage("§cStock not found!")
			return
		}

		val change = stock.getPercentageChange()
		val changeColor = when {
			change > 0 -> "§a"
			change < 0 -> "§c"
			else -> "§7"
		}

		val marketCap = marketManager.getMarketCap(stock.symbol)

		player.sendMessage("§6=== ${stock.symbol} - ${stock.name} ===")
		player.sendMessage("§7Category: §f${stock.category.displayName}")
		player.sendMessage("§7Current Price: §6${marketManager.formatCurrency(stock.currentPrice)}")
		player.sendMessage("§7Previous Price: §6${marketManager.formatCurrency(stock.previousPrice)}")
		player.sendMessage("§7Price Change: $changeColor${String.format("%.2f", change)}% (${marketManager.formatCurrency(stock.getPriceChange())})")
		player.sendMessage("§7Market Cap: §6${marketManager.formatCurrency(marketCap)}")
		player.sendMessage("§7Volume: §f${stock.volume}")
		player.sendMessage("§7Volatility: §f${String.format("%.2f", stock.volatility * 100)}%")
		player.sendMessage("§7Last Updated: §f${stock.lastUpdated}")

		if (stock.businessId != null) {
			player.sendMessage("§7Business ID: §f${stock.businessId}")
		}

		if (stock.dividendYield > 0) {
			player.sendMessage("§7Dividend Yield: §a${String.format("%.2f", stock.dividendYield)}%")
		}

		// Quick buy/sell buttons for players
		val buyButton = TextComponent("§a[BUY 10]")
		buyButton.clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, "/stock buy ${stock.symbol} 10")
		buyButton.hoverEvent = HoverEvent(
			HoverEvent.Action.SHOW_TEXT,
			ComponentBuilder("§7Buy 10 shares").create()
		)

		val sellButton = TextComponent("§c[SELL 10]")
		sellButton.clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, "/stock sell ${stock.symbol} 10")
		sellButton.hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT,
			ComponentBuilder("§7Sell 10 shares").create())

		val message = ComponentBuilder("§7Quick Actions: ").append(buyButton).append(" ").append(sellButton).create()
		player.spigot().sendMessage(*message)
	}

	private fun handleBuy(player: Player, args: Array<String>) {

		if (args.size < 3) {
			player.sendMessage("§cUsage: /stock buy <symbol> <quantity>")
			return
		}

		val symbol = args[1]
		val quantity = args[2].toIntOrNull()

		if (quantity == null || quantity <= 0) {
			player.sendMessage("§cInvalid quantity!")
			return
		}

		if (!marketManager.isMarketOpen()) {
			player.sendMessage("§cMarket is currently closed!")
			return
		}

		val transaction = marketManager.buyStock(player.uniqueId, symbol, quantity)
		if (transaction != null) {
			player.sendMessage("§aBought $quantity shares of $symbol for ${marketManager.formatCurrency(transaction.totalAmount)}")
			if (transaction.fees > BigDecimal.ZERO) {
				player.sendMessage("§7Trading fees: ${marketManager.formatCurrency(transaction.fees)}")
			}
		} else {
			player.sendMessage("§cTransaction failed! Check your balance and try again.")
		}
	}

	private fun handleSell(player: Player, args: Array<String>) {
		if (player !is Player) {
			player.sendMessage("§cOnly players can trade stocks!")
			return
		}

		if (args.size < 3) {
			player.sendMessage("§cUsage: /stock sell <symbol> <quantity>")
			return
		}

		val symbol = args[1]
		val quantity = args[2].toIntOrNull()

		if (quantity == null || quantity <= 0) {
			player.sendMessage("§cInvalid quantity!")
			return
		}

		if (!marketManager.isMarketOpen()) {
			player.sendMessage("§cMarket is currently closed!")
			return
		}

		val transaction = marketManager.sellStock(player.uniqueId, symbol, quantity)
		if (transaction != null) {
			player.sendMessage("§aSold $quantity shares of $symbol for ${marketManager.formatCurrency(transaction.totalAmount)}")
			if (transaction.fees > BigDecimal.ZERO) {
				player.sendMessage("§7Trading fees: ${marketManager.formatCurrency(transaction.fees)}")
			}
		} else {
			player.sendMessage("§cTransaction failed! Make sure you own enough shares.")
		}
	}

	private fun handlePortfolio(player: Player, args: Array<String>) {
		if (player !is Player) {
			player.sendMessage("§cOnly players can view portfolios!")
			return
		}

		val player = if (args.size > 1 && player.hasPermission("stockmarket.admin")) {
			Bukkit.getPlayer(args[1])
		} else {
			player
		} ?: run {
			player.sendMessage("§cPlayer not found!")
			return
		}

		val metrics = portfolioManager.getPortfolioMetrics(player.uniqueId)
		val holdings = portfolioManager.getAllHoldings(player.uniqueId)

		player.sendMessage("§6=== ${player.name}'s Portfolio ===")
		player.sendMessage("§7Cash Balance: §6${marketManager.formatCurrency(metrics.cashBalance)}")
		player.sendMessage("§7Total Value: §6${marketManager.formatCurrency(metrics.totalValue)}")
		player.sendMessage("§7Total P&L: ${if (metrics.totalPnL >= BigDecimal.ZERO) "§a" else "§c"}${marketManager.formatCurrency(metrics.totalPnL)}")
		player.sendMessage("§7ROI: ${if (metrics.roi >= 0) "§a" else "§c"}${String.format("%.2f", metrics.roi)}%")
		player.sendMessage("§7Diversity: §f${metrics.diversity} different stocks")
		player.sendMessage("§7Total Shares: §f${metrics.totalStocks}")

		if (holdings.isNotEmpty()) {
			player.sendMessage("§6=== Holdings ===")
			holdings.forEach { (symbol, quantity) ->
				val stock = stockManager.getStock(symbol)
				if (stock != null) {
					val value = stock.currentPrice * BigDecimal.valueOf(quantity.toLong())
					player.sendMessage("§f$symbol §8- §7$quantity shares §8- §6$marketManager.formatCurrency(value)}")
				}
			}
		}
	}

	private fun handleBalance(player: Player, args: Array<String>) {
		if (player !is Player) {
			player.sendMessage("§cOnly players can check balance!")
			return
		}

		val balance = portfolioManager.getBalance(player.uniqueId)
		player.sendMessage("§7Your balance: §6${marketManager.formatCurrency(balance)}")
	}

	private fun handleHistory(player: Player, args: Array<String>) {
		if (player !is Player) {
			player.sendMessage("§cOnly players can view transaction history!")
			return
		}

		val limit = if (args.size > 1) args[1].toIntOrNull() ?: 10 else 10
		val transactions = marketManager.getTransactionHistory(player.uniqueId, limit)

		if (transactions.isEmpty()) {
			player.sendMessage("§7No transactions found.")
			return
		}

		player.sendMessage("§6=== Transaction History ===")
		transactions.forEach { transaction ->
			val typeColor = when (transaction.type.name) {
				"BUY" -> "§c"
				"SELL" -> "§a"
				else -> "§6"
			}

			player.sendMessage("$typeColor${transaction.type.displayName} §f${transaction.quantity} ${transaction.symbol} " +
					"§7@ §6${marketManager.formatCurrency(transaction.pricePerShare)} " +
					"§7- ${transaction.timestamp.toLocalDate()}")
		}
	}

	private fun handleTop(player: Player, args: Array<String>) {
		val type = if (args.size > 1) args[1].lowercase() else "performers"

		when (type) {
			"performers", "gainers" -> {
				val stocks = marketManager.getTopPerformers(10)
				player.sendMessage("§6=== Top Performers ===")
				stocks.forEachIndexed { index, stock ->
					player.sendMessage("§7${index + 1}. §f${stock.symbol} §a+${String.format("%.2f", stock.getPercentageChange())}%")
				}
			}
			"losers" -> {
				val stocks = marketManager.getWorstPerformers(10)
				player.sendMessage("§6=== Worst Performers ===")
				stocks.forEachIndexed { index, stock ->
					player.sendMessage("§7${index + 1}. §f${stock.symbol} §c${String.format("%.2f", stock.getPercentageChange())}%")
				}
			}
			"portfolios" -> {
				if (!player.hasPermission("stockmarket.admin")) {
					player.sendMessage("§cNo permission!")
					return
				}
				val portfolios = portfolioManager.getTopPortfolios(10)
				player.sendMessage("§6=== Top Portfolios ===")
				portfolios.forEachIndexed { index, (playerId, value) ->
					val playerName = Bukkit.getOfflinePlayer(playerId).name ?: "Unknown"
					player.sendMessage("§7${index + 1}. §f$playerName §6${marketManager.formatCurrency(value)}")
				}
			}
		}
	}

	private fun handleMarket(player: Player) {
		val stocks = stockManager.getAllStocks()
		val marketCap = stocks.sumOf { marketManager.getMarketCap(it.symbol) }
		val totalVolume = stocks.sumOf { it.volume }
		val isOpen = marketManager.isMarketOpen()

		player.sendMessage("§6=== Market Overview ===")
		player.sendMessage("§7Status: ${if (isOpen) "§aOPEN" else "§cCLOSED"}")
		player.sendMessage("§7Total Stocks: §f${stocks.size}")
		player.sendMessage("§7Market Cap: §6${marketManager.formatCurrency(marketCap)}")
		player.sendMessage("§7Total Volume: §f$totalVolume")

		val topGainer = marketManager.getTopPerformers(1).firstOrNull()
		val topLoser = marketManager.getWorstPerformers(1).firstOrNull()

		if (topGainer != null) {
			player.sendMessage("§7Top Gainer: §a${topGainer.symbol} +${String.format("%.2f", topGainer.getPercentageChange())}%")
		}

		if (topLoser != null) {
			player.sendMessage("§7Top Loser: §c${topLoser.symbol} ${String.format("%.2f", topLoser.getPercentageChange())}%")
		}
	}

	private fun handleSearch(player: Player, args: Array<String>) {
		if (args.size < 2) {
			player.sendMessage("§cUsage: /stock search <query>")
			return
		}

		val query = args.drop(1).joinToString(" ")
		val results = stockManager.searchStocks(query)

		if (results.isEmpty()) {
			player.sendMessage("§cNo stocks found matching '$query'")
			return
		}

		player.sendMessage("§6=== Search Results for '$query' ===")
		results.take(10).forEach { stock ->
			val change = stock.getPercentageChange()
			val changeColor = if (change > 0) "§a" else if (change < 0) "§c" else "§7"
			player.sendMessage("§f${stock.symbol} §8- §7${stock.name} §8- §6${marketManager.formatCurrency(stock.currentPrice)} $changeColor(${String.format("%.2f", change)}%)")
		}
	}

	private fun handleChart(player: Player, args: Array<String>) {
		if (args.size < 2) {
			player.sendMessage("§cUsage: /stock chart <symbol> [days]")
			return
		}

		val symbol = args[1]
		val days = if (args.size > 2) args[2].toIntOrNull() ?: 7 else 7

		val stock = stockManager.getStock(symbol)
		if (stock == null) {
			player.sendMessage("§cStock not found!")
			return
		}

		val history = stockManager.getStockHistory(symbol, days)
		if (history.isEmpty()) {
			player.sendMessage("§cNo price history available!")
			return
		}

		player.sendMessage("§6=== $symbol Price Chart (Last $days days) ===")
		history.take(10).forEach { entry ->
			val date = entry.timestamp.toLocalDate()
			player.sendMessage("§7$date §8- §6${marketManager.formatCurrency(entry.price)} §8- §7${entry.reason}")
		}
	}

	private fun handleAdmin(player: Player, args: Array<String>) {
		if (!player.hasPermission("stockmarket.admin")) {
			player.sendMessage("§cNo permission!")
			return
		}

		if (args.size < 2) {
			player.sendMessage("§cAdmin commands: create, setprice, dividend, market, reload")
			return
		}

		when (args[1].lowercase()) {
			"create" -> {
				if (args.size < 5) {
					player.sendMessage("§cUsage: /stock admin create <symbol> <name> <price> [category]")
					return
				}
				val symbol = args[2]
				val name = args[3]
				val price = args[4].toBigDecimalOrNull()
				val category = if (args.size > 5) args[5] else "CUSTOM"

				if (price == null) {
					player.sendMessage("§cInvalid price!")
					return
				}

				if (stockManager.createStock(symbol, name, price, category, 0.05)) {
					player.sendMessage("§aCreated stock $symbol!")
				} else {
					player.sendMessage("§cFailed to create stock!")
				}
			}
			"setprice" -> {
				if (args.size < 4) {
					player.sendMessage("§cUsage: /stock admin setprice <symbol> <price>")
					return
				}
				val symbol = args[2]
				val price = args[3].toBigDecimalOrNull()

				if (price == null) {
					player.sendMessage("§cInvalid price!")
					return
				}

				if (stockManager.updateStockPrice(symbol, price, "Admin")) {
					player.sendMessage("§aUpdated $symbol price to ${marketManager.formatCurrency(price)}")
				} else {
					player.sendMessage("§cStock not found!")
				}
			}
			"dividend" -> {
				if (args.size < 4) {
					player.sendMessage("§cUsage: /stock admin dividend <symbol> <amount>")
					return
				}
				val symbol = args[2]
				val amount = args[3].toBigDecimalOrNull()

				if (amount == null) {
					player.sendMessage("§cInvalid amount!")
					return
				}

				if (marketManager.payDividends(symbol, amount)) {
					player.sendMessage("§aPaid dividends for $symbol!")
				} else {
					player.sendMessage("§cFailed to pay dividends!")
				}
			}
			"market" -> {
				if (args.size < 3) {
					player.sendMessage("§cUsage: /stock admin market <open|close>")
					return
				}

				val open = args[2].lowercase() == "open"
				marketManager.setMarketOpen(open)
				player.sendMessage("§aMarket is now ${if (open) "OPEN" else "CLOSED"}")
			}
			"reload" -> {
				plugin.reloadConfig()
				player.sendMessage("§aConfig reloaded!")
			}
			"stats" -> {
				val stats = databaseManager.getDatabaseStats()
				player.sendMessage("§6=== Database Statistics ===")
				stats.forEach { (table, count) ->
					player.sendMessage("§7$table: §f$count")
				}
			}
		}
	}

	private fun sendHelp(player: Player) {
		player.sendMessage("§6=== StockMarket Commands ===")
		player.sendMessage("§7/stock list [category] - List all stocks")
		player.sendMessage("§7/stock info <symbol> - View stock details")
		player.sendMessage("§7/stock buy <symbol> <quantity> - Buy stocks")
		player.sendMessage("§7/stock sell <symbol> <quantity> - Sell stocks")
		player.sendMessage("§7/stock portfolio [player] - View portfolio")
		player.sendMessage("§7/stock balance - Check your balance")
		player.sendMessage("§7/stock history [limit] - Transaction history")
		player.sendMessage("§7/stock top <performers|losers|portfolios> - View rankings")
		player.sendMessage("§7/stock market - Market overview")
		player.sendMessage("§7/stock search <query> - Search stocks")
		player.sendMessage("§7/stock chart <symbol> [days] - Price history")

		if (player.hasPermission("stockmarket.admin")) {
			player.sendMessage("§c=== Admin Commands ===")
			player.sendMessage("§7/stock admin create <symbol> <name> <price> [category]")
			player.sendMessage("§7/stock admin setprice <symbol> <price>")
			player.sendMessage("§7/stock admin dividend <symbol> <amount>")
			player.sendMessage("§7/stock admin market <open|close>")
			player.sendMessage("§7/stock admin reload")
			player.sendMessage("§7/stock admin stats")
		}
	}


	@Suggestions("args")
	fun containerSuggestions(
		context: CommandContext<Player>,
		input: String
	): Stream<String> {
		val CommandList = mutableListOf<String>()
		CommandList.add("list")
		CommandList.add("info")
		CommandList.add("buy")
		CommandList.add("sell")
		CommandList.add("portfolio")
		CommandList.add("balance")
		CommandList.add("history")
		CommandList.add("top")
		CommandList.add("market")
		CommandList.add("search")
		CommandList.add("chart")
		CommandList.add("admin")
		CommandList.add("help")
		return CommandList.stream()
	}
}