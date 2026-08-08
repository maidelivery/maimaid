package net.krtl.maimaid.core.domain

sealed interface Result<out T, out E> {
    data class Ok<T>(val value: T) : Result<T, Nothing>
    data class Err<E>(val error: E) : Result<Nothing, E>
}

sealed interface DomainError {
    data class Network(val message: String) : DomainError
    data class Unauthorized(val message: String = "Unauthorized") : DomainError
    data class Validation(val message: String) : DomainError
    data class Conflict(val message: String) : DomainError
    data class Server(val code: Int, val message: String) : DomainError
    data class Unknown(val message: String) : DomainError
}

sealed interface UiState<out T> {
    data object Idle : UiState<Nothing>
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val error: DomainError) : UiState<Nothing>
}

