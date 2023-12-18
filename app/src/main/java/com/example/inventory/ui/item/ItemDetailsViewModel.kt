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

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.security.crypto.EncryptedFile
import com.example.inventory.MAIN
import com.example.inventory.MASTER_KEY
import com.example.inventory.data.ItemsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.google.gson.Gson
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
/**
 * ViewModel to retrieve, update and delete an item from the [ItemsRepository]'s data source.
 */
class ItemDetailsViewModel(
    savedStateHandle: SavedStateHandle,
    private val itemsRepository: ItemsRepository,
) : ViewModel() {

    private val itemId: Int = checkNotNull(savedStateHandle[ItemDetailsDestination.itemIdArg])

    /**
     * Holds the item details ui state. The data is retrieved from [ItemsRepository] and mapped to
     * the UI state.
     */
    val uiState: StateFlow<ItemDetailsUiState> =
        itemsRepository.getItemStream(itemId)
            .filterNotNull()
            .map {
                ItemDetailsUiState(outOfStock = it.quantity <= 0, itemDetails = it.toItemDetails())
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(TIMEOUT_MILLIS),
                initialValue = ItemDetailsUiState()
            )

    /**
     * Reduces the item quantity by one and update the [ItemsRepository]'s data source.
     */
    fun reduceQuantityByOne() {
        viewModelScope.launch {
            val currentItem = uiState.value.itemDetails.toItem()
            if (currentItem.quantity > 0) {
                itemsRepository.updateItem(currentItem.copy(quantity = currentItem.quantity - 1))
            }
        }
    }

    /**
     * Deletes the item from the [ItemsRepository]'s data source.
     */
    suspend fun deleteItem() {
        itemsRepository.deleteItem(uiState.value.itemDetails.toItem())
    }

    @SuppressLint("Recycle")
    fun saveToFile(uri: Uri) {
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

        encryptedFile.openFileOutput().apply {
            val jsonItem = Gson().toJson(uiState.value.itemDetails.toItem())
            write(jsonItem.toByteArray())
            close()
        }

        contentResolver.openFileDescriptor(uri, "w")?.use { descriptor ->
            FileOutputStream(descriptor.fileDescriptor).use { outputStream ->
                file.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                    inputStream.close()
                }
                outputStream.close()
            }
        }
        file.delete()

    }

    fun share() {
        val sharingIntent = Intent(Intent.ACTION_SEND)
        sharingIntent.type = "text/plain"

        var sharingText = """
            Item Details:
            Name: ${uiState.value.itemDetails.name}
            Price: ${uiState.value.itemDetails.price} $
            Quantity: ${uiState.value.itemDetails.quantity}
        """.trimIndent()
            if (uiState.value.itemDetails.providerName.isNotBlank() ||
            uiState.value.itemDetails.providerPhoneNumber.isNotBlank() ||
            uiState.value.itemDetails.providerEmail.isNotBlank()) {

                sharingText += "\n"
            sharingText += """
                
                 Provider Info:
                """.trimIndent()

        }
        if (uiState.value.itemDetails.providerName.isNotBlank()){

            sharingText += "\n"
            sharingText += """
                Name: ${uiState.value.itemDetails.providerName}
                """.trimIndent()
        }
        if (uiState.value.itemDetails.providerPhoneNumber.isNotBlank()){
            sharingText += "\n"
            sharingText += """
                Phone Number: ${uiState.value.itemDetails.providerPhoneNumber}
                """.trimIndent()
        }
        if (uiState.value.itemDetails.providerEmail.isNotBlank()){
            sharingText += "\n"
            sharingText += """
                Email: ${uiState.value.itemDetails.providerEmail}
                """.trimIndent()
        }

        sharingIntent.putExtra(Intent.EXTRA_TEXT, sharingText)

        MAIN.startActivity(Intent.createChooser(sharingIntent, null))
    }


    companion object {
        private const val TIMEOUT_MILLIS = 5_000L
    }
}

/**
 * UI state for ItemDetailsScreen
 */
data class ItemDetailsUiState(
    val outOfStock: Boolean = true,
    val itemDetails: ItemDetails = ItemDetails()
)
