package maryk.core.properties.types

import maryk.core.aggregations.bucket.DateUnit.Centuries
import maryk.core.aggregations.bucket.DateUnit.Days
import maryk.core.aggregations.bucket.DateUnit.Decades
import maryk.core.aggregations.bucket.DateUnit.Hours
import maryk.core.aggregations.bucket.DateUnit.Millennia
import maryk.core.aggregations.bucket.DateUnit.Millis
import maryk.core.aggregations.bucket.DateUnit.Minutes
import maryk.core.aggregations.bucket.DateUnit.Months
import maryk.core.aggregations.bucket.DateUnit.Quarters
import maryk.core.aggregations.bucket.DateUnit.Seconds
import maryk.core.aggregations.bucket.DateUnit.Years
import maryk.core.aggregations.bucket.roundToDateUnit
import maryk.lib.time.Time
import kotlin.test.Test
import kotlin.test.expect

class DateUnitTest {
    @Test
    fun roundDateTime() {
        val dateTime = DateTime(2019, 6, 8, 13, 45, 23, 999)

        expect(dateTime) {
            dateTime.roundToDateUnit(Millis)
        }

        expect(DateTime(2019, 6, 8, 13, 45, 23)) {
            dateTime.roundToDateUnit(Seconds)
        }

        expect(DateTime(2019, 6, 8, 13, 45)) {
            dateTime.roundToDateUnit(Minutes)
        }

        expect(DateTime(2019, 6, 8, 13)) {
            dateTime.roundToDateUnit(Hours)
        }

        expect(DateTime(2019, 6, 8)) {
            dateTime.roundToDateUnit(Days)
        }

        expect(DateTime(2019, 6)) {
            dateTime.roundToDateUnit(Months)
        }

        expect(DateTime(2019, 4)) {
            dateTime.roundToDateUnit(Quarters)
        }

        expect(DateTime(2019)) {
            dateTime.roundToDateUnit(Years)
        }

        expect(DateTime(2010)) {
            dateTime.roundToDateUnit(Decades)
        }

        expect(DateTime(1900)) {
            DateTime(1912).roundToDateUnit(Centuries)
        }

        expect(DateTime(1000)) {
            DateTime(1912).roundToDateUnit(Millennia)
        }
    }

    @Test
    fun roundDate() {
        val date = Date(2019, 6, 8)

        expect(date) {
            date.roundToDateUnit(Millis)
        }

        expect(Date(2019, 6, 8)) {
            date.roundToDateUnit(Seconds)
        }

        expect(Date(2019, 6, 8)) {
            date.roundToDateUnit(Minutes)
        }

        expect(Date(2019, 6, 8)) {
            date.roundToDateUnit(Hours)
        }

        expect(Date(2019, 6, 8)) {
            date.roundToDateUnit(Days)
        }

        expect(Date(2019, 6)) {
            date.roundToDateUnit(Months)
        }

        expect(Date(2019, 4)) {
            date.roundToDateUnit(Quarters)
        }

        expect(Date(2019)) {
            date.roundToDateUnit(Years)
        }

        expect(Date(2010)) {
            date.roundToDateUnit(Decades)
        }

        expect(Date(1900)) {
            Date(1912).roundToDateUnit(Centuries)
        }

        expect(Date(1000)) {
            Date(1912).roundToDateUnit(Millennia)
        }
    }

    @Test
    fun roundTime() {
        val time = Time(13, 45, 23, 999)

        expect(time) {
            time.roundToDateUnit(Millis)
        }

        expect(Time(13, 45, 23)) {
            time.roundToDateUnit(Seconds)
        }

        expect(Time(13, 45)) {
            time.roundToDateUnit(Minutes)
        }

        expect(Time(13, 0)) {
            time.roundToDateUnit(Hours)
        }

        expect(Time.MIDNIGHT) {
            time.roundToDateUnit(Days)
        }

        expect(Time.MIDNIGHT) {
            time.roundToDateUnit(Months)
        }

        expect(Time.MIDNIGHT) {
            time.roundToDateUnit(Quarters)
        }

        expect(Time.MIDNIGHT) {
            time.roundToDateUnit(Years)
        }

        expect(Time.MIDNIGHT) {
            time.roundToDateUnit(Decades)
        }

        expect(Time.MIDNIGHT) {
            time.roundToDateUnit(Centuries)
        }

        expect(Time.MIDNIGHT) {
            time.roundToDateUnit(Millennia)
        }
    }
}
