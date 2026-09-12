package com.example.aniflow.data.remote

import com.example.aniflow.data.NetworkModule
import com.example.aniflow.data.model.Anime
import com.example.aniflow.data.model.SearchPage
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.net.URLEncoder

class BackupAnimeApi(private val client: HttpClient) {
    private val json = NetworkModule.json
    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    private val kitsuAccept = "application/vnd.api+json"

    val bleachTybwAnime = Anime(
        id = 185874,
        title = "Bleach: Thousand-Year Blood War - The Calamity",
        englishTitle = "Bleach: Thousand-Year Blood War - The Calamity",
        coverImage = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx185874-WkL6oB6Gj2x6.jpg",
        bannerImage = "https://media.kitsu.app/anime/48015/cover_image/large-b3305a3976b88108f7f32e4d53748c2d.jpeg",
        description = "The fourth and final cour of Bleach: Thousand-Year Blood War adapts the climactic final battle of the Quincy Blood War (Kashin-tan / The Calamity). Ichigo Kurosaki, the Soul Society, and the Gotei 13 make their ultimate stand against Yhwach and the Almighty to determine the fate of all three worlds.",
        episodes = 10,
        averageScore = 92,
        genres = listOf("Action", "Adventure", "Supernatural", "Fantasy"),
        studioName = "PIERROT FILMS",
        status = "RELEASING",
        trailerUrl = "https://www.youtube-nocookie.com/embed/Nq9PkH3UgEo?autoplay=1&mute=1"
    )

