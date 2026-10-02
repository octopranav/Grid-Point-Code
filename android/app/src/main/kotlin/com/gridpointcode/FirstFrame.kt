package com.gridpointcode

import android.os.StrictMode

/**
 * Runs [read] on the main thread with StrictMode's disk checks set aside, for a
 * read the first frame cannot do without: a few small values and one small file,
 * read before anything is drawn so the first frame is the reader's own map,
 * words, sections and bookmark, not the defaults and then a jump.
 *
 * Each use is a choice made where it is made. Every other read or write on the
 * main thread is caught by the debug build's StrictMode, and the device tests
 * fail on it. Release builds have no StrictMode, and this costs them nothing.
 */
inline fun <T> beforeTheFirstFrame(read: () -> T): T {
    val policy = StrictMode.allowThreadDiskReads()
    try {
        return read()
    } finally {
        StrictMode.setThreadPolicy(policy)
    }
}
