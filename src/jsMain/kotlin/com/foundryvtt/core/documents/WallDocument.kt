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
external class WallDocument(
    data: AnyObject = definedExternally,
    options: DocumentConstructionContext = definedExternally
) : Document {
    companion object : DocumentStatic<WallDocument>;

    override fun delete(operation: DatabaseDeleteOperation): Promise<WallDocument>
    override fun update(data: AnyObject, operation: DatabaseUpdateOperation): Promise<WallDocument?>

    var _id: String
    /** [x0, y0, x1, y1] */
    var c: Array<Double>
    var move: Int
    var sight: Int
    var light: Int
    var sound: Int
    var door: Int
    var ds: Int
}