    val curatedBlockbusterHits = listOf(
        bleachTybwAnime,
        Anime(
            id = 151807,
            title = "Solo Leveling",
            englishTitle = "Solo Leveling",
            coverImage = "https://media.kitsu.app/anime/46231/poster_image/large-cdadff31f42490b9f48a035939a01a92.jpeg",
            bannerImage = "https://image.tmdb.org/t/p/original/4HodYYKEIsGOdinkGi2UMcz6X9H.jpg",
            description = "In a world where hunters must battle deadly monsters to protect humanity, Sung Jinwoo, notoriously known as the weakest hunter of all mankind, finds himself in a struggle for survival.",
            episodes = 12,
            averageScore = 86,
            genres = listOf("Action", "Adventure", "Fantasy"),
            studioName = "A-1 Pictures",
            status = "FINISHED"
        ),
        Anime(
            id = 113415,
            title = "Jujutsu Kaisen",
            englishTitle = "JUJUTSU KAISEN",
            coverImage = "https://media.kitsu.app/anime/poster_images/42765/large.jpg",
            bannerImage = "https://image.tmdb.org/t/p/original/gmECX1DvFnahQIhzptJ6gl2VTe7.jpg",
            description = "A boy fights... for the right death. Hardship, regret, shame: the negative feelings that humans feel become Curses that lurk in our everyday lives.",
            episodes = 24,
            averageScore = 86,
            genres = listOf("Action", "Drama", "Supernatural"),
            studioName = "MAPPA",
            status = "FINISHED"
        ),
        Anime(
            id = 101922,
            title = "Demon Slayer: Kimetsu no Yaiba",
            englishTitle = "Demon Slayer: Kimetsu no Yaiba",
            coverImage = "https://media.kitsu.app/anime/poster_images/41370/large.jpg",
            bannerImage = "https://image.tmdb.org/t/p/original/3G1Q5xF40Hvg42VhWWVaHp8daYs.jpg",
            description = "It is the Taisho Period in Japan. Tanjiro, a kindhearted boy who sells charcoal for a living, finds his family slaughtered by a demon.",
            episodes = 26,
            averageScore = 85,
            genres = listOf("Action", "Adventure", "Supernatural"),
            studioName = "ufotable",
            status = "FINISHED"
        ),
        Anime(
            id = 16498,
            title = "Attack on Titan",
            englishTitle = "Attack on Titan",
            coverImage = "https://media.kitsu.app/anime/poster_images/7442/large.jpg",
            bannerImage = "https://image.tmdb.org/t/p/original/y4aq5P5APG97Lm9qthvlWj0TzRk.jpg",
            description = "Humans fight for survival against giant man-eating humanoids called Titans behind massive defensive walls.",
            episodes = 75,
            averageScore = 90,
            genres = listOf("Action", "Drama", "Fantasy", "Mystery"),
            studioName = "WIT Studio",
            status = "FINISHED"
        ),
        Anime(
            id = 21,
            title = "One Piece",
            englishTitle = "One Piece",
            coverImage = "https://media.kitsu.app/anime/poster_images/12/large.jpg",
            bannerImage = "https://image.tmdb.org/t/p/original/4MCKNAc6AbWjEsM2cr9h8vgTWIZ.jpg",
            description = "Monkey D. Luffy refuses to let anyone or anything stand in the way of his quest to become the king of all pirates.",
            episodes = 1100,
            averageScore = 88,
            genres = listOf("Action", "Adventure", "Comedy", "Fantasy"),
            studioName = "Toei Animation",
            status = "RELEASING"
        ),
        Anime(
            id = 154587,
            title = "Frieren: Beyond Journey's End",
            englishTitle = "Frieren: Beyond Journey's End",
            coverImage = "https://media.kitsu.app/anime/46474/poster_image/large-ec9b98dd5fbf8f92532d1edb45f9e882.jpeg",
            bannerImage = "https://image.tmdb.org/t/p/original/179rCi2qG1d9Z5eOzR0S2FpdP6X.jpg",
            description = "The adventure is over but life goes on for an elf mage just beginning to learn what living is all about.",
            episodes = 28,
            averageScore = 91,
            genres = listOf("Adventure", "Drama", "Fantasy"),
            studioName = "Madhouse",
            status = "FINISHED"
        ),
        Anime(
            id = 127230,
            title = "Chainsaw Man",
            englishTitle = "Chainsaw Man",
            coverImage = "https://media.kitsu.app/anime/poster_images/43806/large.jpg",
            bannerImage = "https://image.tmdb.org/t/p/original/y4a6j1o57p3P60g3aV33tX4N2z7.jpg",
            description = "Denji is a young boy living a tragic life. After meeting Pochita the Chainsaw Devil, he gains the power to transform into Chainsaw Man.",
            episodes = 12,
            averageScore = 86,
            genres = listOf("Action", "Comedy", "Drama", "Supernatural"),
            studioName = "MAPPA",
            status = "FINISHED"
        ),
        Anime(
            id = 11061,
            title = "Hunter x Hunter (2011)",
            englishTitle = "Hunter x Hunter (2011)",
            coverImage = "https://media.kitsu.app/anime/poster_images/6448/large.jpg",
            bannerImage = "https://image.tmdb.org/t/p/original/1D9MclC91M9xP38gJ8Ym4i8v0e9.jpg",
            description = "Gon Freecss aspires to become a Hunter, an exceptional being capable of greatness, to find his father who abandoned him as an infant.",
            episodes = 148,
            averageScore = 90,
            genres = listOf("Action", "Adventure", "Fantasy"),
            studioName = "Madhouse",
            status = "FINISHED"
        ),
        Anime(
            id = 5114,
            title = "Fullmetal Alchemist: Brotherhood",
            englishTitle = "Fullmetal Alchemist: Brotherhood",
            coverImage = "https://media.kitsu.app/anime/poster_images/3936/large.jpg",
            bannerImage = "https://image.tmdb.org/t/p/original/2rmK7mnchEG9FL3L7Ik709jLTaB.jpg",
            description = "Two brothers search for a Philosopher's Stone after an attempt to revive their deceased mother goes terribly wrong.",
            episodes = 64,
            averageScore = 91,
            genres = listOf("Action", "Adventure", "Drama", "Fantasy"),
            studioName = "Bones",
            status = "FINISHED"
        ),
        Anime(
            id = 1535,
            title = "Death Note",
            englishTitle = "Death Note",
            coverImage = "https://media.kitsu.app/anime/poster_images/1376/large.jpg",
            bannerImage = "https://image.tmdb.org/t/p/original/96yL8sX4wVw0VfN0m6rW7a8456.jpg",
            description = "A high school student discovers a supernatural notebook that grants him the ability to kill anyone whose name and face he knows.",
            episodes = 37,
            averageScore = 86,
            genres = listOf("Mystery", "Psychological", "Supernatural", "Thriller"),
            studioName = "Madhouse",
            status = "FINISHED"
        ),
        Anime(
            id = 140960,
            title = "Spy x Family",
            englishTitle = "Spy x Family",
            coverImage = "https://media.kitsu.app/anime/poster_images/44549/large.jpg",
            bannerImage = "https://image.tmdb.org/t/p/original/nTvM4mhqNlHIvUkI1gqDYUMvg09.jpg",
            description = "A spy on an undercover mission gets married and adopts a telepathic child as part of his cover, unaware of his wife's secret job as an assassin.",
            episodes = 12,
            averageScore = 84,
            genres = listOf("Action", "Comedy"),
            studioName = "WIT Studio / CloverWorks",
            status = "FINISHED"
        ),
        Anime(
            id = 21519,
            title = "Mob Psycho 100",
            englishTitle = "Mob Psycho 100",
            coverImage = "https://media.kitsu.app/anime/poster_images/11579/large.jpg",
            bannerImage = "https://image.tmdb.org/t/p/original/gmECX1DvFnahQIhzptJ6gl2VTe7.jpg",
            description = "Kageyama Shigeo, nicknamed Mob, is an 8th grader with powerful psychic abilities who wishes to live an ordinary life.",
            episodes = 12,
            averageScore = 86,
            genres = listOf("Action", "Comedy", "Supernatural"),
            studioName = "Bones",
            status = "FINISHED"
        ),
        Anime(
            id = 101348,
            title = "Vinland Saga",
            englishTitle = "Vinland Saga",
            coverImage = "https://media.kitsu.app/anime/poster_images/41314/large.jpg",
            bannerImage = "https://image.tmdb.org/t/p/original/179rCi2qG1d9Z5eOzR0S2FpdP6X.jpg",
            description = "Thorfinn pursues vengeance against his father's killer while caught in a bloody war for the crown of England.",
            episodes = 24,
            averageScore = 88,
            genres = listOf("Action", "Adventure", "Drama"),
            studioName = "WIT Studio",
            status = "FINISHED"
        )
    )

