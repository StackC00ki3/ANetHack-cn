package com.yywspace.anethack.identify

import android.content.Context
import com.yywspace.anethack.R

/**
 * Maps the price-identification data onto the current app language.
 *
 * `nh_default_objects.json` carries English keys plus `*Zh` siblings generated
 * from NetHack's objects.h (see tools/build_price_id_data.py), so every lookup
 * falls back to the English value when a translation is missing.
 */
object PriceIDStrings {
    /** JSON object type key ("Potion", ...) -> localized label. */
    private val TYPE_LABELS = mapOf(
        "Potion" to R.string.price_id_obj_type_potion,
        "Scroll" to R.string.price_id_obj_type_scroll,
        "Spellbook" to R.string.price_id_obj_type_spellbook,
        "Ring" to R.string.price_id_obj_type_ring,
        "Wand" to R.string.price_id_obj_type_wand,
        "Amulet" to R.string.price_id_obj_type_amulet,
        "Boots" to R.string.price_id_obj_type_boots,
        "Cloak" to R.string.price_id_obj_type_cloak,
        "Helm" to R.string.price_id_obj_type_helm,
        "Gloves" to R.string.price_id_obj_type_gloves,
    )

    /** JSON attribute key ("Weight", ...) -> localized label. */
    private val FIELD_LABELS = mapOf(
        "Name" to R.string.price_id_field_name,
        "Cost" to R.string.price_id_field_cost,
        "Weight" to R.string.price_id_field_weight,
        "Appearance" to R.string.price_id_field_appearance,
        "Ink" to R.string.price_id_field_ink,
        "School" to R.string.price_id_field_school,
        "Actions to read" to R.string.price_id_field_actions_to_read,
        "Extrinsic granted" to R.string.price_id_field_extrinsic_granted,
        "Notes" to R.string.price_id_field_notes,
        "Starting charges" to R.string.price_id_field_starting_charges,
        "Attack type" to R.string.price_id_field_attack_type,
        "When eaten" to R.string.price_id_field_when_eaten,
        "AC" to R.string.price_id_field_ac,
        "Material" to R.string.price_id_field_material,
        "Effect" to R.string.price_id_field_effect,
        "MC" to R.string.price_id_field_mc,
    )

    fun typeLabel(context: Context, type: String): String =
        TYPE_LABELS[type]?.let { context.getString(it) } ?: type

    fun fieldLabel(context: Context, field: String): String =
        FIELD_LABELS[field]?.let { context.getString(it) } ?: field

    /**
     * Attribute value for [field], preferring the generated Chinese sibling.
     * Chinese is only used when the app itself is showing Chinese, so an English
     * UI keeps the English data even though both are present in the asset.
     */
    fun fieldValue(context: Context, field: String, obj: Map<String, String>): String {
        val zh = obj["${field}Zh"]
        return if (useChinese(context)) zh ?: obj[field].orEmpty() else obj[field].orEmpty()
    }

    /** Display name of an object, localized the same way as [fieldValue]. */
    fun objName(context: Context, obj: Map<String, String>): String {
        val zh = obj["NameZh"]
        return if (useChinese(context)) zh ?: obj["Name"].orEmpty() else obj["Name"].orEmpty()
    }

    private fun useChinese(context: Context): Boolean =
        context.resources.configuration.locales[0].language == "zh"
}
