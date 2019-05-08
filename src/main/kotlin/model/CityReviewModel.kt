package model

data class StateReview(val code: String, val name: String, val cityReview: List<CityReview>)
data class CityReview(val code: String, val name: String, val cityRatings: Map<Rating, Double>)

enum class Rating(val label: String) {
    ENVIRONMENT("Environnement"),
    TRANSPORTS("Transports"),
    SAFETY("Sécurité"),
    HEALTH_CARE("Santé"),
    SPORTS_AND_LEISURES("Sports et loisirs"),
    CULTURE("Culture"),
    EDUCATION("Enseignement"),
    SHOPS("Commerces"),
    QUALITY_OF_LIFE("Qualité de vie")
}