    val curatedUpcomingAnime: List<Anime> by lazy {
        listOf(
            Anime(
                id = 45666,
                title = "Witch on the Holy Night",
                englishTitle = "Witch on the Holy Night",
                coverImage = "https://media.kitsu.app/anime/45666/poster_image/large-145a2514c9ecdb1b50cf7d89df36f88d.jpeg",
                bannerImage = "https://images.alphacoders.com/129/1296684.png",
                description = "A high-school student named Soujuuro Shizuki moves to Misaki City from the countryside and enters the secret magus world of Aoko Aozaki and Alice Kuonji.",
                episodes = 1,
                averageScore = 85,
                genres = listOf("Action", "Magic", "Supernatural"),
                status = "NOT_YET_RELEASED"
            ),
            Anime(
                id = 50016,
                title = "Cyberpunk: Edgerunners II",
                englishTitle = "Cyberpunk: Edgerunners II",
                coverImage = "https://media.kitsu.app/anime/50016/poster_image/large-c00e4abb4e356c1259a940b3bcf99894.jpeg",
                bannerImage = "https://image.tmdb.org/t/p/original/m99Fuh0bA8u014aR9M67g7y6Q7i.jpg",
                description = "The highly anticipated continuation to the critically acclaimed Cyberpunk: Edgerunners anime project by Studio Trigger and CD Projekt Red.",
                episodes = 10,
                averageScore = 88,
                genres = listOf("Action", "Sci-Fi", "Cyberpunk"),
                status = "NOT_YET_RELEASED"
            ),
            Anime(
                id = 47017,
                title = "Made in Abyss: Mezameru Shinpi",
                englishTitle = "Made in Abyss: Mezameru Shinpi",
                coverImage = "https://media.kitsu.app/anime/47017/poster_image/large-e82b48ac2bf47c53520d120eae09670a.jpeg",
                bannerImage = "https://image.tmdb.org/t/p/original/oXkscZ6X9N8F7lDqY3QZ81n9pU8.jpg",
                description = "The next theatrical installment in the Made in Abyss saga following Riko, Reg, and Nanachi deeper into the perilous unknown depths of the Abyss.",
                episodes = 1,
                averageScore = 89,
                genres = listOf("Adventure", "Mystery", "Drama", "Fantasy"),
                status = "NOT_YET_RELEASED"
            ),
            Anime(
                id = 50024,
                title = "Black Clover (Season 2)",
                englishTitle = "Black Clover (Season 2)",
                coverImage = "https://media.kitsu.app/anime/50024/poster_image/large-0afb0d5b575c5e4c5690c9016ea1233f.jpeg",
                bannerImage = "https://image.tmdb.org/t/p/original/96bW5H4qT6xH5KjV3fTf5m47g3y.jpg",
                description = "The official continuation of Asta and the Black Bulls' journey through the Spade Kingdom Raid arc and beyond.",
                episodes = 24,
                averageScore = 83,
                genres = listOf("Action", "Comedy", "Fantasy", "Shounen"),
                status = "NOT_YET_RELEASED"
            ),
            Anime(
                id = 49752,
                title = "Blue Box (Season 2)",
                englishTitle = "Blue Box (Season 2)",
                coverImage = "https://media.kitsu.app/anime/49752/poster_image/large-7cac4e9c740559275b458099b1608b12.jpeg",
                bannerImage = "https://image.tmdb.org/t/p/original/a07eY5F26E6q2Z3q4eEw7Yy1N6C.jpg",
                description = "The next chapter in Taiki and Chinatsu's sports and romance journey as badminton and basketball seasons heat up.",
                episodes = 12,
                averageScore = 84,
                genres = listOf("Romance", "Sports", "School"),
                status = "NOT_YET_RELEASED"
            ),
            Anime(
                id = 49883,
                title = "Aoashi (Season 2)",
                englishTitle = "Aoashi (Season 2)",
                coverImage = "https://media.kitsu.app/anime/49883/poster_image/large-d5bdc14bd1057e9ce15d4dc202c7c656.jpeg",
                bannerImage = "https://image.tmdb.org/t/p/original/oXq4x6YkMh2cO6X0Yq7Z5L0r9w4.jpg",
                description = "Ashito Aoi continues to develop his vision and playmaker mastery on the Esperion Tokyo youth soccer team.",
                episodes = 24,
                averageScore = 82,
                genres = listOf("Sports", "Drama", "Seinen"),
                status = "NOT_YET_RELEASED"
            ),
            Anime(
                id = 50626,
                title = "Ranma 1/2 (2024)",
                englishTitle = "Ranma 1/2",
                coverImage = "https://media.kitsu.app/anime/50626/poster_image/large-d7d98ce91ec169282830a8c3cf1a900d.jpeg",
                bannerImage = "https://image.tmdb.org/t/p/original/hQx1Jp9l2aB7n9K5Fq5m3t0R7b.jpg",
                description = "The modern reimagining by MAPPA of Rumiko Takahashi's timeless martial arts romantic comedy masterpiece.",
                episodes = 12,
                averageScore = 81,
                genres = listOf("Comedy", "Action", "Romance", "Martial Arts"),
                status = "NOT_YET_RELEASED"
            ),
            Anime(
                id = 49008,
                title = "Magical Knight Rayearth (2026)",
                englishTitle = "Magic Knight Rayearth (2026)",
                coverImage = "https://media.kitsu.app/anime/49008/poster_image/large-744c4394a93562c5863f0cd8d060319d.jpeg",
                bannerImage = "https://image.tmdb.org/t/p/original/4Y6hQyQ0t6iF7QZ7L8K9a7j5sY.jpg",
                description = "Celebrating the 30th anniversary of the legendary CLAMP series with an all-new animation project.",
                episodes = 12,
                averageScore = 80,
                genres = listOf("Fantasy", "Magic", "Mecha", "Adventure"),
                status = "NOT_YET_RELEASED"
            ),
            Anime(
                id = 48397,
                title = "A Returner's Magic Should Be Special (Season 2)",
                englishTitle = "A Returner's Magic Should Be Special (Season 2)",
                coverImage = "https://media.kitsu.app/anime/48397/poster_image/medium-0cbfc13b22520f404d39d1692dce0828.jpeg",
                bannerImage = "https://image.tmdb.org/t/p/original/4Y6hQyQ0t6iF7QZ7L8K9a7j5sY.jpg",
                description = "Desir Arman continues to alter fate and save humanity with his advanced tactical knowledge and spell inversion abilities.",
                episodes = 12,
                averageScore = 78,
                genres = listOf("Action", "Fantasy", "Time Travel"),
                status = "NOT_YET_RELEASED"
            ),
            Anime(
                id = 49971,
                title = "Sound! Euphonium: Final Movement Part 2",
                englishTitle = "Sound! Euphonium: Final Movement",
                coverImage = "https://media.kitsu.app/anime/49971/poster_image/medium-d539c8fbaf4478c0baddacfb595a2821.jpeg",
                bannerImage = "https://image.tmdb.org/t/p/original/a07eY5F26E6q2Z3q4eEw7Yy1N6C.jpg",
                description = "The conclusion to Kumiko Oumae's high school concert band journey by Kyoto Animation.",
                episodes = 1,
                averageScore = 87,
                genres = listOf("Drama", "Music", "School"),
                status = "NOT_YET_RELEASED"
            )
        )
    }

