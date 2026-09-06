package at.posselt.pfrpg2e.fixtures

import at.posselt.pfrpg2e.Config
import at.posselt.pfrpg2e.camping.CampingActor
import at.posselt.pfrpg2e.camping.CampingData
import at.posselt.pfrpg2e.camping.getDefaultCamping
import at.posselt.pfrpg2e.kingdom.KingdomActor
import at.posselt.pfrpg2e.kingdom.KingdomData
import com.foundryvtt.core.Game
import com.foundryvtt.core.documents.User
import com.foundryvtt.core._del
import com.foundryvtt.core.utils.deepClone
import com.foundryvtt.pf2e.actor.PF2ECharacter
import com.foundryvtt.pf2e.item.PF2EConsumable
import js.objects.Record
import js.objects.unsafeJso
import kotlin.js.Promise

/**
 * Merges [source] into [target] using Foundry VTT document update semantics:
 *  - Dotted property paths ("a.b.c") traverse and merge into nested objects.
 *  - A property value of `_del` (or `__forcedDeletion: true`) deletes that property from target.
 *  - A property key starting with "-=" (e.g. "-=key") deletes "key" from target.
 *  - Objects merge recursively; primitives and arrays overwrite.
 *  - Unmentioned keys in target are preserved (classic merge behavior).
 */
fun applyFoundryMerge(target: dynamic, source: dynamic) {
    if (source == null || js("typeof source !== 'object'")) return
    val keys = js("Object.keys(source)").unsafeCast<Array<String>>()
    for (k in keys) {
        val v = source[k]
        if (k.indexOf('.') != -1) {
            val parts = k.split('.').toTypedArray()
            var curr = target
            for (i in 0 until parts.size - 1) {
                val seg = parts[i]
                if (curr[seg] == null || js("typeof curr[seg] !== 'object'")) {
                    curr[seg] = js("({})")
                }
                curr = curr[seg]
            }
            val lastSeg = parts.last()
            if (lastSeg.startsWith("-=")) {
                val prop = lastSeg.substring(2)
                js("delete curr[prop]")
            } else if (v === _del || (v != null && v.__forcedDeletion === true)) {
                js("delete curr[lastSeg]")
            } else if (v != null && js("typeof v === 'object'") && !js("Array.isArray(v)")) {
                if (curr[lastSeg] == null || js("typeof curr[lastSeg] !== 'object'")) {
                    curr[lastSeg] = js("({})")
                }
                applyFoundryMerge(curr[lastSeg], v)
            } else {
                curr[lastSeg] = v
            }
        } else {
            if (k.startsWith("-=")) {
                val prop = k.substring(2)
                js("delete target[prop]")
            } else if (v === _del || (v != null && v.__forcedDeletion === true)) {
                js("delete target[k]")
            } else if (v != null && js("typeof v === 'object'") && !js("Array.isArray(v)")) {
                if (target[k] == null || js("typeof target[k] !== 'object'")) {
                    target[k] = js("({})")
                }
                applyFoundryMerge(target[k], v)
            } else {
                target[k] = v
            }
        }
    }
}

class FakeNotifications {
    val warnings = mutableListOf<String>()
    val errors = mutableListOf<String>()
    val infos = mutableListOf<String>()

    fun warn(message: String, options: dynamic = null) {
        warnings.add(message)
    }

    fun error(message: String, options: dynamic = null) {
        errors.add(message)
    }

    fun info(message: String, options: dynamic = null) {
        infos.add(message)
    }

    fun clear() {
        warnings.clear()
        errors.clear()
        infos.clear()
    }
}

class FakeChat {
    val messages = mutableListOf<dynamic>()

    fun clear() {
        messages.clear()
    }
}

class FakeDocumentRegistry {
    val documents = mutableMapOf<String, Any?>()

    fun register(uuid: String, doc: Any?) {
        documents[uuid] = doc
    }

    fun get(uuid: String): Any? = documents[uuid]

    fun clear() {
        documents.clear()
    }
}

