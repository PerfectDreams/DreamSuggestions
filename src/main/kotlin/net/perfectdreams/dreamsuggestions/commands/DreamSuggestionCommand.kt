package net.perfectdreams.dreamsuggestions.commands

import net.perfectdreams.commands.bukkit.SparklyCommand
import net.perfectdreams.dreamcore.utils.generateCommandInfo
import net.perfectdreams.commands.annotation.Subcommand
import net.perfectdreams.dreamsuggestions.DreamSuggestions
import net.perfectdreams.dreamsuggestions.DreamSuggestions.Companion.PREFIX
import net.perfectdreams.dreamsuggestions.utils.SuggestionsLocation
import org.bukkit.Material
import org.bukkit.block.Sign
import org.bukkit.entity.Player

class DreamSuggestionCommand(val m: DreamSuggestions) : SparklyCommand(arrayOf("suggestions"), permission = "dreamsuggestions.set"){

    @Subcommand
    fun root(player: Player){
        player.sendMessage(generateCommandInfo(
                "suggestions",
                mapOf("set" to "Define o baú ou uma placa de sugestões",
                      "remove" to "Remove um baú de sugestões ou a placa usada para dar livros"
                )
            )
        )
    }

    @Subcommand(["set"])
    fun suggestionsSetLocation(player: Player){
        val targetBlock = player.getTargetBlock(null as Set<Material>?, 10).location

        val saveTargetBlock = SuggestionsLocation(targetBlock)
        val removeTargetBlock = m.suggestionsLocation.firstOrNull { it.location == targetBlock }

        if(targetBlock.block.type == Material.CHEST || targetBlock.block.type == Material.WALL_SIGN){
            m.suggestionsLocation.remove(removeTargetBlock)
            m.suggestionsLocation.add(saveTargetBlock)
            m.storeLocations()
            if(targetBlock.block.type == Material.CHEST){ player.sendMessage("$PREFIX §6Baú de sugestões setado com sucesso!") }

        }else{
            player.sendMessage("$PREFIX §cHey... isso não é um baú!, como você quer que eu armazene as sugestões nesse bloco?")
        }

        if(targetBlock.block.type == Material.WALL_SIGN){
            player.sendMessage("$PREFIX §6Essa será a placa usada para que os players possam pegar livros!")
            val sign = targetBlock.block.state as Sign

            sign.setLine(0, "§c§lClique aqui")
            sign.setLine(1, "§9para pegar um")
            sign.setLine(2, "§9livro e escrever")
            sign.setLine(3, "§9sua sugestão!")

            sign.update()
        }
    }

    @Subcommand(["remove"])
    fun suggestionsRemoveLocation(player: Player){
        val targetBlock = player.getTargetBlock(null as Set<Material>?, 10).location
        val removeTargetBlock = m.suggestionsLocation.firstOrNull { it.location == targetBlock }

        if (removeTargetBlock == null){
            player.sendMessage("$PREFIX §cIsso não é um baú de sugestões, para setar um use §6§l/suggestions set")
            return
        }

	val targetBlockPrefix = if(targetBlock.block.type == Material.CHEST){ "baú de sugestões" }else{ "placa de livros" }

        m.suggestionsLocation.remove(removeTargetBlock)
        m.storeLocations()
	player.sendMessage("$PREFIX §6$targetBlockPrefix §cremovido(a) com sucesso!")
    }
}