package com.maestro.app.domain.model

data class EngineeringMechanicsConcept(
    val id: String,
    val name: String,
    val keywords: List<String>
)

object EngineeringMechanicsConceptCatalog {
    private const val DEFAULT_CONCEPT_ID = "simple_step"

    private val statics2011Ids =
        listOf(
            "anticipate_solved_variables",
            "body_draw_force_on",
            "can_connection_be_modeled_in_2D",
            "centroid_of_composite_area",
            "choose_moment_method",
            "choose_subsystem",
            "conditions_equal_force_pulley",
            "count_independent_equations",
            "count_unknowns",
            "couple_related_to_forces",
            "couple_represents_net_zero_force",
            "determine_joint_is_solvable",
            "determine_moment",
            "determine_subsystem_is_solvable",
            "distinguish_fixed_pin_connections",
            "distinguish_rotation_translation",
            "equivalence_of_couples",
            "find_angle_given_components",
            "find_linear_force_per_length",
            "find_magnitude_given_components",
            "find_moment_arm",
            "find_net_force_for_linear_distribution",
            "find_net_force_for_uniform_distribution",
            "find_net_force_position_for_linear_distribution",
            "find_net_force_position_for_uniform_distribution",
            "find_pressure_under_linear_distribution",
            "find_symmetry_plane",
            "find_uniform_force_per_length",
            "find_uniform_pressure_under_weight",
            "force_at_joint_implied_by_previous_analysis",
            "gravitational_forces",
            "identify_centroid",
            "identify_enabling_unknown",
            "identify_equation_isolates_specific_unknown",
            "identify_external_load_points_on_section",
            "identify_forces_in_symmetry_plane",
            "identify_interaction",
            "identify_internal_load_points_on_section",
            "identify_two-force_member",
            "impose_solve_concurrent_equilibrium",
            "interpret_equation",
            "is_net_moment_sense_obvious",
            "judge_equilibrium_qualitatively",
            "judge_force_sense_based_on_sign",
            "mass_moment_of_inertia_parallel_axis_theorem",
            "moment_about_point_due_to_couple",
            "moment_sign_sense_relation",
            "motion_dependence_on_force",
            "moving_force_perpendicular_to_line_of_action",
            "moving_force_to_general_point",
            "Newtons_third_law",
            "polar_moment_of_area",
            "possible_interaction_for_nonuniform_contact",
            "recognize_conditions_for_full_equivalence",
            "recognize_equivalence_from_motion",
            "recognize_equivalence_of_translated_forces",
            "recognize_forces_collinear",
            "recognize_forces_concurrent",
            "recognize_knowns_vs_unknowns",
            "recognize_variable_solvable_from_subsystem",
            "relate_direction_normal_force_and_contact",
            "relative_magnitudes_mass_moment_of_inertia",
            "relative_magnitudes_moment_of_area",
            "replace_forces_in_opposite_sense_with_force_and_couple",
            "replace_forces_in_same_sense_with_one_force",
            "replace_general_loads_with_force_and_couple",
            "replace_general_loads_with_single_force",
            "represent_forces_two-force_member",
            "represent_interaction_contacting_body",
            "represent_interaction_cord",
            "represent_interaction_fixed_connection",
            "represent_interaction_pin_connection",
            "represent_interaction_pin_in_slot_connection",
            "represent_interaction_rigid_sliding_connection",
            "represent_interaction_roller_connection",
            "represent_interaction_spring",
            "resolve_into_components",
            "rotation_sense_of_force",
            "second_moment_of_area_parallel_axis_theorem",
            "second_moment_of_area_tabulated_shape",
            "sense_if_assuming_tension",
            "simple_step",
            "statics_problem_collinear",
            "statics_problem_force_and_moment",
            "tension_vs_compression_given_force_senses"
        )

    val concepts: List<EngineeringMechanicsConcept> by lazy {
        statics2011Ids.map { id ->
            EngineeringMechanicsConcept(
                id = id,
                name = id.toDisplayName(),
                keywords = id.toKeywords()
            )
        }
    }

    fun bestMatch(text: String): EngineeringMechanicsConcept {
        val scored =
            concepts
                .map { concept -> concept to score(text, concept) }
                .maxBy { it.second }
        return if (scored.second > 0) {
            scored.first
        } else {
            byId(DEFAULT_CONCEPT_ID) ?: concepts.first()
        }
    }

    fun byId(id: String): EngineeringMechanicsConcept? = concepts.find { it.id == id }

    fun byName(name: String): EngineeringMechanicsConcept? {
        val target = name.trim()
        if (target.isBlank()) return null
        return concepts.find { it.name.equals(target, ignoreCase = true) }
            ?: concepts.find { it.id.equals(target, ignoreCase = true) }
    }

    /**
     * Resolves the statics2011 concept key(s) the QE server expects for a quiz.
     * [targetConcept] is matched against concept ids and display names first;
     * if it does not resolve, [evidenceText] is keyword-matched as a fallback so
     * the encode request always carries a valid `concept_keys` entry.
     */
    fun resolveConceptKeys(targetConcept: String, evidenceText: String = ""): List<String> {
        byId(targetConcept.trim())?.let { return listOf(it.id) }
        byName(targetConcept)?.let { return listOf(it.id) }
        val fallbackText =
            listOf(targetConcept, evidenceText)
                .filter { it.isNotBlank() }
                .joinToString(" ")
        return listOf(bestMatch(fallbackText).id)
    }

