package net.perfectdreams.dreamsuggestions

import club.minnced.discord.webhook.WebhookClientBuilder
import club.minnced.discord.webhook.send.WebhookEmbed
import club.minnced.discord.webhook.send.WebhookEmbedBuilder
import com.github.salomonbrys.kotson.fromJson
import com.github.salomonbrys.kotson.string
import net.perfectdreams.dreamsuggestions.commands.DreamSuggestionCommand
import net.perfectdreams.dreamsuggestions.utils.SuggestionsLocation
import org.bukkit.Material
import org.bukkit.block.Chest
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.EventHandler
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BookMeta
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL
import com.google.gson.JsonParser
import net.perfectdreams.dreamcore.utils.*
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.event.block.Action
import java.io.File
import java.util.*
import kotlin.collections.HashMap

class DreamSuggestions : KotlinPlugin(), Listener {
    companion object {
        const val PATH = "suggestions-webhook-url"
        const val PREFIX = "§f[§3SUGESTÕES§f]"

        lateinit var SUGGESTIONS_WEBHOOK_URL: String
    }

    init {
        SUGGESTIONS_WEBHOOK_URL = config.getString(PATH)
    }

    var suggestionsLocation = mutableListOf<SuggestionsLocation>()

    private val suggestionBookCooldown = HashMap<String, Long>()
    private val storeChestLocations = File(dataFolder, "suggestionsChestLocations.json")
    private fun hasStoredLocation(location: Location) = suggestionsLocation.firstOrNull { it.location == location }
    private val bookDescription = "§cNão ofenda ninguém e\n§crespeite as regras, a\nstaff lerá isto!\n\n§9Escreva sua sugestão\n§9abaixo e assine o\n§9livro.\n\n"

    private val STORAGE_KEY = UUID.fromString("75b640cd-553b-4026-a5ac-769f792cfe73")!!

    override fun onEnable(){
        super.softEnable()

        registerCommand(DreamSuggestionCommand(this))

        registerEvents(this)

        if (storeChestLocations.exists()) {
            suggestionsLocation = DreamUtils.gson.fromJson(storeChestLocations.readText())
        }
    }

