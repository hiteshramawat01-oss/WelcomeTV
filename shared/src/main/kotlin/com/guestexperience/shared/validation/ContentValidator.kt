package com.guestexperience.shared.validation

import com.guestexperience.shared.model.PropertyContent

data class ContentProblem(val field: String, val error: FieldError)

/** Validates a whole content document before it is saved on the TV or sent from the phone. */
object ContentValidator {
    const val MAX_NOTES = 8
    const val MAX_PLACES = 8

    /**
     * For the Samsung Guest Welcome app, which shows only the guest name and the welcome message.
     * Same limits as [validate] for those two fields; everything else is ignored there.
     */
    fun validateWelcomeOnly(c: PropertyContent): ContentProblem? =
        Validators.optionalText(c.guestName, Validators.NAME_MAX)?.let { ContentProblem("guestName", it) }
            ?: Validators.optionalText(c.hostMessage, Validators.MESSAGE_MAX)?.let { ContentProblem("hostMessage", it) }

    fun validate(c: PropertyContent): ContentProblem? {
        fun check(field: String, error: FieldError?) = error?.let { ContentProblem(field, it) }

        return check("propertyName", Validators.requiredText(c.propertyName, Validators.PROPERTY_MAX))
            ?: check("location", Validators.optionalText(c.location, Validators.PROPERTY_MAX))
            ?: check("guestName", Validators.optionalText(c.guestName, Validators.NAME_MAX))
            ?: check("hostMessage", Validators.optionalText(c.hostMessage, Validators.MESSAGE_MAX))
            ?: check("wifi.ssid", Validators.ssid(c.wifi.ssid))
            ?: check("wifi.password", Validators.wifiPassword(c.wifi.password, c.wifi.security))
            ?: c.checkOut?.let {
                check("checkOut.date", Validators.date(it.date)) ?: check("checkOut.time", Validators.time(it.time))
            }
            ?: check("houseNotes", if (c.houseNotes.size > MAX_NOTES) FieldError.TOO_LONG else null)
            ?: c.houseNotes.firstNotNullOfOrNull { check("houseNotes", Validators.optionalText(it, Validators.NOTE_MAX)) }
            ?: check("places", if (c.places.size > MAX_PLACES) FieldError.TOO_LONG else null)
            ?: c.places.firstNotNullOfOrNull {
                check("places", Validators.requiredText(it.name, Validators.NAME_MAX))
                    ?: check("places", Validators.optionalText(it.description, Validators.NOTE_MAX))
            }
            ?: c.hostContact?.let {
                check("hostContact.name", Validators.optionalText(it.name, Validators.NAME_MAX))
                    ?: check("hostContact.phone", Validators.optionalText(it.phone, 24))
                    ?: check("hostContact.note", Validators.optionalText(it.note, Validators.NOTE_MAX))
            }
    }
}
