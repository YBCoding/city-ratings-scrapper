import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.mainBody
import model.CityRating
import model.Rating
import model.StateRating
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
    val persistMode by parser.mapping(
        "--file" to PersistMode.FILE,
        "--other" to PersistMode.OTHER,
        help = "mode of persistence"
    )
    val destination by parser.storing("destination file for --file") { File(this) }
    val torDaemon: Process by parser.positional("tor exe file path") { launchTorDaemon(this) }
    val driver by parser.positional("chrome driver exe file path") { getDriver(File(this)) }
}

fun main(args: Array<String>) = mainBody {
    ArgParser(args).parseInto(::MyArgs).run {
        try {
            val reviews = scrapCityRatingsData(driver)
            val persistProcessor = when (persistMode) {
                PersistMode.FILE -> JSONFileRatingsDataPersist(destination)
                PersistMode.OTHER -> DefaultRatingsDataPersist()
            }
            LOGGER.info { "Persist review" }
            persistProcessor.persist(reviews)
        } catch (e: Exception) {
            LOGGER.error { e }
        } finally {
            torDaemon.destroy()
            driver.quit()
            exitProcess(0)
        }
    }
}

private fun MyArgs.launchTorDaemon(torCmd: String): Process {
    return ProcessBuilder(torCmd).start()
}

private fun MyArgs.getDriver(driverExeFilePath: File): RemoteWebDriver {
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
    chromeDriver.get("http://check.torproject.org")
    sleep(3000)
    return chromeDriver
}

private fun scrapCityRatingsData(driver: RemoteWebDriver): List<StateRating> {
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

            StateRating(code, name, cityReview)
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

val cityRegex = "([\\w\\s]+)\\s\\(([0-9]+)\\)".toRegex()
val inseeRegex = "Statistiques INSEE : ([0-9]+)".toRegex()
fun scrapCityData(cityURL: URL, driver: RemoteWebDriver): CityRating {
    driver.get(cityURL.toString())
    sleep(3000)
    val cityText = driver.findElement(By.xpath("/html/body/div/div[@id=\"colleft\"]/h1")).text
    val cityMatchResult = checkNotNull(cityRegex.find(cityText))
    if (cityMatchResult.groupValues.size < 3) {
        throw RuntimeException("Could not scrap city code and name")
    }
    val name = cityMatchResult.groupValues[1]
    val code = cityMatchResult.groupValues[2]

    val inseeCodeText = driver.findElement(By.xpath("//*[@id=\"info\"]/p[2]/a")).text
    val inseeMatchResult = checkNotNull(inseeRegex.find(inseeCodeText))
    if (inseeMatchResult.groupValues.size < 2) {
        throw RuntimeException("Could not scrap city code and name")
    }
    val inseeCode = inseeMatchResult.groupValues[1]

    val cityRatings = scrapCityRatings(driver)
    return CityRating(code, name, inseeCode, cityRatings)
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
