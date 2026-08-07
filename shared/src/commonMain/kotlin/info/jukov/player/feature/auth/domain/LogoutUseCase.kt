package info.jukov.player.feature.auth.domain

import info.jukov.player.feature.download.domain.DownloadsRepository

class LogoutUseCase(
    private val repository: AuthRepository,
    private val downloadsRepository: DownloadsRepository,
) {
    suspend operator fun invoke() {
        downloadsRepository.clearCurrentAccount()
        repository.logout()
    }
}
