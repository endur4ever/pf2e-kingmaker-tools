@file:JsQualifier("foundry.documents")
package com.foundryvtt.core.documents

import com.foundryvtt.core.AnyObject
import com.foundryvtt.core.abstract.DatabaseDeleteOperation
import com.foundryvtt.core.abstract.DatabaseUpdateOperation
import com.foundryvtt.core.abstract.Document
import com.foundryvtt.core.abstract.DocumentConstructionContext
import kotlin.js.Promise

/**
 * Minimal binding: identity and placement only, typed from the served v14.363 schema
 * (2026-09-02). Add members from the served bundle as they are needed, never from memory.
 */
external class AmbientSoundDocument(
    data: AnyObject = definedExternally,
    options: DocumentConstructionContext = definedExternally
) : Document {
    companion object : DocumentStatic<AmbientSoundDocument>;

    override fun delete(operation: DatabaseDeleteOperation): Promise<AmbientSoundDocument>
    override fun update(data: AnyObject, operation: DatabaseUpdateOperation): Promise<AmbientSoundDocument?>

    var _id: String
    var x: Double
    var y: Double
    var elevation: Double
    var radius: Double
    var hidden: Boolean
    var path: String?
}