    val curatedRomanceAnime: List<Anime> by lazy {
        listOf(
            Anime(
                id = 41373,
                title = "Kaguya-sama: Love is War",
                englishTitle = "Kaguya-sama: Love is War",
                coverImage = "https://media.kitsu.app/anime/poster_images/41373/large.jpg",
                bannerImage = "https://image.tmdb.org/t/p/original/a07eY5F26E6q2Z3q4eEw7Yy1N6C.jpg",
                description = "Two genius high school student council leaders engage in an elaborate war of wits to force the other to confess first.",
                episodes = 12,
                averageScore = 88,
                genres = listOf("Comedy", "Romance", "Psychological"),
                status = "FINISHED"
            ),
            Anime(
                id = 43545,
                title = "Horimiya",
                englishTitle = "Horimiya",
                coverImage = "https://media.kitsu.app/anime/poster_images/43545/large.jpg",
                bannerImage = "https://image.tmdb.org/t/p/original/a07eY5F26E6q2Z3q4eEw7Yy1N6C.jpg",
                description = "Two vastly different high school classmates discover each other's secret personas outside of school and form an unexpected bond.",
                episodes = 13,
                averageScore = 82,
                genres = listOf("Romance", "School", "Slice of Life"),
                status = "FINISHED"
            ),
            Anime(
                id = 11614,
                title = "Your Name.",
                englishTitle = "Your Name.",
                coverImage = "https://media.kitsu.app/anime/poster_images/11614/large.jpg",
                bannerImage = "https://image.tmdb.org/t/p/original/a07eY5F26E6q2Z3q4eEw7Yy1N6C.jpg",
                description = "Two teenagers share a profound, magical connection upon discovering they are swapping bodies across time and distance.",
                episodes = 1,
                averageScore = 90,
                genres = listOf("Romance", "Drama", "Supernatural"),
                status = "FINISHED"
            ),
            Anime(
                id = 10028,
                title = "A Silent Voice",
                englishTitle = "A Silent Voice",
                coverImage = "https://media.kitsu.app/anime/poster_images/10028/large.jpg",
                bannerImage = "https://image.tmdb.org/t/p/original/a07eY5F26E6q2Z3q4eEw7Yy1N6C.jpg",
                description = "A former bully seeks redemption and reconciliation with a deaf girl he tormented during elementary school.",
                episodes = 1,
                averageScore = 89,
                genres = listOf("Drama", "Romance", "School"),
                status = "FINISHED"
            ),
            Anime(
                id = 8403,
                title = "Your Lie in April",
                englishTitle = "Your Lie in April",
                coverImage = "https://media.kitsu.app/anime/poster_images/8403/large.jpg",
                bannerImage = "https://image.tmdb.org/t/p/original/a07eY5F26E6q2Z3q4eEw7Yy1N6C.jpg",
                description = "A piano prodigy who lost his ability to play after his mother's death has his world rekindled by an eccentric violinist.",
                episodes = 22,
                averageScore = 87,
                genres = listOf("Drama", "Music", "Romance"),
                status = "FINISHED"
            ),
            Anime(
                id = 3532,
                title = "Toradora!",
                englishTitle = "Toradora!",
                coverImage = "https://media.kitsu.app/anime/poster_images/3532/large.jpg",
                bannerImage = "https://image.tmdb.org/t/p/original/a07eY5F26E6q2Z3q4eEw7Yy1N6C.jpg",
                description = "Ryuuji Takasu and Taiga Aisaka team up to help each other confess to their respective best friends, only to grow closer together.",
                episodes = 25,
                averageScore = 83,
                genres = listOf("Comedy", "Romance", "School"),
                status = "FINISHED"
            )
        )
    }

