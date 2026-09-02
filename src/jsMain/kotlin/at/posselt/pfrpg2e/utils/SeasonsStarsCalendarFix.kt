package at.posselt.pfrpg2e.utils

/**
 * Works around a Seasons & Stars 0.26 load-order race.
 *
 * S&S loads external calendar packs (e.g. the PF2e Golarion calendar from `seasons-and-stars-pf2e`)
 * **asynchronously** during the `init` hook, but it restores the saved active calendar
 * **synchronously** in `setup` (`calendarManager.setActiveCalendarSync(...)`). When the async pack
 * load loses the race, S&S logs `Calendar not found: golarion-pf2e(absalom-reckoning)` and silently
 * falls back to the Gregorian calendar — so dates, weather seasons and our calendar logging all use
 * the wrong calendar.
 *
 * By the time the world is `ready` (and certainly by `seasons-stars:ready`) the pack calendar IS
 * registered, so we simply re-apply the user's saved selection if S&S fell back to something else.
 * This only re-applies the calendar the user already chose; it never overrides an intentional
 * choice, and it no-ops when S&S is absent or already correct. All access is feature-detected so a
 * future S&S API change degrades to a harmless no-op rather than throwing.
 */
fun fixSeasonsStarsActiveCalendar() {
    js(
        """
        (function () {
            function apply() {
                try {
                    var ss = (typeof game !== 'undefined') && game.seasonsStars;
                    var mgr = ss && ss.manager;
                    if (!mgr || typeof mgr.getActiveCalendar !== 'function') return true;
                    var saved = game.settings.get('seasons-and-stars', 'activeCalendar');
                    if (!saved) return true;
                    // S&S variant calendars carry the variant IN the id ("golarion-pf2e(absalom-reckoning)"),
                    // so the saved id must be compared whole. Comparing only the base id read a
                    // correctly selected variant as "fallen back" and re-applied it on every load.
                    var savedFull = String(saved);
                    var savedBase = savedFull.split('(')[0];
                    var active = mgr.getActiveCalendar();
                    if (active && (active.id === savedFull || active.id === savedBase)) return true;
                    var all = (typeof mgr.getAllCalendars === 'function') ? mgr.getAllCalendars() : [];
                    var known = false;
                    for (var i = 0; i < (all ? all.length : 0); i++) {
                        if (all[i] && (all[i].id === savedFull || all[i].id === savedBase)) { known = true; break; }
                    }
                    if (!known) return false;
                    console.log('[pf2e-kingmaker-tools] Re-applying Seasons & Stars active calendar "' + saved + '" (S&S 0.26 had fallen back to "' + (active && active.id) + '" due to an async calendar-pack load race)');
                    if (typeof mgr.setActiveCalendar === 'function') { mgr.setActiveCalendar(saved); }
                    else if (typeof mgr.setActiveCalendarSync === 'function') { mgr.setActiveCalendarSync(saved); }
                    return true;
                } catch (e) {
                    console.error('[pf2e-kingmaker-tools] failed to re-apply Seasons & Stars active calendar', e);
                    return true;
                }
            }
            if (!apply() && typeof Hooks !== 'undefined') {
                Hooks.once('seasons-stars:ready', apply);
            }
        })();
        """
    )
}
