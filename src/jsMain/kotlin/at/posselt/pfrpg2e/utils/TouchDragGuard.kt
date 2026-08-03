package at.posselt.pfrpg2e.utils

/**
 * Defensive band-aid for an upstream Foundry/PF2e bug, NOT a feature of this module.
 *
 * On touch devices, a cancelled token drag (finger lift, second touch, pointer-leave) routes
 * into Foundry core's private `#onDragLeftCancel`, which calls `TokenPF2e._finalizeDragLeft`.
 * Because touch never sets up the drag state the way the mouse path does, that method runs
 * `Object.values(undefined)` and throws:
 *
 *     Uncaught TypeError: Cannot convert undefined or null to object
 *         at TokenPF2e._finalizeDragLeft (foundry.mjs)
 *
 * The throw is non-fatal (the token doesn't move) but spams the console and aborts the
 * cancel cleanup. The real fix for usable touch dragging is the Touch VTT module; this guard
 * only silences the crash so mobile play isn't littered with red errors.
 *
 * It is deliberately surgical: it wraps the canvas token class's drag-finalize method in a
 * try/catch. Normal desktop drags set up their state correctly and never throw, so they run
 * unchanged — only the broken touch-cancel path is swallowed. Wrapping is idempotent.
 */
fun registerTouchDragGuard() {
    js(
        """
        (function () {
            try {
                var cfg = (typeof CONFIG !== 'undefined') ? CONFIG : null;
                var tokenClass = (cfg && cfg.Token) ? cfg.Token.objectClass : null;
                var proto = tokenClass ? tokenClass.prototype : null;
                if (!proto || proto.__pf2eKmtDragGuard) return;
                proto.__pf2eKmtDragGuard = true;
                var methods = ['_finalizeDragLeft', '_onDragLeftCancel'];
                for (var i = 0; i < methods.length; i++) {
                    var name = methods[i];
                    var orig = proto[name];
                    if (typeof orig !== 'function') continue;
                    (function (name, orig) {
                        proto[name] = function () {
                            try {
                                return orig.apply(this, arguments);
                            } catch (e) {
                                console.warn(
                                    'pf2e-kingmaker-tools: suppressed touch drag-cancel error in '
                                        + name + ' (upstream Foundry/PF2e touch bug)', e);
                                return undefined;
                            }
                        };
                    })(name, orig);
                }
            } catch (e) {
                console.warn('pf2e-kingmaker-tools: touch drag guard install failed', e);
            }
        })();
        """
    )
}
