package com.romulus.spikes.spike3.errors

import com.romulus.spikes.spike3.model.CaseFailure
import com.romulus.spikes.spike3.model.FailureStage
import org.apache.commons.compress.archivers.ArchiveException
import org.apache.commons.compress.archivers.zip.UnsupportedZipFeatureException
import java.io.IOException
import java.util.zip.ZipException

object FailureCodes {
    const val RANGE_NOT_SUPPORTED = "RANGE_NOT_SUPPORTED"
    const val RANGE_WINDOW_REJECTED = "RANGE_WINDOW_REJECTED"
    const val FULL_BODY_RESPONSE = "FULL_BODY_RESPONSE"
    const val INVALID_HTTP_RESPONSE = "INVALID_HTTP_RESPONSE"
    const val INVALID_ZIP = "INVALID_ZIP"
    const val UNSUPPORTED_ENCRYPTION = "UNSUPPORTED_ENCRYPTION"
    const val UNSUPPORTED_ZIP_FEATURE = "UNSUPPORTED_ZIP_FEATURE"
    const val SELECTED_ENTRY_MISSING = "SELECTED_ENTRY_MISSING"
    const val FILTER_SELECTION_MISMATCH = "FILTER_SELECTION_MISMATCH"
    const val ARTIFACT_WRITE_FAILED = "ARTIFACT_WRITE_FAILED"
}

class Spike3FailureException(
    val stage: FailureStage,
    val errorCode: String,
    override val message: String,
    override val cause: Throwable? = null,
) : IOException(message, cause)

fun Throwable.toCaseFailure(caseId: String, fallbackStage: FailureStage): CaseFailure {
    return when (this) {
        is Spike3FailureException -> CaseFailure(
            caseId = caseId,
            stage = stage,
            errorCode = errorCode,
            message = message,
            causeClass = cause?.javaClass?.name,
        )

        is UnsupportedZipFeatureException -> {
            val errorCode = if (entry?.generalPurposeBit?.usesEncryption() == true) {
                FailureCodes.UNSUPPORTED_ENCRYPTION
            } else {
                FailureCodes.UNSUPPORTED_ZIP_FEATURE
            }
            CaseFailure(
                caseId = caseId,
                stage = fallbackStage,
                errorCode = errorCode,
                message = message ?: errorCode,
                causeClass = javaClass.name,
            )
        }

        is ZipException, is ArchiveException -> CaseFailure(
            caseId = caseId,
            stage = fallbackStage,
            errorCode = FailureCodes.INVALID_ZIP,
            message = message ?: FailureCodes.INVALID_ZIP,
            causeClass = javaClass.name,
        )

        else -> CaseFailure(
            caseId = caseId,
            stage = fallbackStage,
            errorCode = FailureCodes.INVALID_ZIP,
            message = message ?: javaClass.simpleName,
            causeClass = javaClass.name,
        )
    }
}