    fun extractFranchiseKey(title: String): String {
        return title.lowercase()
            .replace(Regex("""(?i)\s*[:,–-]?\s*(season\s*\d+|\d+(nd|rd|th|st)\s*season|part\s*\d+|cour\s*\d+|the\s*final\s*season|the\s*movie|:\s*season.*|:\s*part.*|;\s*season.*|2nd\s*season|3rd\s*season|4th\s*season|\(\d{4}\)).*"""), "")
            .trim()
    }

    fun deduplicateFranchises(list: List<Anime>): List<Anime> {
        val seen = mutableSetOf<String>()
        val result = mutableListOf<Anime>()
        for (anime in list) {
            val title = anime.englishTitle?.takeIf { it.isNotBlank() } ?: anime.title
            val key = extractFranchiseKey(title)
            if (key.isNotBlank() && seen.add(key)) {
                result.add(anime)
            }
        }
        return result
    }

    suspend fun getTrending(): List<Anime> = withContext(Dispatchers.IO) {
        // Try Kitsu trending first
        val kitsuList = fetchKitsu("https://kitsu.io/api/edge/trending/anime?limit=20")
        if (kitsuList.isNotEmpty()) {
            val curated = deduplicateFranchises(kitsuList)
            if (curated.size < 10) {
                // Interleave premier modern hits (Solo Leveling, JJK, Demon Slayer, etc.) if missing
                val existingKeys = curated.map { extractFranchiseKey(it.englishTitle ?: it.title) }.toSet()
                val missingBlockbusters = curatedBlockbusterHits.filter { 
                    val key = extractFranchiseKey(it.englishTitle ?: it.title)
                    !existingKeys.contains(key)
                }
                return@withContext (curated + missingBlockbusters).distinctBy { it.id }.take(20)
            }
            return@withContext curated.take(20)
        }

        // Fallback to curated blockbusters directly
        curatedBlockbusterHits
    }

    suspend fun getPopular(): List<Anime> = withContext(Dispatchers.IO) {
        val kitsuList = fetchKitsu("https://kitsu.io/api/edge/anime?sort=-userCount&page[limit]=25")
        if (kitsuList.isNotEmpty()) {
            val deduped = deduplicateFranchises(kitsuList)
            return@withContext deduped.take(20)
        }

        curatedBlockbusterHits
    }

    suspend fun getTopRated(): List<Anime> = withContext(Dispatchers.IO) {
        val kitsuList = fetchKitsu("https://kitsu.io/api/edge/anime?sort=-averageRating&page[limit]=25")
        if (kitsuList.isNotEmpty()) {
            val deduped = deduplicateFranchises(kitsuList)
            return@withContext deduped.take(20)
        }

        curatedBlockbusterHits.sortedByDescending { it.averageScore ?: 0 }
    }

    suspend fun getSeasonal(): List<Anime> = withContext(Dispatchers.IO) {
        // AniLight schedule contains currently airing anime this season
        try {
            val response = client.get("https://api.anilight.live/api/schedule") {
                header("User-Agent", userAgent)
            }
            if (response.status == HttpStatusCode.OK) {
                val array = json.parseToJsonElement(response.bodyAsText()) as? JsonArray ?: return@withContext emptyList()
                val items = array.mapNotNull { entry ->
                    val entryObj = (entry as? JsonElement)?.takeIf { it !is JsonNull } as? JsonObject ?: return@mapNotNull null
                    val a = (entryObj["anime"] as? JsonElement)?.takeIf { it !is JsonNull } as? JsonObject ?: return@mapNotNull null
                    mapAniLightJsonToAnime(a)
                }.filter {
                    it.coverImage.isNotBlank() && it.title.isNotBlank() && (it.averageScore ?: 100) >= 65 && (it.episodes ?: 0) < 200
                }
                val deduped = deduplicateFranchises(items)
                if (deduped.isNotEmpty()) return@withContext deduped.take(20)
            }
        } catch (e: Exception) {
            android.util.Log.e("BackupAnimeApi", "Failed to fetch seasonal from AniLight schedule", e)
        }

        // Kitsu seasonal fallback
        val kitsuSeasonal = fetchKitsu("https://kitsu.io/api/edge/anime?filter[status]=current&sort=-userCount&page[limit]=25")
        if (kitsuSeasonal.isNotEmpty()) {
            val seasonalFiltered = kitsuSeasonal.filter { anime ->
                (anime.episodes ?: 0) < 200
            }
            if (seasonalFiltered.isNotEmpty()) {
                return@withContext deduplicateFranchises(seasonalFiltered).take(20)
            }
        }

        curatedBlockbusterHits.filter { it.status == "RELEASING" }
    }

    suspend fun getUpcoming(): List<Anime> = withContext(Dispatchers.IO) {
        val kitsuList = fetchKitsu("https://kitsu.io/api/edge/anime?filter[status]=upcoming&sort=-userCount&page[limit]=25")
        if (kitsuList.isNotEmpty()) {
            val deduped = deduplicateFranchises(kitsuList).filter { anime ->
                anime.status.let { s ->
                    s.contains("UPCOMING", ignoreCase = true) ||
                    s.contains("NOT_YET", ignoreCase = true) ||
                    s.contains("TBA", ignoreCase = true)
                }
            }
            val combined = (deduped + curatedUpcomingAnime).distinctBy { it.id }
            return@withContext combined.take(20)
        }

        curatedUpcomingAnime
    }