    private fun score(text: String, concept: EngineeringMechanicsConcept): Int {
        val normalized = text.lowercase()
        return concept.keywords.sumOf { keyword ->
            Regex("\\b${Regex.escape(keyword.lowercase())}\\b")
                .findAll(normalized)
                .count()
        }
    }

    private fun String.toDisplayName(): String = replace('_', ' ')
        .replace("2D", "2D")
        .split(' ')
        .joinToString(" ") { word ->
            when (word) {
                "Newtons" -> "Newton's"
                "2D" -> "2D"
                else -> word.replaceFirstChar { it.uppercase() }
            }
        }

    private fun String.toKeywords(): List<String> {
        val phrase = replace('_', ' ')
        val aliases = keywordAliases[this].orEmpty()
        return (listOf(this, phrase) + aliases)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private val keywordAliases =
        mapOf(
            "body_draw_force_on" to
                listOf(
                    "free body diagram",
                    "force on body",
                    "draw force"
                ),
            "centroid_of_composite_area" to
                listOf(
                    "composite area",
                    "centroid"
                ),
            "choose_moment_method" to
                listOf(
                    "moment equation",
                    "sum of moments"
                ),
            "choose_subsystem" to
                listOf(
                    "subsystem",
                    "free body"
                ),
            "conditions_equal_force_pulley" to
                listOf(
                    "pulley",
                    "equal tension"
                ),
            "count_independent_equations" to
                listOf(
                    "independent equations",
                    "equilibrium equations"
                ),
            "count_unknowns" to
                listOf(
                    "unknown forces",
                    "unknown reactions"
                ),
            "determine_moment" to
                listOf(
                    "moment",
                    "torque"
                ),
            "distinguish_fixed_pin_connections" to
                listOf(
                    "fixed connection",
                    "pin connection"
                ),
            "find_angle_given_components" to
                listOf(
                    "angle from components",
                    "force components"
                ),
            "find_magnitude_given_components" to
                listOf(
                    "magnitude from components",
                    "resultant force"
                ),
            "find_moment_arm" to
                listOf(
                    "moment arm",
                    "perpendicular distance"
                ),
            "gravitational_forces" to
                listOf(
                    "weight",
                    "gravity"
                ),
            "identify_two-force_member" to
                listOf(
                    "two force member",
                    "two-force member"
                ),
            "impose_solve_concurrent_equilibrium" to
                listOf(
                    "concurrent equilibrium",
                    "force equilibrium"
                ),
            "mass_moment_of_inertia_parallel_axis_theorem" to
                listOf(
                    "mass moment of inertia",
                    "parallel axis theorem"
                ),
            "moment_about_point_due_to_couple" to
                listOf(
                    "couple moment",
                    "moment of a couple"
                ),
            "Newtons_third_law" to
                listOf(
                    "newton's third law",
                    "action reaction"
                ),
            "polar_moment_of_area" to
                listOf(
                    "polar moment",
                    "polar moment of inertia"
                ),
            "recognize_forces_collinear" to
                listOf(
                    "collinear forces",
                    "same line of action"
                ),
            "recognize_forces_concurrent" to
                listOf(
                    "concurrent forces",
                    "forces meet at a point"
                ),
            "relate_direction_normal_force_and_contact" to
                listOf(
                    "normal force",
                    "contact force"
                ),
            "relative_magnitudes_mass_moment_of_inertia" to
                listOf(
                    "mass moment of inertia"
                ),
            "relative_magnitudes_moment_of_area" to
                listOf(
                    "moment of area",
                    "area moment of inertia"
                ),
            "replace_general_loads_with_force_and_couple" to
                listOf(
                    "equivalent force couple",
                    "force and couple"
                ),
            "replace_general_loads_with_single_force" to
                listOf(
                    "equivalent resultant",
                    "single force"
                ),
            "represent_interaction_cord" to
                listOf(
                    "cord",
                    "tension"
                ),
            "represent_interaction_fixed_connection" to
                listOf(
                    "fixed support",
                    "fixed connection"
                ),
            "represent_interaction_pin_connection" to
                listOf(
                    "pin support",
                    "pin connection"
                ),
            "represent_interaction_roller_connection" to
                listOf(
                    "roller support",
                    "roller connection"
                ),
            "represent_interaction_spring" to
                listOf(
                    "spring force",
                    "spring"
                ),
            "resolve_into_components" to
                listOf(
                    "resolve into components",
                    "force components"
                ),
            "second_moment_of_area_parallel_axis_theorem" to
                listOf(
                    "second moment of area",
                    "parallel axis theorem"
                ),
            "second_moment_of_area_tabulated_shape" to
                listOf(
                    "second moment of area",
                    "area moment of inertia"
                ),
            "tension_vs_compression_given_force_senses" to
                listOf(
                    "tension",
                    "compression"
                )
        )
}
