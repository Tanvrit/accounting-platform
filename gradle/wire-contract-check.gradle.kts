// wireContractCheck — a mechanical guard for three wire-contract defect classes the 2026-09
// core audit found by hand (report 02, closing recommendation). Applied from build.gradle.kts.
//
// Scans every <module>/src/commonMain/kotlin tree (build/ excluded) and reports file:line for:
//
//  (a) an `Update*Request` whose primary constructor declares a NON-NULLABLE primitive
//      (Boolean/Int/Long/Double/Float/Short/Byte/Char) with a default. AppJson runs with
//      encodeDefaults = false, so a value equal to the default is never sent and the server
//      cannot tell "keep what is stored" from "set it to the default". Make it `T? = null`
//      (null means keep).
//  (b) an `override var id` with no `@SerialName(` on its own line or on an annotation-only
//      line directly above it (the nearest non-blank code line). BaseDataClass's
//      `@SerialName("_id")` does not inherit onto an override, so a bare override silently
//      changes the wire key — pin it explicitly.
//  (c) two TOP-LEVEL (column-0, non-private) @Serializable classes/objects in different files
//      that share a simple name. Runtime lookups keyed on the simple name (Koin, serializer
//      registries) collide. Nested declarations (AuthRoute.Address vs AccountRoute.Address)
//      are indented and therefore excluded by design — they are addressed via their parent.
//
// `./gradlew wireContractCheck` fails on any finding and is wired into every module's `check`.
// `-PwireContract.strict=false` prints the same report without failing. The report is also
// written to build/reports/wire-contract/report.txt.
//
// `wireContractSelfTest` (a dependency of the check) runs the same scanner over
// gradle/wire-contract-fixtures/*.kt.txt and fails unless it reproduces exactly the findings
// pinned below — the scanner's regression test. The fixtures are .kt.txt so no Kotlin tool
// compiles or lints them.

