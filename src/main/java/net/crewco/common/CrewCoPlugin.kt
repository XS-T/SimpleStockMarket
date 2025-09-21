package net.crewco.common


import com.github.shynixn.mccoroutine.bukkit.SuspendingJavaPlugin
import com.github.shynixn.mccoroutine.bukkit.registerSuspendingEvents
import com.github.shynixn.mccoroutine.bukkit.scope
import com.google.inject.Guice
import com.google.inject.Injector
import net.kyori.adventure.platform.bukkit.BukkitAudiences
import net.crewco.common.injection.SpigotModule
import net.crewco.common.util.SpigotExecutor
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.incendo.cloud.SenderMapper
import org.incendo.cloud.bukkit.CloudBukkitCapabilities
import org.incendo.cloud.execution.ExecutionCoordinator
import org.incendo.cloud.minecraft.extras.MinecraftExceptionHandler
import java.util.function.Function
import kotlin.reflect.KClass
import org.incendo.cloud.minecraft.extras.MinecraftHelp;
import org.incendo.cloud.paper.LegacyPaperCommandManager;
import net.kyori.adventure.text.Component.text
import org.incendo.cloud.annotations.AnnotationParser
import org.incendo.cloud.annotations.BuilderDecorator
import org.incendo.cloud.annotations.CommandDescription
import org.incendo.cloud.description.CommandDescription.commandDescription
import org.incendo.cloud.kotlin.coroutines.annotations.installCoroutineSupport
import org.incendo.cloud.meta.CommandMeta
import org.incendo.cloud.minecraft.extras.caption.ComponentCaptionFormatter
import org.incendo.cloud.parser.StandardParameters


//TODO: Move this to a separate project once we have an actual server and a maven repo
abstract class CrewCoPlugin : SuspendingJavaPlugin() {
	private lateinit var injector: Injector

	lateinit var commandManager: LegacyPaperCommandManager<CommandSender?>
	lateinit var bukkitAudiences: BukkitAudiences
	lateinit var minecraftHelp: MinecraftHelp<CommandSender>
	lateinit var annotationParser: AnnotationParser<CommandSender>

	override suspend fun onEnableAsync() {
		injector = Guice.createInjector(SpigotModule(this))
		val toPlayerMapper = Function<CommandSender, Player> { it as Player }
		val fromPlayerMapper = Function<Player, CommandSender> { it }


		commandManager = LegacyPaperCommandManager( /* Owning plugin */
			this,  /* (1) */
			ExecutionCoordinator.builder<CommandSender>().synchronizeExecution().executor(
				SpigotExecutor(this)
			).build(),  /* (2) */
			SenderMapper.identity()
		)

		if (commandManager.hasCapability(CloudBukkitCapabilities.NATIVE_BRIGADIER)){
			commandManager.registerBrigadier()
		}else if (commandManager.hasCapability(CloudBukkitCapabilities.ASYNCHRONOUS_COMPLETION)){
			commandManager.registerAsynchronousCompletions()
		}

		bukkitAudiences = BukkitAudiences.create(this)

		MinecraftExceptionHandler.create(this.bukkitAudiences::sender)
			.defaultInvalidSyntaxHandler()
			.defaultInvalidSenderHandler()
			.defaultNoPermissionHandler()
			.defaultArgumentParsingHandler()
			.defaultCommandExecutionHandler()
			.decorator(
				{ component ->
					text()
						.append(text("[", NamedTextColor.DARK_GRAY))
						.append(text("Example", NamedTextColor.GOLD))
						.append(text("] ", NamedTextColor.DARK_GRAY))
						.append(component).build()
				}
			)
			.registerTo(commandManager)

		minecraftHelp = MinecraftHelp.builder<CommandSender>().commandManager(commandManager).audienceProvider(bukkitAudiences::sender).commandPrefix("/builder help").messageProvider(
			MinecraftHelp.captionMessageProvider(commandManager.captionRegistry(), ComponentCaptionFormatter.miniMessage())).build()

		commandManager.captionRegistry().registerProvider(MinecraftHelp.defaultCaptionsProvider())

		annotationParser = AnnotationParser(commandManager,CommandSender::class.java){
			return@AnnotationParser CommandMeta.empty()
		}

		annotationParser.registerBuilderDecorator(
			BuilderDecorator.defaultDescription(commandDescription("No Description"))
		)

		annotationParser.installCoroutineSupport(this.scope)
	}

	override suspend fun onDisableAsync() {
		super.onDisableAsync()
	}

	protected fun registerListeners(vararg listeners: KClass<out Listener>) {
		listeners.forEach {
			server.pluginManager.registerSuspendingEvents(injector.getInstance(it.java), this)
		}
	}

	protected fun registerCommands(vararg commands: KClass<out Any>) {
		commands.forEach {
			annotationParser.parse(injector.getInstance(it.java))
		}
	}
}