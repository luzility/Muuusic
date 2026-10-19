package me.luzility.muuusic.ui

import kotlinx.coroutines.CancellationException

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val throwable: Throwable) : UiState<Nothing>

    companion object {
        suspend fun <T> of(block: suspend () -> T): UiState<T> = try {
            Success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Error(e)
        }
    }
}
