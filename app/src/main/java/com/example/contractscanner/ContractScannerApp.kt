package com.example.contractscanner

import android.app.Application
import com.example.contractscanner.data.AppDatabase
import com.example.contractscanner.data.ContractRepository

class ContractScannerApp : Application() {

    val database by lazy { AppDatabase.getDatabase(this) }
    val repository by lazy { ContractRepository(database.contractDao()) }
}
