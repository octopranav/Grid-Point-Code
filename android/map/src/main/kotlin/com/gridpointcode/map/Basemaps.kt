package com.gridpointcode.map

import androidx.compose.ui.graphics.toArgb
import ca.pranavpatel.algo.gridpointcode.design.brassDark
import ca.pranavpatel.algo.gridpointcode.design.brassLight
import ca.pranavpatel.algo.gridpointcode.design.inkDark
import ca.pranavpatel.algo.gridpointcode.design.inkSoftDark
import ca.pranavpatel.algo.gridpointcode.design.inkSoftLight
import ca.pranavpatel.algo.gridpointcode.design.prussianDark
import ca.pranavpatel.algo.gridpointcode.design.prussianLight
import ca.pranavpatel.algo.gridpointcode.design.surfaceLight

/**
 * The basemaps the tile provider offers, the same list the website gives, and
 * whether each is a light map or a dark one.
 *
 * Positron and fiord are the desaturated pair, which is what a drawing laid on
 * top needs: the cell has to be the brightest thing on the screen. They are what
 * [AUTO] picks, following the light or dark theme.
 */
enum class Basemap(internal val style: String?, internal val dark: Boolean?) {
    AUTO(null, null),
    POSITRON("positron", false),
    BRIGHT("bright", false),
    LIBERTY("liberty", false),
    DARK("dark", true),
    FIORD("fiord", true),
}

/** The style a choice comes to, and whether what it draws is dark. */
internal data class Resolved(val url: String, val dark: Boolean)

internal fun resolve(basemap: Basemap, darkTheme: Boolean, base: String = BuildConfig.STYLES): Resolved =
    Resolved(
        url = base + (basemap.style ?: if (darkTheme) "fiord" else "positron"),
        dark = basemap.dark ?: darkTheme,
    )

/** The colours the drawing is made in. */
internal data class Ink(val brass: Int, val soft: Int, val prussian: Int, val halo: Int)

/**
 * The drawing takes its colours from the map beneath it, not from the app.
 *
 * A reader can pick a light map in the dark theme or a dark one in the light,
 * and the cell has to stay visible on whichever it is. Measured against each
 * style's own background: the light theme's brass is about 5:1 on positron,
 * bright and liberty, and 1.4:1 on fiord, where it all but disappears; the dark
 * theme's brass is 3.5:1 on fiord and 8.5:1 on dark, and 2.1:1 on the light
 * maps. So a light map gets the light theme's inks and a dark map the dark
 * theme's, whatever the rest of the screen is wearing.
 */
internal fun inkFor(darkMap: Boolean): Ink =
    if (darkMap) {
        Ink(brassDark.toArgb(), inkSoftDark.toArgb(), prussianDark.toArgb(), inkDark.toArgb())
    } else {
        Ink(brassLight.toArgb(), inkSoftLight.toArgb(), prussianLight.toArgb(), surfaceLight.toArgb())
    }
