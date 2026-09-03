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
external class AmbientLightDocument(
    data: AnyObject = definedExternally,
    options: DocumentConstructionContext = definedExternally
) : Document {
    companion object : DocumentStatic<AmbientLightDocument>;

    override fun delete(operation: DatabaseDeleteOperation): Promise<AmbientLightDocument>
    override fun update(data: AnyObject, operation: DatabaseUpdateOperation): Promise<AmbientLightDocument?>

    var _id: String
    var x: Double
    var y: Double
    var elevation: Double
    var rotation: Double
    var hidden: Boolean
}
