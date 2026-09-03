@file:JsQualifier("foundry.abstract")

package com.foundryvtt.core.abstract

import com.foundryvtt.core.AnyObject
import kotlin.js.Promise


abstract external class DataModel(
    data: AnyObject? = definedExternally,
    options: DocumentConstructionContext? = definedExternally
) {
    // Static members (defineSchema, schema, cleanData, fromSource) are deliberately unbound: Kotlin/JS
    // statics on an abstract external class need a companion, and nothing calls them yet.
    open fun toObject(source: Boolean = definedExternally): AnyObject
    open fun toJSON(): AnyObject
    open fun reset()
    open fun clone(data: AnyObject? = definedExternally, context: AnyObject? = definedExternally): Promise<Document>
    open fun updateSource(changes: AnyObject? = definedExternally, options: AnyObject? = definedExternally): AnyObject
    open fun validate(options: DataModelValidationOptions? = definedExternally)
    open val parent: DataModel?
    val _source: AnyObject
    val invalid: Boolean
    val validationFailures: DataModelValidationFailures
}