class FakeFoundryEnvironment {
    val notifications = FakeNotifications()
    val chat = FakeChat()
    val registry = FakeDocumentRegistry()

    fun install() {
        installFoundryGlobals()

        val notifWarn: (String, dynamic) -> Unit = { msg, opts -> notifications.warn(msg, opts) }
        val notifError: (String, dynamic) -> Unit = { msg, opts -> notifications.error(msg, opts) }
        val notifInfo: (String, dynamic) -> Unit = { msg, opts -> notifications.info(msg, opts) }
        val notifObj = js("({})")
        notifObj.warn = notifWarn
        notifObj.error = notifError
        notifObj.info = notifInfo
        js("globalThis.ui = globalThis.ui || {}; globalThis.ui.notifications = notifObj;")

        val chatCreate: (dynamic) -> Promise<dynamic> = { data ->
            chat.messages.add(data)
            Promise.resolve<dynamic>(data)
        }
        val chatSpeaker: (dynamic) -> dynamic = { opts ->
            val speaker = js("({})")
            speaker.actor = if (opts != null && opts.actor != null) opts.actor.id else null
            speaker.alias = if (opts != null && opts.actor != null) opts.actor.name else null
            speaker
        }
        val chatApplyMode: (dynamic, dynamic) -> Unit = { data, mode ->
            data.mode = mode
        }
        val chatMessageObj = js("({})")
        chatMessageObj.create = chatCreate
        chatMessageObj.getSpeaker = chatSpeaker
        chatMessageObj.applyMode = chatApplyMode
        js("globalThis.ChatMessage = chatMessageObj;")

        val renderTemplateFn: (String, dynamic) -> Promise<String> = { path, _ ->
            Promise.resolve("<template data-path='$path'></template>")
        }
        js("globalThis.renderTemplate = renderTemplateFn;")

        val fromUuidFn: (String) -> Promise<dynamic> = { uuid ->
            val doc = registry.get(uuid)
            Promise.resolve<dynamic>(doc ?: null)
        }
        js("globalThis.fromUuidMock = fromUuidFn;")
    }

    fun reset() {
        notifications.clear()
        chat.clear()
        registry.clear()
    }
}

class FakeGame(
    var isGM: Boolean = true,
    var currentUserId: String = "test-user-1",
    var currentUserName: String = "Test User",
    initialWorldTime: Double = 0.0,
) {
    private var _worldTime: Double = initialWorldTime
    val settingsMap = mutableMapOf<String, Any?>()
    val actorsList = mutableListOf<dynamic>()

    fun setWorldTime(seconds: Double) {
        _worldTime = seconds
        gameObj.time.worldTime = seconds
    }

    fun setWorldDay(day: Int) {
        setWorldTime(day * 86400.0)
    }

    val gameObj: dynamic = js("({})")

    init {
        val userObj = js("({})")
        userObj.id = currentUserId
        userObj._id = currentUserId
        userObj.name = currentUserName
        userObj.isGM = isGM

        val usersObj = js("({})")
        usersObj.contents = arrayOf(userObj)
        usersObj.get = { id: String -> if (id == currentUserId) userObj else null }
        usersObj.filter = { predicate: (dynamic) -> Boolean ->
            (usersObj.contents.unsafeCast<Array<dynamic>>()).filter(predicate).toTypedArray()
        }
        usersObj.find = { predicate: (dynamic) -> Boolean ->
            (usersObj.contents.unsafeCast<Array<dynamic>>()).firstOrNull(predicate)
        }
        if (isGM) {
            usersObj.activeGM = userObj
        }

        val timeObj = js("({})")
        timeObj.worldTime = _worldTime

        val settingsObj = js("({})")
        settingsObj.get = { scope: String, key: String ->
            settingsMap["$scope.$key"]
        }
        settingsObj.set = { scope: String, key: String, value: Any? ->
            settingsMap["$scope.$key"] = value
            Promise.resolve<dynamic>(value)
        }
        val campingWeatherSettings = js("({})")
        campingWeatherSettings.getHomebrewProfileRegistry = {
            settingsMap["pfrpg2eKingdomCampingWeather.homebrewProfileRegistry"] as? String
        }
        settingsObj.pfrpg2eKingdomCampingWeather = campingWeatherSettings

        val actorsObj = js("({})")
        actorsObj.contents = actorsList.toTypedArray()
        actorsObj.get = { id: String ->
            actorsList.firstOrNull { it.id == id }
        }
        actorsObj.filter = { predicate: (dynamic) -> Boolean ->
            actorsList.filter(predicate).toTypedArray()
        }
        actorsObj.find = { predicate: (dynamic) -> Boolean ->
            actorsList.firstOrNull(predicate)
        }

        gameObj.user = userObj
        gameObj.users = usersObj
        gameObj.time = timeObj
        gameObj.settings = settingsObj
        gameObj.actors = actorsObj
        gameObj.scenes = js("({ active: null, contents: [] })")
        gameObj.i18n = js("({ localize: function(k) { return k; }, format: function(k) { return k; } })")
    }

    fun asGame(): Game = gameObj.unsafeCast<Game>()

    fun addActor(actor: dynamic) {
        actorsList.add(actor)
        gameObj.actors.contents = actorsList.toTypedArray()
    }
}

