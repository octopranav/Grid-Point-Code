package com.gridpointcode.automotive

import android.content.Context
import com.gridpointcode.car.CarPlaces
import com.gridpointcode.car.GpcCarAppService
import com.gridpointcode.core.spellingFor
import com.gridpointcode.notices.Block
import com.gridpointcode.notices.blocksOf
import com.gridpointcode.notices.componentsIn
import com.gridpointcode.notices.readNotice
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Android Automotive: the car screens, run by the car itself with no phone.
 * With no phone there are no saved places, so the screens say where they are,
 * and the car's own language is the listener's. The car is the only screen
 * this app has, so the notices are shown here.
 */
class CarService : GpcCarAppService() {

    override fun places(): CarPlaces = CarPlaces(
        saved = MutableStateFlow(emptyList()),
        spelling = { spellingFor(Locale.getDefault()) },
        savedHere = false,
        notices = ::notices,
    )
}

/**
 * Every library the release ships, with the Apache License most are under and
 * the notice each of the others requires, as one message.
 */
private fun notices(context: Context): String {
    val components = componentsIn(readNotice(context, "components.txt").orEmpty())
    val apache = components.filter { it.licence == "Apache-2.0" }.joinToString("\n") { it.module }
    val others = components.filter { it.licence != "Apache-2.0" }
    return buildList {
        add(context.getString(R.string.notices_intro))
        add(context.getString(R.string.notices_apache) + "\n" + apache)
        add(unwrapped(readNotice(context, "apache-2.0.txt").orEmpty()))
        others.forEach { component ->
            add(context.getString(R.string.notices_other, component.module, component.licence))
            component.notice?.let { add(unwrapped(readNotice(context, it).orEmpty())) }
        }
    }.joinToString("\n\n")
}

/** A notice hard-wrapped for a terminal, rejoined into paragraphs the car wraps to its own screen. */
private fun unwrapped(notice: String): String =
    blocksOf(notice).mapNotNull {
        when (it) {
            is Block.Heading -> it.text
            is Block.Paragraph -> it.text
            Block.Rule -> null
        }
    }.joinToString("\n\n")
