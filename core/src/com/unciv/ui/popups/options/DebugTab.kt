package com.unciv.ui.popups.options

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle
import com.unciv.GUI
import com.unciv.UncivGame
import com.unciv.logic.UncivShowableException
import com.unciv.logic.files.MapSaver
import com.unciv.logic.files.UncivFiles
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.ui.components.widgets.UncivTextField
import com.unciv.ui.components.extensions.addSeparator
import com.unciv.ui.components.extensions.toCheckBox
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.widgets.UncivSlider
import com.unciv.ui.popups.ToastPopup
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.utils.Concurrency
import com.unciv.utils.DebugUtils

fun debugTab(
    optionsPopup: OptionsPopup
) = Table(BaseScreen.skin).apply {
    pad(10f)
    defaults().pad(5f)
    val game = UncivGame.Current

    if (GUI.isWorldLoaded()) {
        val simulateButton = "Simulate until turn:".toTextButton()
        val simulateTextField = UncivTextField.Numeric("Turn", DebugUtils.SIMULATE_UNTIL_TURN, integerOnly = true)
        val invalidInputLabel = "This is not a valid integer!".toLabel().also { it.isVisible = false }
        simulateButton.onClick {
            val simulateUntilTurns = simulateTextField.value?.toInt()
            if (simulateUntilTurns == null) {
                invalidInputLabel.isVisible = true
                return@onClick
            }
            DebugUtils.SIMULATE_UNTIL_TURN = simulateUntilTurns
            invalidInputLabel.isVisible = false
            GUI.getWorldScreen().nextTurn()
        }
        add(simulateButton)
        add(simulateTextField).row()
        add(invalidInputLabel).colspan(2).row()
    }

    optionsPopup.addCheckbox(this, "Supercharged", DebugUtils.SUPERCHARGED) { DebugUtils.SUPERCHARGED = it }
    optionsPopup.addCheckbox(this, "View entire map", DebugUtils.VISIBLE_MAP) { DebugUtils.VISIBLE_MAP = it }
    optionsPopup.addCheckbox(this, "Show coordinates on tiles", DebugUtils.SHOW_TILE_COORDS) { DebugUtils.SHOW_TILE_COORDS = it }
    optionsPopup.addCheckbox(this, "Show tile image locations", DebugUtils.SHOW_TILE_IMAGE_LOCATIONS) { DebugUtils.SHOW_TILE_IMAGE_LOCATIONS = it }

    val curGameInfo = game.gameInfo
    if (curGameInfo != null) {
        optionsPopup.addCheckbox(this, "God mode (current game)", curGameInfo.gameParameters.godMode) { curGameInfo.gameParameters.godMode = it }
    }

    optionsPopup.addCheckbox(this, "Save games compressed", UncivFiles.saveZipped) { UncivFiles.saveZipped = it }
    optionsPopup.addCheckbox(this, "Save maps compressed", MapSaver.saveZipped) { MapSaver.saveZipped = it }
    optionsPopup.addCheckbox(this, "Gdx Scene2D debug", BaseScreen.enableSceneDebug) { BaseScreen.enableSceneDebug = it }
        

    add(Table().apply {
        add("Unique misspelling threshold".toLabel()).left().fillX()
        add(
            UncivSlider(0f, 0.5f, 0.05f, initial = RulesetCache.uniqueMisspellingThreshold.toFloat()) {
                RulesetCache.uniqueMisspellingThreshold = it.toDouble()
            }
        ).minWidth(120f).pad(5f)
    }).colspan(2).row()

    val unlockTechsButton = "Unlock all techs".toTextButton()
    unlockTechsButton.onClick {
        if (curGameInfo == null)
            return@onClick
        for (tech in curGameInfo.ruleset.technologies.keys) {
            if (tech !in curGameInfo.getCurrentPlayerCivilization().tech.techsResearched) {
                curGameInfo.getCurrentPlayerCivilization().tech.addTechnology(tech)
                curGameInfo.getCurrentPlayerCivilization().popupAlerts.removeLastOrNull()
            }
        }
        curGameInfo.getCurrentPlayerCivilization().cache.updateSightAndResources()
        GUI.setUpdateWorldOnNextRender()
    }
    add(unlockTechsButton).colspan(2).row()

    val giveResourcesButton = "Get all strategic resources".toTextButton()
    giveResourcesButton.onClick {
        if (curGameInfo == null)
            return@onClick
        val ownedTiles = curGameInfo.tileMap.values.asSequence().filter { it.getOwner() == curGameInfo.getCurrentPlayerCivilization() }
        val resourceTypes = curGameInfo.ruleset.tileResources.values.asSequence().filter { it.resourceType == ResourceType.Strategic }
        for ((tile, resource) in ownedTiles zip resourceTypes) {
            tile.resource = resource.name
            tile.resourceAmount = 999
            // Debug option, so if it crashes on this that's relatively fine
            // If this becomes a problem, check if such an improvement exists and otherwise plop down a great improvement or so
            tile.setImprovement(resource.getImprovements().first())
        }
        curGameInfo.getCurrentPlayerCivilization().cache.updateSightAndResources()
        GUI.setUpdateWorldOnNextRender()
    }
    add(giveResourcesButton).colspan(2).row()

    add("Load online multiplayer game as hotseat from clipboard".toTextButton().onClick {
        // Code duplication : LoadGameScreen.getLoadFromClipboardButton
        Concurrency.run {
            try {
                val clipboardContentsString = Gdx.app.clipboard.contents.trim()
                val loadedGame = UncivFiles.gameInfoFromString(clipboardContentsString)
                loadedGame.gameParameters.isOnlineMultiplayer = false
                optionsPopup.game.loadGame(loadedGame, callFromLoadScreen =  true)
                optionsPopup.close()
            } catch (ex: Exception) {
                ToastPopup(ex.message ?: ex::class.java.simpleName, optionsPopup.stageToShowOn)
            }
        }
    }).colspan(2).row()

    addSeparator()
    add("* Crash Unciv! *".toTextButton(skin.get("negative", TextButtonStyle::class.java)).onClick {
        throw UncivShowableException("Intentional crash")
    }).colspan(2).row()
    addSeparator()
}
