package com.vendistri.operations.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponsiveLayoutTest {
    @Test
    fun compactPhoneShrinksPrimaryMapAction() {
        val layout = vendistriResponsiveLayout(screenWidthDp = 320, screenHeightDp = 569)

        assertTrue(layout.compactDevice)
        assertEquals(56f, layout.primaryMapActionSize.value, 0.01f)
        assertEquals(176f, layout.taskPanelTopClearance.value, 0.01f)
        assertEquals(18f, layout.calendarDayRowHeight.value, 0.01f)
    }

    @Test
    fun regularPhoneCapsPrimaryMapActionAtDefaultSize() {
        val layout = vendistriResponsiveLayout(screenWidthDp = 432, screenHeightDp = 900)

        assertEquals(70f, layout.primaryMapActionSize.value, 0.01f)
        assertEquals(224f, layout.taskPanelTopClearance.value, 0.01f)
    }
}
