package com.gridpointcode.screens

/**
 * The screens the design is checked on, as Android resource qualifiers: width
 * and height in dp, then the density.
 */
object Devices {
    /** A Pixel 7, the phone the device tests also run on. */
    const val PHONE = "w411dp-h914dp-normal-long-notround-any-420dpi-keyshidden-nonav"

    /** The same phone drawn very tall, so an opened panel or a long page is one image. */
    const val TALL = "w411dp-h2600dp-normal-long-notround-any-420dpi-keyshidden-nonav"

    /** A small, older phone, where every row is shortest. */
    const val SMALL = "w360dp-h640dp-normal-notlong-notround-any-320dpi-keyshidden-nonav"

    /** A foldable opened out: nearly square, and just short of the width the wide layout takes. */
    const val FOLDABLE = "w820dp-h790dp-large-notlong-notround-any-420dpi-keyshidden-nonav"

    /** A tablet on its side, which takes the wide layout: the panel beside the map. */
    const val TABLET = "w1280dp-h800dp-xlarge-long-notround-any-240dpi-keyshidden-nonav"

    /** A 7-inch tablet on its side: the wide layout too, on the least height it is drawn at. */
    const val SMALL_TABLET = "w960dp-h600dp-large-long-notround-any-320dpi-keyshidden-nonav"
}