    suspend fun getActionAnime(): List<Anime> = withContext(Dispatchers.IO) {
        val kitsuList = fetchKitsu("https://kitsu.io/api/edge/anime?filter[categories]=action&include=categories&sort=-userCount&page[limit]=25")
        if (kitsuList.isNotEmpty()) {
            val actionItems = kitsuList.map { anime ->
                if (anime.genres.any { it.contains("Action", ignoreCase = true) }) anime
                else anime.copy(genres = (anime.genres + "Action").distinct())
            }
            return@withContext deduplicateFranchises(actionItems).take(20)
        }

        curatedBlockbusterHits.filter { it.genres.any { g -> g.contains("Action", ignoreCase = true) } }
    }

    suspend fun getRomanceAnime(): List<Anime> = withContext(Dispatchers.IO) {
        val kitsuList = fetchKitsu("https://kitsu.io/api/edge/anime?filter[categories]=romance&include=categories&sort=-userCount&page[limit]=25")
        if (kitsuList.isNotEmpty()) {
            val romanceItems = kitsuList.map { anime ->
                if (anime.genres.any { it.contains("Romance", ignoreCase = true) }) anime
                else anime.copy(genres = (anime.genres + "Romance").distinct())
            }
            val deduped = deduplicateFranchises(romanceItems)
            val combined = (deduped + curatedRomanceAnime).distinctBy { it.id }
            return@withContext combined.take(20)
        }

        curatedRomanceAnime
    }

    suspend fun searchAnime(query: String, page: Int = 1): SearchPage = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext SearchPage(emptyList(), false, page)

        val encoded = URLEncoder.encode(trimmed, "UTF-8")
        val offset = (page - 1) * 20

        // 1. Concurrently query AniLight AND Kitsu to guarantee all Movies, ONAs, OVAs, and TV series are found
        val aniLightDeferred = async {
            try {
                val response = client.get("https://api.anilight.live/api/search?q=$encoded") {
                    header("User-Agent", userAgent)
                }
                if (response.status == HttpStatusCode.OK) {
                    val array = json.parseToJsonElement(response.bodyAsText()) as? JsonArray ?: return@async emptyList<Anime>()
                    array.mapNotNull { element ->
                        val obj = (element as? JsonElement)?.takeIf { it !is JsonNull } as? JsonObject ?: return@mapNotNull null
                        mapAniLightJsonToAnime(obj)
                    }.filter { it.title.isNotBlank() && it.coverImage.isNotBlank() }
                } else emptyList()
            } catch (e: Exception) {
                android.util.Log.w("BackupAnimeApi", "AniLight search failed for: $trimmed", e)
                emptyList()
            }
        }

        val kitsuDeferred = async {
            try {
                val kitsuUrl = "https://kitsu.io/api/edge/anime?filter[text]=$encoded&page[limit]=20&page[offset]=$offset"
                fetchKitsu(kitsuUrl)
            } catch (e: Exception) {
                android.util.Log.w("BackupAnimeApi", "Kitsu search failed for: $trimmed", e)
                emptyList()
            }
        }

        val aniLightList = aniLightDeferred.await()
        val kitsuList = kitsuDeferred.await()

        // Merge results: AniLight first, then append distinct Kitsu entries (crucial for Movies/OVAs/ONAs)
        val combined = mutableListOf<Anime>()
        val seenKeys = mutableSetOf<String>()

        fun normalizeTitleKey(t: String): String {
            return t.lowercase().replace(Regex("[^a-z0-9]"), "").trim()
        }

        for (item in aniLightList) {
            val key = normalizeTitleKey(item.englishTitle ?: item.title)
            if (key.isNotBlank() && seenKeys.add(key)) {
                combined.add(item)
            }
        }

        for (item in kitsuList) {
            val key = normalizeTitleKey(item.englishTitle ?: item.title)
            if (key.isNotBlank() && seenKeys.add(key)) {
                combined.add(item)
            }
        }

        // When user searches for "bleach", prioritize The Calamity at index 0
        if (trimmed.contains("bleach", ignoreCase = true) && combined.none { it.id == 185874 }) {
            combined.add(0, bleachTybwAnime)
        }

