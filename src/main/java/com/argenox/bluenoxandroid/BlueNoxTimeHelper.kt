/*
 * Copyright (c) 2015-2026, Argenox Technologies LLC
 *
 * Licensed under the terms in the LICENSE file at the module root.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL ARGENOX TECHNOLOGIES LLC BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * File:    TimeHelper.kt
 * Summary: Time and date utility functions for BlueNox
 *
 **********************************************************************************/
package com.argenox.bluenoxandroid

import android.util.Log
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object TimeHelper {
    fun getTimeString(seconds: Long, top: Boolean): String {
        val days = TimeUnit.SECONDS.toDays(seconds).toInt()
        val hours = TimeUnit.SECONDS.toHours(seconds) - (days * 24)
        val minutes = TimeUnit.SECONDS.toMinutes(seconds) - (TimeUnit.SECONDS.toHours(seconds) * 60)
        val second =
            TimeUnit.SECONDS.toSeconds(seconds) - (TimeUnit.SECONDS.toMinutes(seconds) * 60)

        var str = ""

        if (days > 0) {
            if (str.length > 0) str += " "
            val strDays = String.format(Locale.US, "%dd", days)
            str += strDays
            if (top) return str
        }

        if (hours > 0) {
            if (str.length > 0) str += " "
            val strHrs = String.format(Locale.US, "%dh", hours)
            str += strHrs
            if (top) return str
        }

        if (minutes > 0) {
            if (str.length > 0) str += " "
            val strHrs = String.format(Locale.US, "%dm", minutes)
            str += strHrs
            if (top) return str
        }

        if (second > 0) {
            if (str.length > 0) str += " "
            val strSecs = String.format(Locale.US, "%ds", second)
            str += strSecs
            if (top) return str
        }

        return str
    }

    fun getTimelapse(from: Date, to: Date): Long {
        val val1 = to.time
        val val2 = from.time
        return (val1 - val2) / 1000
    }

    /* Returns the date range of the past hour,  up the the current time */
    private fun pastHourRange(): Array<Date?> {
        val dateRange = arrayOfNulls<Date>(2)

        var cal = Calendar.getInstance()
        cal.add(Calendar.HOUR_OF_DAY, -1)
        dateRange[0] = cal.time

        cal = Calendar.getInstance()
        dateRange[1] = cal.time

        return dateRange
    }

    /* Returns the date range of the current hour */
    fun currentHourRange(): Array<Date?> {
        val dateRange = arrayOfNulls<Date>(2)

        var cal = Calendar.getInstance()
        cal.clear(Calendar.MINUTE)
        cal.clear(Calendar.SECOND)
        cal.clear(Calendar.MILLISECOND)

        dateRange[0] = cal.time

        cal = Calendar.getInstance()
        cal[Calendar.MINUTE] = 59
        cal[Calendar.SECOND] = 59
        cal[Calendar.MILLISECOND] = 999

        dateRange[1] = cal.time

        return dateRange
    }

    /* Returns the date range of the current hour */
    fun currentMinuteRange(): Array<Date?> {
        val dateRange = arrayOfNulls<Date>(2)

        var cal = Calendar.getInstance()
        cal.clear(Calendar.SECOND)
        cal.clear(Calendar.MILLISECOND)

        dateRange[0] = cal.time

        cal = Calendar.getInstance()
        cal[Calendar.SECOND] = 59
        cal[Calendar.MILLISECOND] = 999

        dateRange[1] = cal.time

        return dateRange
    }


    /* Returns the date range of the current hour */
    private fun pastDayRange(): Array<Date?> {
        val dateRange = arrayOfNulls<Date>(2)

        var cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -1)
        dateRange[0] = cal.time

        cal = Calendar.getInstance()
        dateRange[1] = cal.time

        return dateRange
    }

    /* Returns the date range of the current hour */
    fun currentDayRange(): Array<Date?> {
        val dateRange = arrayOfNulls<Date>(2)

        var cal = Calendar.getInstance()
        cal[Calendar.HOUR_OF_DAY] = 0
        cal.clear(Calendar.MINUTE)
        cal.clear(Calendar.SECOND)
        cal.clear(Calendar.MILLISECOND)

        dateRange[0] = cal.time

        cal = Calendar.getInstance()
        cal[Calendar.HOUR_OF_DAY] = 23
        cal[Calendar.MINUTE] = 59
        cal[Calendar.SECOND] = 59
        cal[Calendar.MILLISECOND] = 999

        dateRange[1] = cal.time

        return dateRange
    }


    /* Returns the date range of the current week */
    fun currentWeekRange(): Array<Date?> {
        val dateRange = arrayOfNulls<Date>(2)

        var cal = Calendar.getInstance()

        cal.firstDayOfWeek = Calendar.MONDAY
        cal[Calendar.DAY_OF_WEEK] = Calendar.MONDAY
        cal[Calendar.HOUR_OF_DAY] = 0
        cal.clear(Calendar.MINUTE)
        cal.clear(Calendar.SECOND)
        cal.clear(Calendar.MILLISECOND)

        dateRange[0] = cal.time

        cal = Calendar.getInstance()
        cal.firstDayOfWeek = Calendar.MONDAY
        cal[Calendar.DAY_OF_WEEK] = Calendar.SUNDAY
        cal[Calendar.HOUR_OF_DAY] = 23
        cal[Calendar.MINUTE] = 59
        cal[Calendar.SECOND] = 59
        cal[Calendar.MILLISECOND] = 999

        dateRange[1] = cal.time

        return dateRange
    }

    /* Returns the date range of the current week */
    private fun pastWeekRange(): Array<Date?> {
        val dateRange = arrayOfNulls<Date>(2)

        var cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -7)

        dateRange[0] = cal.time

        cal = Calendar.getInstance()
        dateRange[1] = cal.time

        return dateRange
    }

    /* Returns the date range of the current month */
    fun currentMonthRange(): Array<Date?> {
        val dateRange = arrayOfNulls<Date>(2)

        var cal = Calendar.getInstance()

        cal[Calendar.DAY_OF_MONTH] = 1
        cal[Calendar.HOUR_OF_DAY] = 0
        cal.clear(Calendar.MINUTE)
        cal.clear(Calendar.SECOND)
        cal.clear(Calendar.MILLISECOND)

        dateRange[0] = cal.time

        cal = Calendar.getInstance()
        cal[Calendar.DAY_OF_MONTH] =
            Calendar.getInstance().getActualMaximum(Calendar.DAY_OF_MONTH)

        cal[Calendar.HOUR_OF_DAY] = 23
        cal[Calendar.MINUTE] = 59
        cal[Calendar.SECOND] = 59
        cal[Calendar.MILLISECOND] = 999

        dateRange[1] = cal.time

        return dateRange
    }

    /* Returns the date range of the current month */
    private fun pastMonthRange(): Array<Date?> {
        val dateRange = arrayOfNulls<Date>(2)

        var cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, -1)
        dateRange[0] = cal.time

        cal = Calendar.getInstance()
        dateRange[1] = cal.time

        return dateRange
    }

    /* Returns the date range of the current year */
    fun currentYearRange(): Array<Date?> {
        val dateRange = arrayOfNulls<Date>(2)

        var cal = Calendar.getInstance()
        cal[Calendar.MONTH] = Calendar.JANUARY
        cal[Calendar.DAY_OF_MONTH] = 1
        cal[Calendar.HOUR_OF_DAY] = 0
        cal.clear(Calendar.MINUTE)
        cal.clear(Calendar.SECOND)
        cal.clear(Calendar.MILLISECOND)

        dateRange[0] = cal.time

        cal = Calendar.getInstance()
        cal[Calendar.MONTH] = Calendar.DECEMBER
        cal[Calendar.DAY_OF_MONTH] =
            Calendar.getInstance().getActualMaximum(Calendar.DAY_OF_MONTH)
        cal[Calendar.HOUR_OF_DAY] = 23
        cal[Calendar.MINUTE] = 59
        cal[Calendar.SECOND] = 59
        cal[Calendar.MILLISECOND] = 999

        dateRange[1] = cal.time

        return dateRange
    }

    /* Returns the date range of the current year */
    private fun pastYearRange(): Array<Date?> {
        val dateRange = arrayOfNulls<Date>(2)

        var cal = Calendar.getInstance()
        cal.add(Calendar.YEAR, -1)
        dateRange[0] = cal.time

        cal = Calendar.getInstance()
        dateRange[1] = cal.time

        return dateRange
    }

    private fun printDateRange(title: String, range: Array<Date?>) {
        Log.d("TimeHelper ", title + range[0].toString() + " - " + range[1].toString())
    }

    fun testTimeHelper() {
        printDateRange("pastHourRange: ", pastHourRange())
        printDateRange("currentHourRange: ", currentHourRange())
        printDateRange("pastDayRange: ", pastDayRange())
        printDateRange("currentDayRange: ", currentDayRange())
        printDateRange("currentWeekRange: ", currentWeekRange())
        printDateRange("pastWeekRange: ", pastWeekRange())
        printDateRange("currentMonthRange: ", currentMonthRange())
        printDateRange("pastMonthRange: ", pastMonthRange())
        printDateRange("currentYearRange: ", currentYearRange())
        printDateRange("pastYearRange: ", pastYearRange())
    }

    fun genDate(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int): Date {
        val cal = Calendar.getInstance()
        cal[Calendar.YEAR] = year
        cal[Calendar.MONTH] = month
        cal[Calendar.DAY_OF_MONTH] = day
        cal[Calendar.HOUR_OF_DAY] = hour
        cal[Calendar.MINUTE] = minute
        cal[Calendar.SECOND] = second
        cal[Calendar.MILLISECOND] = 0
        return cal.time
    }
}
