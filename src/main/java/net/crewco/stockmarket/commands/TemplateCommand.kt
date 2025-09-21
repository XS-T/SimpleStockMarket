package net.crewco.stockmarket.commands

import com.google.inject.Inject
import net.crewco.stockmarket.StockMarketPlugin
import org.bukkit.entity.Player
import org.incendo.cloud.annotations.Command
import org.incendo.cloud.annotations.CommandDescription
import org.incendo.cloud.annotations.Permission

class TemplateCommand @Inject constructor(private val plugin: StockMarketPlugin) {
	@Command("templateCommand <item>")
	@CommandDescription("This is a template command")
	@Permission("template.command.use")
	suspend fun template(player: Player) {
	}



}