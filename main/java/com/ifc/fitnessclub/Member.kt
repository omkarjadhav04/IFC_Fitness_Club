package com.ifc.fitnessclub

data class Member(
    val id: String = "",
    val name: String = "",
    val phone: String = "",
    val plan: String = "",
    val address: String = "",
    val amountPaid: Double = 0.0,
    val pendingAmount: Double = 0.0,
    val joinDate: String = "",
    val status: String = "Active"
)