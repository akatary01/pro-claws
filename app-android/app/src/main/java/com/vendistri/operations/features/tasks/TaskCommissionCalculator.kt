package com.vendistri.operations.features.tasks

import com.vendistri.operations.features.location.AppLocation
import com.vendistri.operations.features.location.LocationCommissionRoundingMode
import com.vendistri.operations.features.location.LocationCommissionType
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.round

object TaskCommissionCalculator {
    fun includeRefundsInCommission(location: AppLocation?): Boolean? {
        val commission = location?.commission ?: return null
        return !(commission.excludeRefundsFromCommission ?: true)
    }

    fun commissionPercentText(location: AppLocation?): String? {
        val value = location?.commission?.value ?: return null
        return "%.2f%%".format(value)
    }

    fun commissionPercentText(gross: Double, commission: Double): String? {
        if (kotlin.math.abs(gross) < 0.01) return null
        return "%.2f%%".format((commission / gross) * 100.0)
    }

    fun calculatedCommission(
        gross: Double,
        refunds: Double,
        cardTransactionSurchargeTotal: Double = 0.0,
        location: AppLocation?
    ): Double? {
        val commission = location?.commission ?: return null
        if (commission.type == LocationCommissionType.None) return 0.0
        val value = commission.value ?: return null
        val includeRefunds = !(commission.excludeRefundsFromCommission ?: true)
        val surcharge = if (commission.excludeCardTransactionSurchargeFromCommission ?: true) {
            cardTransactionSurchargeTotal
        } else {
            0.0
        }
        val base = maxOf((if (includeRefunds) gross else gross - refunds) - surcharge, 0.0)
        return when (commission.type) {
            LocationCommissionType.PercentGrossSales -> applyRounding(base * (value / 100.0), commission.roundingMode)
            LocationCommissionType.PercentGrossProfit -> {
                val profitMargin = commission.profitMarginPercent ?: 0.0
                applyRounding(base * (value / 100.0) * (profitMargin / 100.0), commission.roundingMode)
            }
            LocationCommissionType.Monthly -> roundCurrency(value)
            LocationCommissionType.None -> 0.0
        }
    }

    fun calculatedNet(gross: Double, refunds: Double, commission: Double): Double {
        return roundCurrency(gross - refunds - commission)
    }

    fun grossBreakdownValid(gross: Double, grossCash: Double, grossCard: Double): Boolean {
        return kotlin.math.abs((grossCash + grossCard) - gross) < 0.01
    }

    fun recalculatedGrossFromBreakdown(grossCash: Double, grossCard: Double): Double {
        return roundCurrency(grossCash + grossCard)
    }

    fun canRecalculate(
        gross: Double,
        refunds: Double,
        commission: Double,
        cardTransactionSurchargeTotal: Double = 0.0,
        location: AppLocation?
    ): Boolean {
        val calculated = calculatedCommission(gross, refunds, cardTransactionSurchargeTotal, location) ?: return false
        return kotlin.math.abs(calculated - commission) >= 0.01
    }

    fun canManuallyRecalculateCommission(
        autoCalcCommission: Boolean,
        gross: Double,
        refunds: Double,
        commission: Double,
        cardTransactionSurchargeTotal: Double = 0.0,
        location: AppLocation?
    ): Boolean {
        return !autoCalcCommission && canRecalculate(
            gross = gross,
            refunds = refunds,
            commission = commission,
            cardTransactionSurchargeTotal = cardTransactionSurchargeTotal,
            location = location
        )
    }

    private fun applyRounding(value: Double, mode: LocationCommissionRoundingMode?): Double {
        return when (mode) {
            LocationCommissionRoundingMode.NearestDollar -> roundCurrency(round(value))
            LocationCommissionRoundingMode.UpToDollar -> roundCurrency(ceil(value))
            LocationCommissionRoundingMode.DownToDollar -> roundCurrency(floor(value))
            LocationCommissionRoundingMode.None, null -> roundCurrency(value)
        }
    }
}
