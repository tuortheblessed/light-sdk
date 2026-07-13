package com.thelightphone.hey.ui

import androidx.annotation.DrawableRes
import com.thelightphone.hey.R

/** Maps HEY habit icon slugs to ported watchface drawables. */
object HeyHabitIcons {
    @DrawableRes
    fun drawableRes(slug: String?, darkTheme: Boolean): Int? {
        val key = slug?.trim()?.lowercase().orEmpty()
        if (key.isEmpty()) return null
        val base = SLUG_TO_BASE[key] ?: SLUG_TO_BASE["star"] ?: return null
        return if (darkTheme) base.second else base.first
    }

    // Pair(light/black, dark/white)
    private val SLUG_TO_BASE: Map<String, Pair<Int, Int>> = mapOf(
        "art" to (R.drawable.habit_art to R.drawable.habit_art_w),
        "baseball" to (R.drawable.habit_baseball to R.drawable.habit_baseball_w),
        "basketball" to (R.drawable.habit_basketball to R.drawable.habit_basketball_w),
        "bed" to (R.drawable.habit_bed to R.drawable.habit_bed_w),
        "bicycle" to (R.drawable.habit_bicycle to R.drawable.habit_bicycle_w),
        "brain" to (R.drawable.habit_brain to R.drawable.habit_brain_w),
        "breathe" to (R.drawable.habit_breathe to R.drawable.habit_breathe_w),
        "camera" to (R.drawable.habit_camera to R.drawable.habit_camera_w),
        "cat" to (R.drawable.habit_cat to R.drawable.habit_cat_w),
        "church" to (R.drawable.habit_church to R.drawable.habit_church_w),
        "clean" to (R.drawable.habit_clean to R.drawable.habit_clean_w),
        "cook" to (R.drawable.habit_cook to R.drawable.habit_cook_w),
        "dog" to (R.drawable.habit_dog to R.drawable.habit_dog_w),
        "drink" to (R.drawable.habit_drink to R.drawable.habit_drink_w),
        "football" to (R.drawable.habit_football to R.drawable.habit_football_w),
        "fruit" to (R.drawable.habit_fruit to R.drawable.habit_fruit_w),
        "game" to (R.drawable.habit_game to R.drawable.habit_game_w),
        "garden" to (R.drawable.habit_garden to R.drawable.habit_garden_w),
        "guitar" to (R.drawable.habit_guitar to R.drawable.habit_guitar_w),
        "heart" to (R.drawable.habit_heart to R.drawable.habit_heart_w),
        "heat" to (R.drawable.habit_heat to R.drawable.habit_heat_w),
        "hydrate" to (R.drawable.habit_hydrate to R.drawable.habit_hydrate_w),
        "ice" to (R.drawable.habit_ice to R.drawable.habit_ice_w),
        "lotus" to (R.drawable.habit_lotus to R.drawable.habit_lotus_w),
        "meditate" to (R.drawable.habit_meditate to R.drawable.habit_meditate_w),
        "money" to (R.drawable.habit_money to R.drawable.habit_money_w),
        "music" to (R.drawable.habit_music to R.drawable.habit_music_w),
        "piano" to (R.drawable.habit_piano to R.drawable.habit_piano_w),
        "pill" to (R.drawable.habit_pill to R.drawable.habit_pill_w),
        "plant" to (R.drawable.habit_plant to R.drawable.habit_plant_w),
        "read" to (R.drawable.habit_read to R.drawable.habit_read_w),
        "run" to (R.drawable.habit_run to R.drawable.habit_run_w),
        "smoke" to (R.drawable.habit_smoke to R.drawable.habit_smoke_w),
        "soccer" to (R.drawable.habit_soccer to R.drawable.habit_soccer_w),
        "star" to (R.drawable.habit_star to R.drawable.habit_star_w),
        "study" to (R.drawable.habit_study to R.drawable.habit_study_w),
        "swim" to (R.drawable.habit_swim to R.drawable.habit_swim_w),
        "tea" to (R.drawable.habit_tea to R.drawable.habit_tea_w),
        "toothbrush" to (R.drawable.habit_toothbrush to R.drawable.habit_toothbrush_w),
        "tree" to (R.drawable.habit_tree to R.drawable.habit_tree_w),
        "tv" to (R.drawable.habit_tv to R.drawable.habit_tv_w),
        "vegetable" to (R.drawable.habit_vegetable to R.drawable.habit_vegetable_w),
        "walk" to (R.drawable.habit_walk to R.drawable.habit_walk_w),
        "water" to (R.drawable.habit_water to R.drawable.habit_water_w),
        "weights" to (R.drawable.habit_weights to R.drawable.habit_weights_w),
        "write" to (R.drawable.habit_write to R.drawable.habit_write_w),
        "yoga" to (R.drawable.habit_yoga to R.drawable.habit_yoga_w),
    )
}
