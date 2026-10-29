package com.team11.labdip.data.model

enum class Status { Pass, Marginal, Fail }

/** Firmware sends ΔE only; the verdict is decided here with the user's limits. */
fun statusFor(deltaE: Double, passMax: Double, marginalMax: Double): Status = when {
    deltaE <= passMax -> Status.Pass
    deltaE <= marginalMax -> Status.Marginal
    else -> Status.Fail
}