    @EventHandler
    fun onPlayerInteractEvent(e: PlayerInteractEvent){

        val item = e.player.inventory.itemInMainHand

        if (item.type == Material.WRITTEN_BOOK && e.clickedBlock.type == Material.CHEST) {

            if (hasStoredLocation(e.clickedBlock.location) != null) {
                val book = item.itemMeta as BookMeta

                var atualData: ItemStack = item
                val world = server.getWorld(e.clickedBlock.world.name)
                val chestLoc = world.getBlockAt(e.clickedBlock.location).state

                val chest = chestLoc as Chest

                e.isCancelled = true

                for((books) in chest.blockInventory.withIndex()) {
                    atualData = item.setStorageData("$books", STORAGE_KEY)
                }

                chest.blockInventory.addItem(atualData)

                e.player.inventory.itemInMainHand = null

                val (uuid, name) = playerInfo(e.player.name)

                val newBookDesc = bookDescription.replace("(§c|§9)".toRegex(), "")

                val headSkin = "https://minotar.net/avatar/$uuid/100.png"

                val suggestion = book.pages.joinToString("\n") { it.stripColorCode().replace(newBookDesc, "") }


                // Construindo nossa embed com o nome do player, cabeça (caso a conta não seja original, iremos enviar a cabeça da mascote do servidor)
                // E o autor do livro em que a sugestão está
		        val embedBuilder = WebhookEmbedBuilder()

                embedBuilder.setAuthor(WebhookEmbed.EmbedAuthor(book.author, headSkin, headSkin))
                embedBuilder.setThumbnailUrl(headSkin)
                embedBuilder.addField(WebhookEmbed.EmbedField(false, "Título: ", book.title))
                embedBuilder.addField(WebhookEmbed.EmbedField(false, "Sugestão: ", suggestion))
                embedBuilder.setColor(1217178)
                embedBuilder.setFooter(WebhookEmbed.EmbedFooter("SparklyPower | Sugestão enviada por $name", "https://imgur.com/v1gMSLI.png"))

                // URL da nossa webhook que será pega da config.yml do plugin
                val client = WebhookClientBuilder(SUGGESTIONS_WEBHOOK_URL).build()

                // Enviando nossa embed construida para a webhook do discord
                client.send(embedBuilder.build())

                e.player.sendMessage("$PREFIX §6Sua sugestão foi enviada para a staff do servidor. Obriagado por ajudar o §4§lSparkly§9§lPower §r§6a ser um servidor melhor!")
                e.clickedBlock.world.spawnParticle(Particle.HEART, e.clickedBlock.location.add(0.0, 1.0, 0.0), 5, 1.0, 1.0, 1.0)
            }
        }

        if (e.action == Action.RIGHT_CLICK_BLOCK && e.clickedBlock.type == Material.WALL_SIGN) {
            if (hasStoredLocation(e.clickedBlock.location) != null) {
                if(e.player.inventory.contains(Material.WRITABLE_BOOK)){
                    e.player.sendMessage("$PREFIX §cHey espertinho, você já tem um livro no seu inventário, e ele está em perfeitas condições, use ele para escrever sua sugestão!")
                    return
                }

                val (leftTime, cooldown) = CoolDown(e.player.name)
		        val timePrefix = if(leftTime > 60){ "${leftTime/60} minuto(s)" }else{ "$leftTime segundos" }

                if(cooldown){
                    e.player.sendMessage("$PREFIX §cVocê precisa esperar mais §6$timePrefix §cpara pegar outro livro!")
		            return
                }

                val writableBook = ItemStack(Material.WRITABLE_BOOK, 1)
                val book = writableBook.itemMeta as BookMeta

                book.pages = Arrays.asList(bookDescription)

                if (e.player.inventory.canHoldItem(writableBook)) {
                    writableBook.itemMeta = book

                    val atualItem = e.player.inventory.itemInMainHand

                    e.player.inventory.itemInMainHand = writableBook
                    e.player.inventory.addItem(atualItem)

                    e.player.sendMessage("$PREFIX §6Livro em mãos... vamos lá!")
                }
            }
        }
    }

    fun CoolDown(playerName: String): Pair<Long, Boolean>{
        var _leftTime: Long = 0
        var userCoolDown = false

        val coolDownTime = 120 // Segundos

        if(suggestionBookCooldown.containsKey(playerName)){
            val leftTime = ((suggestionBookCooldown[playerName]!! /1000)+coolDownTime) - (System.currentTimeMillis()/1000)

            if(leftTime > 0){
                userCoolDown = true
		        _leftTime = leftTime
            }else{
	        suggestionBookCooldown.remove(playerName)
	    }

        }else{
            suggestionBookCooldown[playerName] = System.currentTimeMillis() // Player não está na lista de suggestionBookCooldown, então vamos adicionar ele
	    }

        return Pair(_leftTime, userCoolDown)
    }

    fun storeLocations(){
        storeChestLocations.writeText(DreamUtils.gson.toJson(suggestionsLocation))
    }

    fun playerInfo(playerName: String): List<String?>{
        val isPremiumAcc: Boolean

        val loritufa = listOf("b564af71e8684569aa317df28861d38c", "b837f3d677204b469a8e2b6bf04a241a")
        val random = (0..1).random()

        var playerUUID = loritufa[random]

        try {
            val mojangAPI = URL("https://api.mojang.com/users/profiles/minecraft/$playerName")
            val jsonOtp = BufferedReader(InputStreamReader(mojangAPI.openStream())).readLine()

            isPremiumAcc = jsonOtp != null

            if(isPremiumAcc){
                val (uuid, name) = mojangJsonAPI(jsonOtp)
                playerUUID = uuid
            }

        }catch (e: Exception){

        }

        return listOf(playerUUID, playerName)
    }

    fun mojangJsonAPI(jsonStr: String? = null): List<String> {
        val receivedJson = JsonParser().parse(jsonStr)
        val entries = receivedJson.asJsonObject.entrySet()

        val accountInfo = mutableListOf<String>()

        for((idx, key) in entries.withIndex()){
            accountInfo.add(idx, key.value.string)
        }

        return listOf(accountInfo[0], accountInfo[1]) // uuid, name
    }
}