/**
 * Creates a FakeActor with Foundry's merge semantics for flag reads and writes.
 */
fun createFakeActorInternal(
    prototypeClass: dynamic,
    id: String,
    uuid: String,
    name: String,
    isOwner: Boolean = true,
): dynamic {
    val actor = js("Object.create(prototypeClass.prototype)")
    actor.id = id
    actor._id = id
    actor.uuid = uuid
    actor.name = name
    actor.isOwner = isOwner
    actor.items = js("({ contents: [], get: function(id) { return null; }, filter: function() { return []; } })")

    val flags = js("({})")
    actor.flags = flags

    val getFlagFn: (String, String) -> dynamic = { scope, key ->
        val scopeObj = flags[scope]
        if (scopeObj != null && scopeObj[key] != null) {
            deepClone(scopeObj[key])
        } else {
            null
        }
    }
    actor.getFlag = getFlagFn

    val setFlagFn: (String, String, dynamic) -> Promise<dynamic> = { scope, key, value ->
        if (flags[scope] == null) {
            flags[scope] = js("({})")
        }
        if (flags[scope][key] == null) {
            if (value != null && js("typeof value === 'object'") && !js("Array.isArray(value)")) {
                flags[scope][key] = js("({})")
            } else {
                flags[scope][key] = value
            }
        }
        if (value != null && js("typeof value === 'object'")) {
            applyFoundryMerge(flags[scope][key], value)
        } else {
            flags[scope][key] = value
        }
        Promise.resolve<dynamic>(flags[scope][key])
    }
    actor.setFlag = setFlagFn

    val unsetFlagFn: (String, String) -> Promise<dynamic> = { scope, key ->
        if (flags[scope] != null) {
            js("delete flags[scope][key]")
        }
        Promise.resolve<dynamic>(undefined)
    }
    actor.unsetFlag = unsetFlagFn

    val updateFn: (dynamic, dynamic) -> Promise<dynamic> = { data, _ ->
        val keys = js("Object.keys(data)").unsafeCast<Array<String>>()
        for (k in keys) {
            if (k.startsWith("flags.")) {
                val path = k.removePrefix("flags.")
                val firstDot = path.indexOf('.')
                if (firstDot != -1) {
                    val scope = path.substring(0, firstDot)
                    val remaining = path.substring(firstDot + 1)
                    val nextDot = remaining.indexOf('.')
                    val flagKey = if (nextDot != -1) remaining.substring(0, nextDot) else remaining
                    val subPath = if (nextDot != -1) remaining.substring(nextDot + 1) else null
                    if (flags[scope] == null) flags[scope] = js("({})")
                    if (flags[scope][flagKey] == null) flags[scope][flagKey] = js("({})")
                    if (subPath != null) {
                        val nestedUpdate = js("({})")
                        nestedUpdate[subPath] = data[k]
                        applyFoundryMerge(flags[scope][flagKey], nestedUpdate)
                    } else {
                        if (data[k] != null && js("typeof data[k] === 'object'")) {
                            applyFoundryMerge(flags[scope][flagKey], data[k])
                        } else {
                            flags[scope][flagKey] = data[k]
                        }
                    }
                }
            } else {
                actor[k] = data[k]
            }
        }
        Promise.resolve<dynamic>(actor)
    }
    actor.update = updateFn

    return actor
}