        SearchPage(
            results = combined,
            hasNextPage = (kitsuList.size >= 20),
            currentPage = page
        )
    }

    suspend fun getAnimeDetail(id: Int): Anime? = withContext(Dispatchers.IO) {
        if (id == 185874) return@withContext bleachTybwAnime

        // Check curated blockbuster hits first
        curatedBlockbusterHits.find { it.id == id }?.let { return@withContext it }

        // Try AniLight search with common known IDs
        try {
            val searchUrl = "https://api.anilight.live/api/search?q=$id"
            val response = client.get(searchUrl) {
                header("User-Agent", userAgent)
            }
            if (response.status == HttpStatusCode.OK) {
                val array = json.parseToJsonElement(response.bodyAsText()) as? JsonArray
                if (array != null) {
                    for (el in array) {
                        val obj = (el as? JsonElement)?.takeIf { it !is JsonNull } as? JsonObject ?: continue
                        val anilistId = obj["anilistId"]?.jsonPrimitive?.intOrNull
                        if (anilistId == id) {
                            return@withContext mapAniLightJsonToAnime(obj)
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // Try Kitsu by ID
        try {
            val response = client.get("https://kitsu.io/api/edge/anime/$id") {
                header("User-Agent", userAgent)
                header("Accept", kitsuAccept)
            }
            if (response.status == HttpStatusCode.OK) {
                val root = json.parseToJsonElement(response.bodyAsText()) as? JsonObject
                val dataObj = (root?.get("data") as? JsonElement)?.takeIf { it !is JsonNull } as? JsonObject
                if (dataObj != null) {
                    val attrs = (dataObj["attributes"] as? JsonElement)?.takeIf { it !is JsonNull } as? JsonObject ?: JsonObject(emptyMap())
                    return@withContext mapKitsuAttributesToAnime(dataObj["id"]?.jsonPrimitive?.content ?: id.toString(), attrs)
                }
            }
        } catch (_: Exception) {}

        null
    }

    fun ensureBleachFirst(list: List<Anime>): List<Anime> {
        val deduped = deduplicateFranchises(list)
        val mutable = deduped.toMutableList()
        // Ensure Bleach TYBW Part 4 (The Calamity) is at index 0 if not present
        if (mutable.none { it.id == 185874 }) {
            mutable.add(0, bleachTybwAnime)
        } else {
            val idx = mutable.indexOfFirst { it.id == 185874 }
            if (idx > 0) {
                val item = mutable.removeAt(idx)
                mutable.add(0, item)
            }
        }
        return mutable
    }

    private suspend fun fetchKitsu(url: String): List<Anime> {
        return try {
            val response = client.get(url) {
                header("User-Agent", userAgent)
                header("Accept", kitsuAccept)
            }
            if (response.status == HttpStatusCode.OK) {
                val root = json.parseToJsonElement(response.bodyAsText()) as? JsonObject ?: return emptyList()
                val dataArray = (root["data"] as? JsonElement)?.takeIf { it !is JsonNull } as? JsonArray ?: return emptyList()

                // Parse category mapping from included array if present
                val categoryMap = mutableMapOf<String, String>()
                val includedArray = (root["included"] as? JsonElement)?.takeIf { it !is JsonNull } as? JsonArray
                includedArray?.forEach { inc ->
                    val incObj = (inc as? JsonElement)?.takeIf { it !is JsonNull } as? JsonObject ?: return@forEach
                    val type = incObj["type"]?.jsonPrimitive?.contentOrNull
                    val id = incObj["id"]?.jsonPrimitive?.contentOrNull
                    if (type == "categories" && id != null) {
                        val catAttrs = (incObj["attributes"] as? JsonElement)?.takeIf { it !is JsonNull } as? JsonObject
                        val catTitle = catAttrs?.get("title")?.jsonPrimitive?.contentOrNull
                            ?: catAttrs?.get("slug")?.jsonPrimitive?.contentOrNull
                        if (!catTitle.isNullOrBlank()) {
                            categoryMap[id] = catTitle
                        }
                    }
                }

                dataArray.mapNotNull { item ->
                    val obj = (item as? JsonElement)?.takeIf { it !is JsonNull } as? JsonObject ?: return@mapNotNull null
                    val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val attrs = (obj["attributes"] as? JsonElement)?.takeIf { it !is JsonNull } as? JsonObject ?: return@mapNotNull null

                    val rels = (obj["relationships"] as? JsonElement)?.takeIf { it !is JsonNull } as? JsonObject
                    val catRel = (rels?.get("categories") as? JsonElement)?.takeIf { it !is JsonNull } as? JsonObject
                    val catData = (catRel?.get("data") as? JsonElement)?.takeIf { it !is JsonNull } as? JsonArray
                    val genres = catData?.mapNotNull { catRef ->
                        val catId = (catRef as? JsonObject)?.get("id")?.jsonPrimitive?.contentOrNull
                        catId?.let { categoryMap[it] }
                    } ?: emptyList()

                    mapKitsuAttributesToAnime(id, attrs, genres)
                }.filter { it.title.isNotBlank() && it.coverImage.isNotBlank() }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            android.util.Log.e("BackupAnimeApi", "Failed to fetch from Kitsu: $url", e)
            emptyList()
        }
    }

    private suspend fun fetchAniLightSearches(queries: List<String>): List<Anime> {
        val results = mutableListOf<Anime>()
        for (q in queries) {
            try {
                val encoded = URLEncoder.encode(q, "UTF-8")
                val response = client.get("https://api.anilight.live/api/search?q=$encoded") {
                    header("User-Agent", userAgent)
                }
                if (response.status == HttpStatusCode.OK) {
                    val array = json.parseToJsonElement(response.bodyAsText()) as? JsonArray
                    if (array != null && array.isNotEmpty()) {
                        val firstObj = (array[0] as? JsonElement)?.takeIf { it !is JsonNull } as? JsonObject
                        if (firstObj != null) {
                            mapAniLightJsonToAnime(firstObj)?.let { anime ->
                                if (results.none { it.id == anime.id }) {
                                    results.add(anime)
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return results
    }

    private fun mapKitsuAttributesToAnime(idStr: String, attrs: JsonObject, genres: List<String> = emptyList()): Anime {
        val intId = idStr.toIntOrNull() ?: Math.abs(idStr.hashCode())
        val titles = (attrs["titles"] as? JsonElement)?.takeIf { it !is JsonNull } as? JsonObject
        val enTitle = titles?.get("en")?.jsonPrimitive?.contentOrNull
            ?: titles?.get("en_us")?.jsonPrimitive?.contentOrNull
        val canonical = attrs["canonicalTitle"]?.jsonPrimitive?.contentOrNull ?: "Anime Title"
        val poster = (attrs["posterImage"] as? JsonElement)?.takeIf { it !is JsonNull } as? JsonObject
        val cover = (attrs["coverImage"] as? JsonElement)?.takeIf { it !is JsonNull } as? JsonObject

        val coverImg = poster?.get("large")?.jsonPrimitive?.contentOrNull
            ?: poster?.get("original")?.jsonPrimitive?.contentOrNull
            ?: poster?.get("medium")?.jsonPrimitive?.contentOrNull
            ?: ""

        val bannerImg = cover?.get("large")?.jsonPrimitive?.contentOrNull
            ?: cover?.get("original")?.jsonPrimitive?.contentOrNull
            ?: coverImg

        val ratingStr = attrs["averageRating"]?.jsonPrimitive?.contentOrNull
        val score = ratingStr?.toDoubleOrNull()?.toInt()

        val ytId = attrs["youtubeVideoId"]?.jsonPrimitive?.contentOrNull
        val trailer = if (!ytId.isNullOrBlank()) {
            "https://www.youtube-nocookie.com/embed/$ytId?autoplay=1&mute=1"
        } else null

        val epCount = attrs["episodeCount"]?.jsonPrimitive?.intOrNull
        val statusStr = attrs["status"]?.jsonPrimitive?.contentOrNull?.uppercase() ?: "FINISHED"
        val subtype = attrs["subtype"]?.jsonPrimitive?.contentOrNull?.uppercase()
        val format = when (subtype) {
            "MOVIE" -> "MOVIE"
            "OVA" -> "OVA"
            "ONA" -> "ONA"
            "SPECIAL" -> "SPECIAL"
            "MUSIC" -> "MUSIC"
            "TV" -> "TV"
            else -> subtype
        }

        return Anime(
            id = intId,
            title = enTitle ?: canonical,
            englishTitle = enTitle,
            coverImage = coverImg,
            bannerImage = bannerImg,
            description = attrs["synopsis"]?.jsonPrimitive?.contentOrNull ?: attrs["description"]?.jsonPrimitive?.contentOrNull,
            episodes = epCount,
            format = format,
            averageScore = score,
            genres = if (genres.isNotEmpty()) genres else listOf("Animation"),
            status = statusStr,
            trailerUrl = trailer
        )
    }

    private fun mapAniLightJsonToAnime(obj: JsonObject): Anime? {
        val rawId = obj["anilistId"]?.jsonPrimitive?.intOrNull ?: obj["id"]?.jsonPrimitive?.intOrNull ?: return null
        val titleObj = (obj["title"] as? JsonElement)?.takeIf { it !is JsonNull } as? JsonObject
        val title = titleObj?.get("english")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: titleObj?.get("romaji")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: titleObj?.get("native")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: "Anime Title"
        val englishTitle = titleObj?.get("english")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

        val coverObj = (obj["coverImage"] as? JsonElement)?.takeIf { it !is JsonNull } as? JsonObject
        val coverImage = coverObj?.get("extraLarge")?.jsonPrimitive?.contentOrNull
            ?: coverObj?.get("large")?.jsonPrimitive?.contentOrNull
            ?: ""

        val tmdbObj = (obj["tmdb"] as? JsonElement)?.takeIf { it !is JsonNull } as? JsonObject
        val bannerImage = obj["bannerImage"]?.jsonPrimitive?.contentOrNull
            ?: tmdbObj?.get("backdrop")?.jsonPrimitive?.contentOrNull
            ?: coverImage

        val trailerObj = (obj["trailer"] as? JsonElement)?.takeIf { it !is JsonNull } as? JsonObject
        val trailerId = trailerObj?.get("id")?.jsonPrimitive?.contentOrNull
        val trailerSite = trailerObj?.get("site")?.jsonPrimitive?.contentOrNull
        val trailerUrl = if (trailerSite == "youtube" && trailerId != null) {
            "https://www.youtube-nocookie.com/embed/$trailerId?autoplay=1&mute=1"
        } else null

        val genres = (obj["genres"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
        val score = obj["averageScore"]?.jsonPrimitive?.intOrNull
        val episodes = obj["episodes"]?.jsonPrimitive?.intOrNull
        val status = obj["status"]?.jsonPrimitive?.contentOrNull ?: "FINISHED"
        val desc = obj["description"]?.jsonPrimitive?.contentOrNull
        val formatStr = obj["format"]?.jsonPrimitive?.contentOrNull?.uppercase()
            ?: obj["type"]?.jsonPrimitive?.contentOrNull?.uppercase()

        return Anime(
            id = rawId,
            title = title,
            englishTitle = englishTitle,
            coverImage = coverImage,
            bannerImage = bannerImage,
            description = desc,
            episodes = episodes,
            format = formatStr,
            averageScore = score,
            genres = genres,
            status = status,
            trailerUrl = trailerUrl
        )
    }
}
