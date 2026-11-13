// AC-01,07: FileStorageConfiguration responsibility.
package com.foodrescue.backend.file.storage

import com.foodrescue.backend.file.FileProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.nio.file.Path

@Configuration
@EnableConfigurationProperties(FileProperties::class)
class FileStorageConfiguration {
    @Bean
    fun fileStorage(properties: FileProperties): FileStorage {
        require(properties.storageDir.isNotBlank()) { "files.storage-dir (FILE_STORAGE_DIR) must be configured" }
        return LocalDiskFileStorage(Path.of(properties.storageDir))
    }
}
