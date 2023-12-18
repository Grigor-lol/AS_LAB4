/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.inventory.ui.item

import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.security.crypto.EncryptedFile
import com.example.inventory.MAIN
import com.example.inventory.MASTER_KEY
import com.example.inventory.data.Item
import com.example.inventory.data.ItemsRepository
import com.example.inventory.data.Settings
import com.example.inventory.data.SourceType
import com.google.gson.Gson
import java.io.File
import java.io.FileInputStream
import java.text.NumberFormat

/**
 * ViewModel to validate and insert items in the Room database.
 */
class ItemEntryViewModel(private val itemsRepository: ItemsRepository) : ViewModel() {

    /**
     * Holds current item ui state
     */
    var itemUiState by mutableStateOf(ItemUiState())
        private set

    /**
     * Updates the [itemUiState] with the value provided in the argument. This method also triggers
     * a validation for input values.
     */
    fun updateUiState(itemDetails: ItemDetails) {
        itemUiState =
            ItemUiState(itemDetails = itemDetails, isEntryValid = validateInput(itemDetails),
                isPhoneValid = validatePhone(itemDetails),
                isEmailValid = validateEmail(itemDetails))
    }

    /**
     * Inserts an [Item] in the Room database
     */
    suspend fun saveItem() {
        if (validateInput()) {
            itemsRepository.insertItem(itemUiState.itemDetails.toItem())
        }
    }

    private fun validateInput(uiState: ItemDetails = itemUiState.itemDetails): Boolean {
        return with(uiState) {
            name.isNotBlank() && price.isNotBlank() && quantity.isNotBlank()
                    && validatePhone(uiState)
                    && validateEmail(uiState)
        }
    }

    private val validEmailRegex = Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
    private val validPhoneNumberRegex = Regex("^8\\d{10}$")


    private fun validatePhone(uiState: ItemDetails = itemUiState.itemDetails): Boolean {
        return with(uiState) {
            !(providerPhoneNumber.isNotBlank() && !validPhoneNumberRegex.matches(providerPhoneNumber))
        }
    }

    private fun validateEmail(uiState: ItemDetails = itemUiState.itemDetails): Boolean {
        return with(uiState) {
            !(providerEmail.isNotBlank() && !validEmailRegex.matches(providerEmail))
        }
    }

    suspend fun loadFromFile(uri: Uri) {
        val contentResolver = MAIN.applicationContext.contentResolver

        val file = File(MAIN.applicationContext.cacheDir, "temp.json")
        if (file.exists())
            file.delete()

        val encryptedFile = EncryptedFile.Builder(
            MAIN.applicationContext,
            file,
            MASTER_KEY,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()

        file.outputStream().use { outputStream ->
            contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                FileInputStream(descriptor.fileDescriptor).use { inputStream ->
                    inputStream.copyTo(outputStream)
                    inputStream.close()
                }
                outputStream.close()
            }
        }

        encryptedFile.openFileInput().use { inputStream ->
            val jsonItem = String(inputStream.readBytes())
            val gson = Gson()
            val item = gson.fromJson(jsonItem, Item::class.java)
            item.sourceType = SourceType.File
            var curName = item.name
            var i = 1
            while (itemsRepository.isExists(curName)) {
                curName = "${item.name} ($i)"
                i++
            }

            val finalItem = Item(
                id = 0,
                name = curName,
                price = item.price,
                quantity = item.quantity,
                providerEmail = item.providerEmail,
                providerPhoneNumber = item.providerPhoneNumber,
                providerName = item.providerName,
                sourceType = item.sourceType,

                )
            itemsRepository.insertItem(finalItem)

            file.delete()
        }
    }
}

/**
 * Represents Ui State for an Item.
 */
data class ItemUiState(
    val itemDetails: ItemDetails = if (!Settings.enableDefaultFields)
        ItemDetails()
    else ItemDetails(
        id = 0,
        name = "",
        price = "",
        quantity = "",
        providerName = Settings.defaultProviderName,
        providerPhoneNumber = Settings.defaultProviderPhoneNumber,
        providerEmail = Settings.defaultProviderEmail,
        sourceType = SourceType.Manual,
    ),
    val isEntryValid: Boolean = false,
    val isPhoneValid: Boolean = true,
    val isEmailValid: Boolean = true,
)

data class ItemDetails(
    val id: Int = 0,
    val name: String = "",
    val price: String = "",
    val quantity: String = "",
    val providerName: String = "",
    val providerEmail: String = "",
    val providerPhoneNumber: String = "",
    val sourceType: SourceType = SourceType.Manual
    )

/**
 * Extension function to convert [ItemUiState] to [Item]. If the value of [ItemDetails.price] is
 * not a valid [Double], then the price will be set to 0.0. Similarly if the value of
 * [ItemUiState] is not a valid [Int], then the quantity will be set to 0
 */
fun ItemDetails.toItem(): Item {
    return Item(
        id = id,
        name = name,
        price = price.toDoubleOrNull() ?: 0.0,
        quantity = quantity.toIntOrNull() ?: 0,
        providerEmail = providerEmail,
        providerPhoneNumber = providerPhoneNumber,
        providerName = providerName,
        sourceType = sourceType,

    )
}

fun Item.formatedPrice(): String {
    return NumberFormat.getCurrencyInstance().format(price)
}

/**
 * Extension function to convert [Item] to [ItemUiState]
 */
fun Item.toItemUiState(isEntryValid: Boolean = false): ItemUiState = ItemUiState(
    itemDetails = this.toItemDetails(),
    isEntryValid = isEntryValid
)

/**
 * Extension function to convert [Item] to [ItemDetails]
 */
fun Item.toItemDetails(): ItemDetails = ItemDetails(
    id = id,
    name = name,
    price = price.toString(),
    quantity = quantity.toString(),
    providerName = providerName,
    providerEmail = providerEmail,
    providerPhoneNumber = providerPhoneNumber,
    sourceType = sourceType,
)
