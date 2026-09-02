package com.foundryvtt.kingmaker

import com.foundryvtt.core.AnyObject
import com.foundryvtt.core.helpers.HooksEventListener
import org.w3c.dom.HTMLElement

typealias KingmakerHexEditApp = Any

/**
 * STALE — kept only so old call sites compile. pf2e-kingmaker 2.3.x's editor is the ApplicationV2
 * class `HexEditor`, so Foundry fires `closeHexEditor`; nothing has fired `closeKingmakerHexEdit`
 * since that rename, and every listener bound here was silently dead. Use [onCloseHexEditor].
 */
@Deprecated("Foundry fires closeHexEditor for pf2e-kingmaker 2.3.x; this name never fires", ReplaceWith("onCloseHexEditor(callback)"))
fun <O> HooksEventListener.onCloseKingmakerHexEdit(callback: (KingmakerHexEditApp, HTMLElement) -> O) =
    on("closeKingmakerHexEdit", callback)

/** The native hex editor closed; `app.options.hex` is the KingmakerHex it was editing. */
fun <O> HooksEventListener.onCloseHexEditor(callback: (app: AnyObject, html: HTMLElement) -> O) =
    on("closeHexEditor", callback)

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