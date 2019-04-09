import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.mainBody
import model.CityReview
import model.Rating
import model.StateReview
import mu.KotlinLogging
import org.openqa.selenium.By
import org.openqa.selenium.NoSuchElementException
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeDriverService
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.remote.RemoteWebDriver
import java.io.File
import java.lang.Thread.sleep
import java.net.URL
import java.text.NumberFormat
import java.util.Locale
import kotlin.collections.HashMap
import kotlin.system.exitProcess


val PARIS_AND_SUBURBS = listOf("75", "92", "93", "94")
val FRENCH_NUMBER_FORMAT: NumberFormat = NumberFormat.getInstance(Locale.FRENCH)
val LOGGER = KotlinLogging.logger {}

enum class PersistMode { FILE, OTHER }

class MyArgs(parser: ArgParser) {
    val v by parser.flagging("enable verbose mode")
    val persistMode by parser.mapping(
        "--file" to PersistMode.FILE,
        "--other" to PersistMode.OTHER,
        help = "mode of persistence"
    )
    val destination by parser.storing("destination file for --file") { File(this) }
    val driver by parser.positional("chrome driver exe file path") { getDriver(File(this)) }
    val torDaemon: Process by parser.positional("tor exe file path") { ProcessBuilder(this).start() }
}

fun main(args: Array<String>) = mainBody {
    ArgParser(args).parseInto(::MyArgs).run {
        try {
            val reviews = getReviews()
            val persistor = when (persistMode) {
                PersistMode.FILE -> JSONFileReviewDataPersist(destination)
                PersistMode.OTHER -> DefaultReviewDataPersist()
            }
            LOGGER.info { "Persist review" }
            persistor.persist(reviews)
        } catch (e: Exception) {
            LOGGER.error { e }
        } finally {
            torDaemon.destroy()
            driver.quit()
            exitProcess(0)
        }
    }
}

private fun getDriver(driverExeFilePath: File): RemoteWebDriver {
    val options = ChromeOptions()
    val proxy = "socks5://localhost:9050" // IP:PORT or HOST:PORT
    options.addArguments("start-maximized")
    options.addArguments("disable-infobars")
    options.addArguments("--disable-extensions")
    options.addArguments("--proxy-server=$proxy")

    val driverService = ChromeDriverService.Builder()
        .usingDriverExecutable(driverExeFilePath)
        .usingAnyFreePort()
        .build()

    val chromeDriver = ChromeDriver(driverService, options)
    sleep(1000)
    chromeDriver.get("http://check.torproject.org")
    sleep(3000)
    return chromeDriver
}

private fun MyArgs.getReviews(): List<StateReview> {
    LOGGER.info { "Start scrapping city reviews" }

    return PARIS_AND_SUBURBS.asSequence()
        .mapNotNull {
            driver.get("https://www.ville-ideale.fr/villespardepts.php")
            driver.findElement(By.xpath("//*[@id=\"listedepts\"]/a[contains(text(),'$it')]"))
        }.map {
            it.click()
            sleep(3000)
            val code = scrapStateCode(driver)
            val name = scrapStateName(driver)
            val cityReview = toCitiesLink(driver)
                .map { cityLink -> scrapCityData(cityLink, driver) }

            StateReview(code, name, cityReview)
        }.toList()
}

fun toCitiesLink(driver: RemoteWebDriver): List<URL> {
    return driver.findElementsByCssSelector("#depart > p > a")
        .map { it.getAttribute("href") }
        .map { URL(it) }
}

fun scrapStateCode(driver: RemoteWebDriver): String {
    return driver.findElementByCssSelector("#titredept")
        .text
        .substringBefore(" - ")
}

fun scrapStateName(driver: RemoteWebDriver): String {
    return driver.findElementByCssSelector("#titredept")
        .text
        .substringAfter(" - ")
}

fun scrapCityData(cityURL: URL, driver: RemoteWebDriver): CityReview {
    driver.get(cityURL.toString())
    sleep(3000)
    val city = driver.findElement(By.xpath("/html/body/div/div[@id=\"colleft\"]/h1")).text
    val matchResult = checkNotNull("([\\w\\s]+)\\s\\(([0-9]+)\\)".toRegex().find(city))
    val values = matchResult.groupValues
    if (values.size < 3) {
        throw RuntimeException("Could not scrap city code and name")
    }
    val name = values[1]
    val code = values[2]
    val cityRatings = scrapCityRatings(driver)
    return CityReview(code, name, cityRatings)
}

val nbEvalRegex = "Notes obtenues sur ([0-9]+) évaluations".toRegex()
fun scrapCityRatings(driver: RemoteWebDriver): Map<Rating, Double> {
    return try {
        val nbEvalElement = driver.findElement(By.xpath("//*[@id=\"nobt\"]/a"))
        nbEvalRegex.find(nbEvalElement.text)?.let {
            val (nbEvalStr) = it.destructured
            FRENCH_NUMBER_FORMAT.parse(nbEvalStr).toInt()
                .takeIf { nbEval -> nbEval >= 20 }
                .run {
                    Rating.values()
                        .map { rating -> rating to scrapRating(rating.label, driver) }
                        .toMap()
                }
        }.orEmpty()
    } catch (e: NoSuchElementException) {
        HashMap()
    }
}

fun scrapRating(type: String, driver: RemoteWebDriver): Double {
    val text = driver.findElement(By.xpath("//*[@id=\"tablonotes\"]/tbody/tr[th/text()=\"$type\"]/td")).text
    return FRENCH_NUMBER_FORMAT.parse(text)
        .toDouble()
}