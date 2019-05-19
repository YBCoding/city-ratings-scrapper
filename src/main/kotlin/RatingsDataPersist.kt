import com.google.gson.Gson
import model.StateRating
import java.io.File

interface RatingsDataPersist {
    fun persist(ratings: List<StateRating>)
}

class JSONFileRatingsDataPersist(private val destination: File) : RatingsDataPersist {
    private val gson = Gson()
    override fun persist(ratings: List<StateRating>) {
        val jsonString = gson.toJson(ratings)
        destination.writeText(jsonString)
    }

}

class DefaultRatingsDataPersist : RatingsDataPersist {
    override fun persist(ratings: List<StateRating>) {
        println(ratings)
    }
}