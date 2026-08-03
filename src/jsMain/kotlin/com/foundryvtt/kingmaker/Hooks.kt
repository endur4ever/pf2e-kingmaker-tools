package com.foundryvtt.kingmaker

import com.foundryvtt.core.AnyObject
import com.foundryvtt.core.helpers.HooksEventListener
import org.w3c.dom.HTMLElement

typealias KingmakerHexEditApp = Any

fun <O> HooksEventListener.onCloseKingmakerHexEdit(callback: (KingmakerHexEditApp, HTMLElement) -> O) =
    on("closeKingmakerHexEdit", callback)

// pf2e-kingmaker 2.3.x renders its native hex editor as the ApplicationV2 class `HexEditor`,
// so Foundry fires render/close hooks under that name. `app` is the HexEditor instance
// (its `options.hex` carries the KingmakerHex), `html` is the rendered form element.
fun <O> HooksEventListener.onRenderHexEditor(callback: (app: AnyObject, html: HTMLElement, context: AnyObject) -> O) =
    on("renderHexEditor", callback)

// The native hover tooltip class is `HexHUD` in km 2.3.x, so Foundry fires `renderHexHUD`
// (the older `renderKingmakerHexHUD` binding above is stale). `app.hex` is the hovered KingmakerHex.
fun <O> HooksEventListener.onRenderHexHud(callback: (app: AnyObject, html: HTMLElement, context: AnyObject) -> O) =
    on("renderHexHUD", callback)

fun <O> HooksEventListener.onRenderKingmakerHexHud(callback: (app: KingmakerHexHud, html: HTMLElement, messageData: AnyObject) -> O) =
    on("renderKingmakerHexHUD", callback)