abstract class WireContractCheck : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:Internal
    abstract val rootDirectory: DirectoryProperty

    @get:Input
    abstract val strict: Property<Boolean>

    /** Self-test mode: findings must equal [expectedFindings] exactly instead of being gated. */
    @get:Input
    abstract val selfTest: Property<Boolean>

    @get:Input
    abstract val expectedFindings: ListProperty<String>

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    init {
        selfTest.convention(false)
        expectedFindings.convention(emptyList())
    }

    private data class Finding(
        val rule: Char,
        val file: String,
        val line: Int,
        val detail: String,
    ) {
        fun compact() = "$rule $file:$line $detail"
    }

    private val updateRequestClass = Regex("""\bclass\s+(Update\w*Request)\s*\(""")
    private val primitiveWithDefault =
        Regex("""(?:\b(?:val|var)\s+)?\b(\w+)\s*:\s*(Boolean|Int|Long|Double|Float|Short|Byte|Char)\s*=""")
    private val overrideVarId = Regex("""\boverride\s+var\s+id\b""")
    private val propertyDecl = Regex("""\b(?:val|var)\s+\w+""")
    private val anyClassDecl = Regex("""\b(?:class|object)\s+(\w+)""")
    private val serializable = Regex("""@(?:kotlinx\.serialization\.)?Serializable\b""")

    // Anchored at column 0; `private` (and `protected`) are deliberately absent from the
    // modifier list, so a private top-level declaration never matches.
    private val topLevelDecl =
        Regex(
            """^(?:@[\w.]+(?:\([^)]*\))?\s+)*""" +
                """(?:(?:public|internal|data|sealed|enum|abstract|open|value|final|expect|actual)\s+)*""" +
                """(?:class|object)\s+(\w+)""",
        )

    @TaskAction
    fun scan() {
        val root = rootDirectory.get().asFile
        val findings = mutableListOf<Finding>()
        val topLevelSerializable = sortedMapOf<String, MutableList<Pair<String, Int>>>()

        sources.files.sortedBy { it.invariantSeparatorsPath }.forEach { file ->
            val rel = file.relativeTo(root).invariantSeparatorsPath
            val code = codeOnly(file.readLines())
            scanUpdateRequests(rel, code, findings)
            scanOverrideIds(rel, code, findings)
            collectTopLevelSerializable(rel, code, topLevelSerializable)
        }
        topLevelSerializable
            .filterValues { decls -> decls.map { it.first }.distinct().size > 1 }
            .forEach { (name, decls) -> decls.forEach { (f, l) -> findings += Finding('c', f, l, name) } }

        if (selfTest.get()) {
            verifySelfTest(findings)
            return
        }

        val report = render(findings)
        writeReport(report)
        when {
            findings.isEmpty() -> logger.lifecycle("wireContractCheck: clean")
            strict.get() -> {
                logger.error(report)
                throw GradleException(
                    "wireContractCheck: ${findings.size} wire-contract violation(s) — see the report above " +
                        "(also ${reportFile.get().asFile}). Run with -PwireContract.strict=false to report without failing.",
                )
            }
            else -> {
                logger.quiet(report)
                logger.quiet("wireContractCheck: report-only (-PwireContract.strict=false) — not failing.")
            }
        }
    }

    private fun verifySelfTest(findings: List<Finding>) {
        val actual = findings.map { it.compact() }.sorted()
        val expected = expectedFindings.get().sorted()
        writeReport(actual.joinToString("\n", postfix = "\n"))
        if (actual != expected) {
            throw GradleException(
                buildString {
                    appendLine("$name: the wire-contract scanner no longer reproduces its fixtures.")
                    appendLine("missing: ${expected - actual.toSet()}")
                    appendLine("unexpected: ${actual - expected.toSet()}")
                },
            )
        }
        logger.lifecycle("$name: ${actual.size} expected finding(s) reproduced")
    }

    private fun writeReport(text: String) {
        reportFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(text)
        }
    }

    private fun render(findings: List<Finding>): String {
        val a = findings.filter { it.rule == 'a' }
        val b = findings.filter { it.rule == 'b' }
        val c = findings.filter { it.rule == 'c' }
        return buildString {
            appendLine("wireContractCheck — ${findings.size} finding(s)")
            appendLine()
            appendLine(
                "(a) Update*Request non-nullable primitive with a default — ${a.size} field(s) in " +
                    "${a.map { it.file }.distinct().size} file(s). Make each `T? = null` (null = keep).",
            )
            a.forEach { appendLine("  ${it.file}:${it.line}: ${it.detail}") }
            appendLine()
            appendLine("(b) `override var id` without @SerialName — ${b.size}. Pin @SerialName(\"id\").")
            b.forEach { appendLine("  ${it.file}:${it.line}: ${it.detail}") }
            appendLine()
            val names = c.groupBy { it.detail }
            appendLine(
                "(c) duplicate top-level @Serializable simple names — ${names.size} name(s). " +
                    "Rename one side (keep a @Deprecated typealias for source compatibility).",
            )
            names.forEach { (name, decls) ->
                appendLine("  $name")
                decls.forEach { appendLine("    ${it.file}:${it.line}") }
            }
        }
    }

    // Rule (a): walk the primary constructor by paren depth so multi-line constructors,
    // nested default-value calls and one-line constructors are all handled.
    private fun scanUpdateRequests(
        file: String,
        code: List<String>,
        out: MutableList<Finding>,
    ) {
        var i = 0
        while (i < code.size) {
            val match = updateRequestClass.find(code[i])
            if (match == null) {
                i++
                continue
            }
            val className = match.groupValues[1]
            var depth = 0
            var line = i
            var col = match.range.last
            var closed = false
            while (line < code.size && !closed) {
                val text = code[line]
                val segment = StringBuilder()
                var c = col
                while (c < text.length) {
                    val ch = text[c]
                    if (ch == '(') depth++
                    if (ch == ')') {
                        depth--
                        if (depth == 0) {
                            closed = true
                            break
                        }
                    }
                    if (depth >= 1) segment.append(ch)
                    c++
                }
                primitiveWithDefault.findAll(segment).forEach { m ->
                    out += Finding('a', file, line + 1, "$className.${m.groupValues[1]}: ${m.groupValues[2]}")
                }
                line++
                col = 0
            }
            i = line
        }
    }

    // Rule (b). The line above only counts when it is annotation-only: `@SerialName("name") val
    // name` directly above `override var id` pins `name`, not `id`.
    private fun scanOverrideIds(
        file: String,
        code: List<String>,
        out: MutableList<Finding>,
    ) {
        code.forEachIndexed { idx, text ->
            if (!overrideVarId.containsMatchIn(text) || text.contains("@SerialName(")) return@forEachIndexed
            val prev = (idx - 1 downTo 0).firstOrNull { code[it].isNotBlank() }?.let { code[it] }
            if (prev != null && prev.contains("@SerialName(") && !propertyDecl.containsMatchIn(prev)) return@forEachIndexed
            val owner = (idx downTo 0).firstNotNullOfOrNull { anyClassDecl.find(code[it])?.groupValues?.get(1) } ?: "?"
            out += Finding('b', file, idx + 1, "$owner.id")
        }
    }

    // Rule (c): a column-0 declaration counts as @Serializable when the annotation sits on its
    // own line or on one of the 3 lines above it — walking back only through blank/annotation
    // lines, so a short serializable class never lends its annotation to the next declaration.
    private fun collectTopLevelSerializable(
        file: String,
        code: List<String>,
        into: MutableMap<String, MutableList<Pair<String, Int>>>,
    ) {
        code.forEachIndexed { idx, text ->
            val match = topLevelDecl.find(text) ?: return@forEachIndexed
            var annotated = serializable.containsMatchIn(text)
            var k = 1
            while (!annotated && k <= 3 && idx - k >= 0) {
                val above = code[idx - k].trim()
                if (above.isNotEmpty() && !above.startsWith("@")) break
                annotated = serializable.containsMatchIn(above)
                k++
            }
            if (annotated) into.getOrPut(match.groupValues[1]) { mutableListOf() } += file to (idx + 1)
        }
    }

    // Blanks comments and string/char literal contents (same line length) so the regexes
    // above never match inside KDoc, `//` comments or strings, and parens in them do not
    // skew the constructor depth count. Block comments and raw strings span lines.
    private fun codeOnly(lines: List<String>): List<String> {
        var inBlock = false
        var inRaw = false
        return lines.map { line ->
            val sb = StringBuilder(line.length)
            var inStr = false
            var inChar = false
            var i = 0
            while (i < line.length) {
                val ch = line[i]
                val next = if (i + 1 < line.length) line[i + 1] else ' '
                var step = 1
                when {
                    inBlock -> if (ch == '*' && next == '/') {
                        inBlock = false
                        sb.append("  ")
                        step = 2
                    } else {
                        sb.append(' ')
                    }
                    inRaw -> if (line.startsWith("\"\"\"", i)) {
                        inRaw = false
                        sb.append("\"\"\"")
                        step = 3
                    } else {
                        sb.append(' ')
                    }
                    inStr || inChar -> when {
                        ch == '\\' -> {
                            sb.append("  ")
                            step = 2
                        }
                        (inStr && ch == '"') || (inChar && ch == '\'') -> {
                            inStr = false
                            inChar = false
                            sb.append(ch)
                        }
                        else -> sb.append(' ')
                    }
                    ch == '/' && next == '/' -> {
                        repeat(line.length - i) { sb.append(' ') }
                        step = line.length - i
                    }
                    ch == '/' && next == '*' -> {
                        inBlock = true
                        sb.append("  ")
                        step = 2
                    }
                    line.startsWith("\"\"\"", i) -> {
                        inRaw = true
                        sb.append("\"\"\"")
                        step = 3
                    }
                    ch == '"' -> {
                        inStr = true
                        sb.append(ch)
                    }
                    ch == '\'' -> {
                        inChar = true
                        sb.append(ch)
                    }
                    else -> sb.append(ch)
                }
                i += step
            }
            sb.toString()
        }
    }
}

