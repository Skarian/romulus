package com.romulus.mobile.source.ingest

import android.content.Context
import com.networknt.schema.InputFormat
import com.networknt.schema.SchemaRegistry
import com.networknt.schema.dialect.Dialects
import java.nio.charset.StandardCharsets

internal class SourceSchemaValidator private constructor(private val schemaData: String) {
    private val schema = SchemaRegistry
        .withDialect(Dialects.getDraft202012())
        .getSchema(schemaData, InputFormat.JSON)

    fun validate(bytes: ByteArray): Result<Unit> = runCatching {
        val input = bytes.toString(StandardCharsets.UTF_8)
        val violations = schema.validate(input, InputFormat.JSON)
        check(violations.isEmpty()) {
            violations.joinToString(separator = "\n") { violation -> violation.message }
        }
    }

    companion object {
        private const val ASSET_NAME = "source-schema.json"

        fun create(context: Context): SourceSchemaValidator = fromSchemaData(
            schemaData = context.assets.open(ASSET_NAME).bufferedReader().use { reader ->
                reader.readText()
            }
        )

        fun fromSchemaData(schemaData: String): SourceSchemaValidator =
            SourceSchemaValidator(schemaData)
    }
}
