import com.google.gson.Gson
import model.StateReview
import java.io.File

interface ReviewDataPersist {
    fun persist(reviews: List<StateReview>)
}

class JSONFileReviewDataPersist(private val destination: File) : ReviewDataPersist {
    private val gson = Gson()
    override fun persist(reviews: List<StateReview>) {
        val jsonString = gson.toJson(reviews)
        destination.writeText(jsonString)
    }

}

class DefaultReviewDataPersist : ReviewDataPersist {
    override fun persist(reviews: List<StateReview>) {
        println(reviews)
    }
}