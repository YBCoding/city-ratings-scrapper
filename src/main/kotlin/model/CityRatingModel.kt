package model

data class StateRating(val code: String, val name: String, val cityRatings: List<CityRating>)
data class CityRating(val code: String, val name: String, val inseeCode: String, val ratings: Map<Rating, Double>)

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
