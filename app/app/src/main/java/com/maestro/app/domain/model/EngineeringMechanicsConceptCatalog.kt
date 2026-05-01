package com.maestro.app.domain.model

data class EngineeringMechanicsConcept(
    val id: String,
    val name: String,
    val keywords: List<String>
)

object EngineeringMechanicsConceptCatalog {
    val concepts = listOf(
        EngineeringMechanicsConcept(
            id = "em_statics_equilibrium",
            name = "Statics and equilibrium",
            keywords = listOf(
                "static", "statics", "equilibrium", "force balance",
                "free body", "free-body", "reaction force", "moment"
            )
        ),
        EngineeringMechanicsConcept(
            id = "em_truss_frame",
            name = "Trusses and frames",
            keywords = listOf(
                "truss", "frame", "joint", "member", "method of joints",
                "method of sections", "zero-force"
            )
        ),
        EngineeringMechanicsConcept(
            id = "em_friction",
            name = "Friction",
            keywords = listOf(
                "friction", "coefficient of friction", "static friction",
                "kinetic friction", "impending motion"
            )
        ),
        EngineeringMechanicsConcept(
            id = "em_centroid_inertia",
            name = "Centroids and moments of inertia",
            keywords = listOf(
                "centroid", "center of mass", "moment of inertia",
                "second moment", "parallel axis"
            )
        ),
        EngineeringMechanicsConcept(
            id = "em_stress_strain",
            name = "Stress and strain",
            keywords = listOf(
                "stress", "strain", "normal stress", "shear stress",
                "hooke", "young's modulus", "elastic"
            )
        ),
        EngineeringMechanicsConcept(
            id = "em_axial_torsion",
            name = "Axial loading and torsion",
            keywords = listOf(
                "axial", "torsion", "torque", "shaft", "angle of twist",
                "polar moment"
            )
        ),
        EngineeringMechanicsConcept(
            id = "em_beam_shear_moment",
            name = "Beam shear and bending moment",
            keywords = listOf(
                "beam", "shear force", "bending moment",
                "distributed load", "shear diagram", "moment diagram"
            )
        ),
        EngineeringMechanicsConcept(
            id = "em_beam_stress_deflection",
            name = "Beam stress and deflection",
            keywords = listOf(
                "bending stress", "deflection", "slope",
                "elastic curve", "flexure", "neutral axis"
            )
        ),
        EngineeringMechanicsConcept(
            id = "em_kinematics",
            name = "Kinematics",
            keywords = listOf(
                "kinematics", "velocity", "acceleration",
                "relative motion", "projectile", "rigid body motion"
            )
        ),
        EngineeringMechanicsConcept(
            id = "em_kinetics",
            name = "Kinetics, work, energy, and momentum",
            keywords = listOf(
                "kinetics", "newton", "work", "energy", "impulse",
                "momentum", "power", "conservation"
            )
        )
    )

    fun bestMatch(text: String): EngineeringMechanicsConcept {
        val normalized = text.lowercase()
        return concepts.maxBy { concept ->
            concept.keywords.sumOf { keyword ->
                Regex("\\b${Regex.escape(keyword.lowercase())}\\b")
                    .findAll(normalized)
                    .count()
            }
        }
    }

    fun byId(id: String): EngineeringMechanicsConcept? =
        concepts.find { it.id == id }
}
