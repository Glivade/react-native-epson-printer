package com.reactnativeepsonprinter

object PrintUtil {
    val FONT_SIZE_SMALL = byteArrayOf(27, 33, 0)
    val FONT_SIZE_REGULAR = byteArrayOf(27, 33, 16)
    val FONT_SIZE_MEDIUM = byteArrayOf(27, 33, 32)
    val FONT_SIZE_LARGE = byteArrayOf(27, 33, 48)

    val ESC_ALIGN_CENTER = byteArrayOf(0x1b, 'a'.toByte(), 0x01)
    var SELECT_BIT_IMAGE_MODE = byteArrayOf(0x1B, 0x2A, 33, 255.toByte(), 3)

    val CUT_PAPER = byteArrayOf(29, 86, 66, 0)
}