val wireContractFixtures = layout.projectDirectory.dir("gradle/wire-contract-fixtures")

val wireContractSelfTest =
    tasks.register<WireContractCheck>("wireContractSelfTest") {
        group = "verification"
        description = "Regression test for the wireContractCheck scanner: it must reproduce exactly the fixture findings."
        sources.from(fileTree(wireContractFixtures) { include("*.kt.txt") })
        rootDirectory.set(wireContractFixtures)
        strict.set(true)
        selfTest.set(true)
        // Hand-derived from the fixtures' trailing comments — change both together.
        expectedFindings.set(
            listOf(
                "a update-requests.kt.txt:8 UpdateThingRequest.isActive: Boolean",
                "a update-requests.kt.txt:9 UpdateThingRequest.count: Int",
                "a update-requests.kt.txt:14 UpdateThingRequest.expiresAt: Long",
                "a update-requests.kt.txt:15 UpdateThingRequest.mode: Char",
                "a update-requests.kt.txt:20 UpdateOneLineRequest.flag: Boolean",
                "a update-requests.kt.txt:20 UpdateOneLineRequest.n: Short",
                "b ids.kt.txt:19 Bare.id",
                "b ids.kt.txt:25 PinnedOnlyInComment.id",
                "c dup-one.kt.txt:5 SharedName",
                "c dup-two.kt.txt:4 SharedName",
                "c dup-one.kt.txt:9 AnnotatedTwice",
                "c dup-two.kt.txt:8 AnnotatedTwice",
            ),
        )
        reportFile.set(layout.buildDirectory.file("reports/wire-contract/self-test.txt"))
    }

tasks.register<WireContractCheck>("wireContractCheck") {
    group = "verification"
    description =
        "Fails on Update*Request non-null primitive defaults, bare `override var id`, and duplicate " +
        "top-level @Serializable names in commonMain. -PwireContract.strict=false reports without failing."
    dependsOn(wireContractSelfTest)
    sources.from(
        fileTree(rootDir) {
            include("*/src/commonMain/kotlin/**/*.kt")
            exclude("**/build/**")
        },
    )
    rootDirectory.set(layout.projectDirectory)
    // Only an explicit `false` turns the gate off — a typo must not silently disable it.
    strict.set(
        providers.gradleProperty("wireContract.strict").map { !it.trim().equals("false", ignoreCase = true) }.orElse(true),
    )
    reportFile.set(layout.buildDirectory.file("reports/wire-contract/report.txt"))
    // Report-only runs always re-print; a strict run is up to date once it has passed.
    outputs.upToDateWhen { strict.get() }
}

subprojects {
    tasks.matching { it.name == "check" }.configureEach {
        dependsOn(rootProject.tasks.named("wireContractCheck"))
    }
}
