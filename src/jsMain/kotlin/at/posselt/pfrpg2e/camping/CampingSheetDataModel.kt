package at.posselt.pfrpg2e.camping

import com.foundryvtt.core.AnyObject
import com.foundryvtt.core.abstract.DataModel
import com.foundryvtt.core.abstract.DocumentConstructionContext
import com.foundryvtt.core.data.dsl.buildSchema

@JsExport
class CampingSheetDataModel(
    value: AnyObject,
    options: DocumentConstructionContext?
) : DataModel(value, options) {
    companion object {
        @JsStatic
        fun defineSchema() = buildSchema {
            boolean("travelModeActive")
            boolean("forcedMarchActive")
            string("region")
            string("travelStartHex", nullable = true)
            string("travelEndHex", nullable = true)
            // Only rendered for GMs, so it is absent from a player's form; the submit handler
            // therefore only applies it when the submitting user is a GM.
            boolean("travelMoveToken")
            // Only present in the DOM while the Set Watches section is open, so it must be nullable.
            int("numberOfWatches", nullable = true)
            schema("activities") {
                stringRecord("selectedSkill")
                stringRecord("learnTarget") {
                    string(nullable = true) {

                    }
                }
                stringRecord("degreeOfSuccess") {
                    string(nullable = true) {

                    }
                }
            }
            schema("recipes") {
                stringRecord("selectedSkill")
                stringRecord("degreeOfSuccess") {
                    string(nullable = true) {

                    }
                }
            }
        }
    }
}
