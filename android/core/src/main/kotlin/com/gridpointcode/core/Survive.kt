package com.gridpointcode.core

/**
 * What of the place on screen outlives the app's process.
 *
 * Android ends a process in the background whenever it wants the memory back. A
 * reader who goes to a messaging app to paste a code, or to the telephone with
 * the emergency card open, can come back to a new process, and before this they
 * came back to the specification's example. What they left is put back: the
 * place, how it was reached and how closely, the directions written for it, the
 * area shown instead of it, and where a converted place came from. A complaint,
 * the listening for a fix and the offer of codes one slip away belong to the
 * moment, and are not.
 *
 * Plain strings, which the platform's saved state carries as they are.
 */
fun PlaceState.toSaved(): Map<String, String?> = mapOf(
    LATITUDE to selection.point.latitude.toString(),
    LONGITUDE to selection.point.longitude.toString(),
    SOURCE to selection.source.name,
    ACCURACY to selection.accuracyMetres?.toString(),
    NOTE to note.ifEmpty { null },
    AREA to area?.cell,
    ORIGIN_FORMAT to origin?.format?.name,
    ORIGIN_TEXT to origin?.text,
    ORIGIN_METRES to origin?.metres?.toString(),
    ORIGIN_CENTRE to origin?.viewCentre?.toString(),
)

/**
 * The place [toSaved] kept, read back through [saved], or null when nothing was
 * kept or what was kept no longer reads, as after an update that renamed a
 * source: the example is a better start than a place half put back.
 */
fun restoredPlace(saved: (String) -> String?): PlaceState? = runCatching {
    val latitude = saved(LATITUDE)?.toDouble() ?: return null
    val longitude = saved(LONGITUDE)?.toDouble() ?: return null
    val source = Source.valueOf(saved(SOURCE) ?: return null)
    val origin = saved(ORIGIN_FORMAT)?.let { format ->
        Origin(
            format = Format.valueOf(format),
            text = saved(ORIGIN_TEXT).orEmpty(),
            metres = saved(ORIGIN_METRES)?.toDouble() ?: 0.0,
            viewCentre = saved(ORIGIN_CENTRE).toBoolean(),
        )
    }
    PlaceState(
        selection = selectionAt(Point(latitude, longitude), source, saved(ACCURACY)?.toDouble()),
        note = saved(NOTE).orEmpty(),
        area = saved(AREA)?.let(::areaOf),
        origin = origin,
    )
}.getOrNull()

private const val LATITUDE = "place.latitude"
private const val LONGITUDE = "place.longitude"
private const val SOURCE = "place.source"
private const val ACCURACY = "place.accuracy"
private const val NOTE = "place.note"
private const val AREA = "place.area"
private const val ORIGIN_FORMAT = "place.origin.format"
private const val ORIGIN_TEXT = "place.origin.text"
private const val ORIGIN_METRES = "place.origin.metres"
private const val ORIGIN_CENTRE = "place.origin.centre"