fun createFakeCampingActor(
    id: String = "camping-party",
    uuid: String = "Actor.camping-party",
    name: String = "The Party",
    initialCamping: CampingData? = null,
): CampingActor {
    val partyClass = js("globalThis.CONFIG.PF2E.Actor.documentClasses.party")
    val actor = createFakeActorInternal(partyClass, id, uuid, name, isOwner = true)
    actor.active = true
    actor.type = "party"
    if (initialCamping != null) {
        actor.setFlag(Config.moduleId, "camping-sheet", deepClone(initialCamping))
    }
    return actor.unsafeCast<CampingActor>()
}

fun createFakeKingdomActor(
    id: String = "kingdom-party",
    uuid: String = "Actor.kingdom-party",
    name: String = "The Kingdom",
    initialKingdom: KingdomData? = null,
): KingdomActor {
    val partyClass = js("globalThis.CONFIG.PF2E.Actor.documentClasses.party")
    val actor = createFakeActorInternal(partyClass, id, uuid, name, isOwner = true)
    actor.active = true
    actor.type = "party"
    if (initialKingdom != null) {
        actor.setFlag(Config.moduleId, "kingdom", deepClone(initialKingdom))
    }
    return actor.unsafeCast<KingdomActor>()
}

fun createFakeCharacter(
    id: String,
    uuid: String,
    name: String,
    isOwner: Boolean = true,
): PF2ECharacter {
    val characterClass = js("globalThis.CONFIG.PF2E.Actor.documentClasses.character")
    val actor = createFakeActorInternal(characterClass, id, uuid, name, isOwner = isOwner)
    actor.type = "character"
    actor.system = js("({ abilities: { con: { mod: 2 } } })")
    val itemsList = mutableListOf<dynamic>()
    actor.items = js("({ contents: [], get: function(id) { return null; }, filter: function() { return []; } })")
    actor.items.contents = itemsList.toTypedArray()
    actor.items.filter = { pred: (dynamic) -> Boolean -> itemsList.filter(pred).toTypedArray() }
    actor.items.get = { itemId: String -> itemsList.firstOrNull { it.id == itemId } }

    actor.addItem = { item: dynamic ->
        itemsList.add(item)
        actor.items.contents = itemsList.toTypedArray()
    }
    return actor.unsafeCast<PF2ECharacter>()
}

fun createFakeConsumable(
    id: String,
    uuid: String,
    name: String,
    quantity: Int = 1,
    usesValue: Int = 1,
    usesMax: Int = 1,
): PF2EConsumable {
    val consumableClass = js("globalThis.CONFIG.PF2E.Item.documentClasses.consumable")
    val item = js("Object.create(consumableClass.prototype)")
    item.id = id
    item._id = id
    item.uuid = uuid
    item.name = name
    item.type = "consumable"
    item.system = js("({ quantity: quantity, uses: { value: usesValue, max: usesMax } })")
    val updateItemFn: (dynamic, dynamic) -> Promise<dynamic> = { data, _ ->
        if (data != null && data.system != null) {
            if (data.system.quantity !== undefined) item.system.quantity = data.system.quantity
            if (data.system.uses != null && data.system.uses.value !== undefined) item.system.uses.value = data.system.uses.value
        }
        Promise.resolve<dynamic>(item)
    }
    item.update = updateItemFn
    return item.unsafeCast<PF2EConsumable>()
}
