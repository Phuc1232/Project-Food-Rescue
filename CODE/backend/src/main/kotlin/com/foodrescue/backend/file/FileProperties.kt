// AC-01,03,07: FileProperties responsibility.
package com.foodrescue.backend.file

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.util.unit.DataSize

/** Storage root outside the source tree (roadmap D4) and the business size limit per uploaded file. */
@ConfigurationProperties("files")
data class FileProperties(val storageDir: String, val maxSize: DataSize = DataSize.ofMegabytes(10))
