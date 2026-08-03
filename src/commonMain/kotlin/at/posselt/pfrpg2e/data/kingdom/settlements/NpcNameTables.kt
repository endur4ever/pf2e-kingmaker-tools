package at.posselt.pfrpg2e.data.kingdom.settlements

/**
 * Name tables for the Stolen Lands / Brevoy / River Kingdoms region.
 *
 * Three cultural flavours:
 * - Issian: cold northern kingdom (Slavic-flavored)
 * - Iobarian: old eastern kingdom (mixed Slavic/medieval)
 * - Taldan: decadent southern empire (Roman/Italian-flavored)
 *
 * The Stolen Lands sit at the crossroads of all three, so a settlement's
 * population may draw from any mix of these traditions.
 */

enum class NameCulture {
    ISSIAN,
    IOBARIAN,
    TALDAN,
}

object NpcNameTables {

    // ── Issian given names (Slavic-flavored) ──────────────────────────

    val issianMaleGivenNames = listOf(
        "Aleksei", "Andrei", "Anton", "Bogdan", "Boris",
        "Dmitri", "Dragomir", "Egor", "Fyodor", "Gavril",
        "Grigori", "Igor", "Ivan", "Kirill", "Kozma",
        "Lev", "Maksim", "Mikhail", "Nikita", "Oleg",
        "Pavel", "Radomir", "Rurik", "Semyon", "Sergei",
        "Stefan", "Tikhon", "Vadim", "Valentin", "Vasily",
        "Viktor", "Vladislav", "Yakov", "Yaroslav", "Yuri",
    )

    val issianFemaleGivenNames = listOf(
        "Aleksandra", "Anastasia", "Anya", "Bronislava", "Darya",
        "Ekaterina", "Elena", "Galina", "Inna", "Irina",
        "Katya", "Kira", "Kseniya", "Larisa", "Ludmila",
        "Margarita", "Marina", "Masha", "Nadezhda", "Natalya",
        "Oksana", "Olga", "Polina", "Raisa", "Svetlana",
        "Tamara", "Tatiana", "Uliana", "Valentina", "Vera",
        "Veronika", "Viktoria", "Yelena", "Yevdokiya", "Zoya",
    )

    val issianSurnames = listOf(
        "Avilov", "Babin", "Chernov", "Doronin", "Eletsky",
        "Fedorov", "Golovin", "Ilyin", "Kazakov", "Korovin",
        "Kozlov", "Kuznetsov", "Lebedev", "Makarov", "Medvedev",
        "Morozov", "Nikitin", "Orlov", "Pavlov", "Popov",
        "Romanov", "Semyonov", "Sokolov", "Stepanov", "Tarasov",
        "Volkov", "Vorontsov", "Zaitsev", "Zhdanov", "Zhukov",
    )

    // ── Iobarian given names (mixed Slavic/medieval) ──────────────────

    val iobarianMaleGivenNames = listOf(
        "Aldric", "Bogumil", "Borislav", "Branimir", "Casimir",
        "Dalibor", "Dobromil", "Dragoslav", "Gostislav", "Jaromir",
        "Kresimir", "Lubomir", "Milodrag", "Miroslav", "Mstivoj",
        "Ostrozrad", "Premysl", "Radoslav", "Ratibor", "Rostislav",
        "Slavomir", "Sobieslav", "Stanislav", "Svatopluk", "Tomislav",
        "Venceslav", "Vlastimir", "Vojtech", "Vratislav", "Zbignev",
        "Zdeslav", "Zitomir", "Bogdan", "Goran", "Lubor",
    )

    val iobarianFemaleGivenNames = listOf(
        "Bozena", "Dobroslava", "Dragomira", "Gorana", "Jelena",
        "Katarina", "Kvetoslava", "Lidmila", "Ludmila", "Milena",
        "Miloslava", "Mirka", "Miroslava", "Nevenka", "Radmila",
        "Rostislava", "Slavenka", "Stanislava", "Svetlana", "Tatiana",
        "Vesna", "Vjera", "Vladana", "Vlasta", "Zdenka",
        "Zlata", "Zora", "Zvonimira", "Biljana", "Bogdana",
        "Dobrila", "Gordana", "Krasimira", "Ljubica", "Snezana",
    )

