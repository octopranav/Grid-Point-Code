package com.gridpointcode.map

import androidx.compose.ui.graphics.toArgb
import ca.pranavpatel.algo.gridpointcode.design.brassDark
import ca.pranavpatel.algo.gridpointcode.design.brassLight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Which style a choice comes to, and which inks draw on it.
 *
 * The inks are the part worth a test. It is natural to draw in the app's own
 * colours, and that is right until someone picks a light map in the dark theme,
 * when the cell drops to about 2:1 against the map, or fiord in the light theme,
 * when it drops to 1.4:1 and all but vanishes.
 */
class BasemapTest {

    private val base = "https://tiles.example/styles/"

    @Test
    fun matchingTheThemeFollowsIt() {
        assertEquals(Resolved(base + "positron", dark = false), resolve(Basemap.AUTO, darkTheme = false, base))
        assertEquals(Resolved(base + "fiord", dark = true), resolve(Basemap.AUTO, darkTheme = true, base))
    }

    @Test
    fun aChosenMapIsTheSameInEitherTheme() {
        assertEquals(resolve(Basemap.BRIGHT, darkTheme = false, base), resolve(Basemap.BRIGHT, darkTheme = true, base))
        assertEquals(base + "bright", resolve(Basemap.BRIGHT, darkTheme = true, base).url)
    }

    @Test
    fun aLightMapIsDrawnInTheLightInksEvenInTheDarkTheme() {
        val positron = resolve(Basemap.POSITRON, darkTheme = true, base)
        assertEquals(brassLight.toArgb(), inkFor(positron.dark).brass)
    }

    @Test
    fun aDarkMapIsDrawnInTheDarkInksEvenInTheLightTheme() {
        val fiord = resolve(Basemap.FIORD, darkTheme = false, base)
        assertEquals(brassDark.toArgb(), inkFor(fiord.dark).brass)
    }

    @Test
    fun everyNamedMapSaysWhetherItIsDark() {
        Basemap.entries.filter { it != Basemap.AUTO }.forEach { assertNotNull(it.dark, it.name) }
    }
}