    val iobarianSurnames = listOf(
        "Bilinski", "Cherny", "Dobrin", "Dragovic", "Goranski",
        "Hrvatin", "Jankovic", "Kovacek", "Kralj", "Mihaljevic",
        "Milanovic", "Novak", "Obradovic", "Petrovic", "Radic",
        "Savic", "Stanimirovic", "Stojanovic", "Tomasic", "Veselic",
        "Vukovic", "Zoric", "Blagojevic", "Bogdanovic", "Cvetkovic",
        "Dimitrijevic", "Filipovic", "Grujic", "Ilic", "Jovanovic",
    )

    // ── Taldan given names (Roman/Italian-flavored) ───────────────────

    val taldanMaleGivenNames = listOf(
        "Alessandro", "Ambrogio", "Aurelio", "Baldassare", "Bartolomeo",
        "Benedetto", "Bernardo", "Cesare", "Claudio", "Cornelio",
        "Dario", "Demetrio", "Emilio", "Enrico", "Fabrizio",
        "Federico", "Flavio", "Francesco", "Gaetano", "Gennaro",
        "Giovanni", "Giulio", "Gregorio", "Horatio", "Lorenzo",
        "Luca", "Marco", "Marcello", "Massimo", "Niccolo",
        "Ottavio", "Pietro", "Raffaele", "Salvatore", "Vittorio",
    )

    val taldanFemaleGivenNames = listOf(
        "Alessandra", "Beatrice", "Bianca", "Camilla", "Carlotta",
        "Cecilia", "Chiara", "Clarissa", "Costanza", "Diamante",
        "Donatella", "Elisabetta", "Emilia", "Fiorenza", "Flavia",
        "Francesca", "Ginevra", "Giulia", "Isabella", "Leonora",
        "Livia", "Lucia", "Lucrezia", "Margherita", "Mirabella",
        "Nerina", "Ottavia", "Patrizia", "Serafina", "Stella",
        "Valentina", "Violetta", "Vittoria", "Zaffira", "Zita",
    )

    val taldanSurnames = listOf(
        "Aldrovandi", "Barbieri", "Benedetti", "Borghese", "Capuleti",
        "Colonna", "Conti", "Cornaro", "D'Este", "Farnese",
        "Gonzaga", "Grimaldi", "Loredan", "Machiavelli", "Medici",
        "Monteverdi", "Orsini", "Pallavicini", "Pazzi", "Ravenna",
        "Sforza", "Strozzi", "Torlonia", "Valeri", "Visconti",
        "Zanetti", "Aretino", "Boccaccio", "Cellini", "Donatello",
    )

    // ── Surname suffixes for patronymic/occupational generation ────────

    val issianPatronymicSuffixes = listOf("ovich", "evich", "ich")
    val issianOccupationalSuffixes = listOf("nikov", "sky", "ets")

    // ── All names aggregated by culture ────────────────────────────────

    fun maleGivenNames(culture: NameCulture): List<String> = when (culture) {
        NameCulture.ISSIAN -> issianMaleGivenNames
        NameCulture.IOBARIAN -> iobarianMaleGivenNames
        NameCulture.TALDAN -> taldanMaleGivenNames
    }

    fun femaleGivenNames(culture: NameCulture): List<String> = when (culture) {
        NameCulture.ISSIAN -> issianFemaleGivenNames
        NameCulture.IOBARIAN -> iobarianFemaleGivenNames
        NameCulture.TALDAN -> taldanFemaleGivenNames
    }

    fun surnames(culture: NameCulture): List<String> = when (culture) {
        NameCulture.ISSIAN -> issianSurnames
        NameCulture.IOBARIAN -> iobarianSurnames
        NameCulture.TALDAN -> taldanSurnames
    }

    /** All male given names across all cultures. */
    val allMaleGivenNames: List<String> =
        issianMaleGivenNames + iobarianMaleGivenNames + taldanMaleGivenNames

    /** All female given names across all cultures. */
    val allFemaleGivenNames: List<String> =
        issianFemaleGivenNames + iobarianFemaleGivenNames + taldanFemaleGivenNames

    /** All surnames across all cultures. */
    val allSurnames: List<String> =
        issianSurnames + iobarianSurnames + taldanSurnames
}
