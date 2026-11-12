package id.catursuara

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.widget.*
import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Square
import com.github.bhlangonijr.chesslib.move.Move
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

/** CATUR SUARA V2 — pemandu papan fisik dengan Stockfish offline. */
class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private var board = Board()
    private var myColor = "WHITE"
    private var level = 1
    private var gameId = 0
    private var busy = false
    private var selected: Square? = null
    private var suggestion: Move? = null
    private var lastMove: Move? = null
    private var tts: TextToSpeech? = null
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var status: TextView
    private lateinit var guide: TextView
    private lateinit var grid: GridLayout
    private lateinit var squares: Array<TextView>
    private lateinit var confirm: Button
    private val letters = "abcdefgh"
    private val pieceGlyph = mapOf(
        "WHITE_KING" to "♔", "WHITE_QUEEN" to "♕", "WHITE_ROOK" to "♖",
        "WHITE_BISHOP" to "♗", "WHITE_KNIGHT" to "♘", "WHITE_PAWN" to "♙",
        "BLACK_KING" to "♚", "BLACK_QUEEN" to "♛", "BLACK_ROOK" to "♜",
        "BLACK_BISHOP" to "♝", "BLACK_KNIGHT" to "♞", "BLACK_PAWN" to "♟"
    )
    private fun dp(n: Int) = (n * resources.displayMetrics.density + .5f).toInt()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        val root = ScrollView(this).apply { setBackgroundColor(Color.rgb(247,247,244)) }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(12), dp(10), dp(18))
        }
        root.addView(content)
        status = TextView(this).apply { textSize = 19f; gravity = Gravity.CENTER; setTextColor(Color.BLACK) }
        content.addView(status)
        val side = Spinner(this)
        side.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            listOf("Saya bermain PUTIH", "Saya bermain HITAM"))
        content.addView(side)
        side.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val chosen = if (position == 0) "WHITE" else "BLACK"
                if (chosen != myColor) { myColor = chosen; newGame() }
            }
        }
        val difficulty = Spinner(this)
        difficulty.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            listOf("Level 1 — Pemula", "Level 2 — Menengah", "Level 3 — Mahir", "Level 4 — Master"))
        difficulty.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                level = position + 1
            }
        }
        content.addView(difficulty)
        val screen = resources.displayMetrics.widthPixels - dp(20)
        val cell = (screen - dp(22)) / 8
        val boardWidth = cell * 8
        val boardContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val boardRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val ranks = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for (r in 0..7) {
            ranks.addView(TextView(this).apply {
                text = if (myColor == "WHITE") "${8-r}" else "${r+1}"
                gravity = Gravity.CENTER
                textSize = 12f
                setTextColor(Color.DKGRAY)
            }, LinearLayout.LayoutParams(dp(22), cell))
        }
        boardRow.addView(ranks)
        grid = GridLayout(this).apply { rowCount = 8; columnCount = 8 }
        squares = Array(64) { i ->
            TextView(this).apply {
                gravity = Gravity.CENTER
                textSize = 31f
                includeFontPadding = false
                setOnClickListener { onSquare(displaySquare(i)) }
                grid.addView(this, GridLayout.LayoutParams(GridLayout.spec(i/8), GridLayout.spec(i%8)).apply {
                    width = cell; height = cell
                })
            }
        }
        boardRow.addView(grid, LinearLayout.LayoutParams(boardWidth, boardWidth))
        boardContainer.addView(boardRow)
        val files = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        files.addView(TextView(this), LinearLayout.LayoutParams(dp(22), dp(20)))
        for (c in 0..7) files.addView(TextView(this).apply {
            text = "${if (myColor == "WHITE") letters[c] else letters[7-c]}".uppercase()
            gravity = Gravity.CENTER; textSize = 13f; setTextColor(Color.DKGRAY)
        }, LinearLayout.LayoutParams(cell, dp(20)))
        boardContainer.addView(files)
        content.addView(boardContainer)
        // Recreate coordinate labels when side changes.
        side.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val chosen = if (position == 0) "WHITE" else "BLACK"
                if (chosen != myColor) { myColor = chosen; newGame() }
                for (r in 0..7) (ranks.getChildAt(r) as TextView).text = "${if (myColor == "WHITE") 8-r else r+1}"
                for (c in 0..7) (files.getChildAt(c+1) as TextView).text =
                    "${if (myColor == "WHITE") letters[c] else letters[7-c]}".uppercase()
                render()
            }
        }
        guide = TextView(this).apply {
            textSize = 17f; setTextColor(Color.rgb(20,70,45))
            setPadding(dp(4),dp(12),dp(4),dp(12))
        }
        content.addView(guide)
        content.addView(Button(this).apply {
            text = "🎤 UCAPKAN LANGKAH LAWAN / SAYA"
            setOnClickListener { listen() }
        })
        confirm = Button(this).apply {
            text = "✓ SUDAH SAYA JALANKAN LANGKAH SARAN"
            setOnClickListener { confirmSuggestion() }
        }
        content.addView(confirm)
        content.addView(Button(this).apply {
            text = "🔊 ULANGI PETUNJUK"
            setOnClickListener { speak(guide.text.toString()) }
        })
        content.addView(Button(this).apply { text = "PERMAINAN BARU"; setOnClickListener { newGame() } })
        content.addView(TextView(this).apply {
            text = "Papan fisik: sebutkan langkah lawan (contoh: E7 ke E5). " +
                "Saat giliran Anda, Stockfish menyarankan langkah; pindahkan bidak di papan fisik lalu tekan konfirmasi. " +
                "Anda juga bisa mengucapkan langkah sendiri. Koordinat mengikuti warna pilihan."
            textSize = 13f; setTextColor(Color.DKGRAY)
        })
        setContentView(root)
        newGame()
    }
    private fun displaySquare(i: Int): Square {
        val r = i / 8; val c = i % 8
        val file = if (myColor == "WHITE") letters[c] else letters[7-c]
        val rank = if (myColor == "WHITE") 8-r else r+1
        return Square.valueOf("${file.uppercaseChar()}$rank")
    }
    private fun newGame() {
        gameId++; board = Board(); busy = false; suggestion = null
        lastMove = null; selected = null
        guide.text = if (myColor == "WHITE")
            "Anda PUTIH. Jalankan langkah pertama atau tunggu rekomendasi Stockfish."
        else "Anda HITAM. Minta lawan putih melangkah, lalu ucapkan langkahnya."
        render()
        if (myColor == "WHITE") recommend()
        else speak("Anda bermain hitam. Sebutkan langkah pertama lawan putih.")
    }
    private fun onSquare(square: Square) {
        if (busy || board.isMated || board.isDraw) return
        if (selected == null) {
            if (board.getPiece(square).toString().startsWith(board.sideToMove.toString())) {
                selected = square; render()
            }
            return
        }
        val from = selected!!
        selected = null
        val choices = board.legalMoves().filter { it.from == from && it.to == square }
        if (choices.isEmpty()) { render(); speak("Langkah tidak sah"); return }
        submit(choices.firstOrNull { it.promotion.toString().endsWith("QUEEN") } ?: choices.first())
    }
    private fun submit(move: Move) {
        if (busy || board.isMated || board.isDraw) return
        if (!board.legalMoves().contains(move)) { speak("Langkah tidak sah"); return }
        val wasMine = board.sideToMove.toString() == myColor
        board.doMove(move)
        lastMove = move; suggestion = null; selected = null
        val msg = if (wasMine) "Langkah Anda" else "Langkah lawan"
        guide.text = "$msg: ${sayMove(move)}.${outcome()}"
        render()
        if (board.isMated || board.isDraw) { speak(guide.text.toString()); return }
        if (board.sideToMove.toString() == myColor) recommend()
        else speak("$msg diterima. ${sayMove(move)}. Sekarang giliran lawan.")
    }
    private fun outcome(): String = when {
        board.isMated -> " Skakmat."
        board.isDraw -> " Remis."
        board.isKingAttacked -> " Skak."
        else -> ""
    }
    private fun saySquare(s: Square): String {
        val v = s.toString().lowercase()
        return "${v[0].uppercaseChar()} ${v[1]}"
    }
    private fun sayMove(m: Move): String {
        val name = when ((if (board.getPiece(m.from).toString() == "NONE") board.getPiece(m.to) else board.getPiece(m.from)).toString().substringAfter("_", "")) {
            "PAWN" -> "pion"; "KNIGHT" -> "kuda"; "BISHOP" -> "gajah"
            "ROOK" -> "benteng"; "QUEEN" -> "menteri"; "KING" -> "raja"
            else -> "bidak"
        }
        return "Gerakkan $name dari ${saySquare(m.from)} ke ${saySquare(m.to)}"
    }
    private fun recommend() {
        if (board.sideToMove.toString() != myColor || board.isMated || board.isDraw) return
        busy = true; render()
        val fen = board.fen; val token = gameId; val strength = level
        guide.text = "Stockfish sedang menghitung langkah terbaik..."
        worker.execute {
            var engineError: String? = null
            val answer = try { stockfish(fen, strength) } catch (e: Exception) {
                engineError = "${e.javaClass.simpleName}: ${e.message ?: "tanpa keterangan"}"
                null
            }
            runOnUiThread {
                if (token != gameId || isFinishing || isDestroyed) return@runOnUiThread
                busy = false
                val move = board.legalMoves().firstOrNull {
                    (it.from.toString()+it.to.toString()).lowercase() == answer?.take(4) &&
                    (answer.length < 5 || promotionLetter(it) == answer.getOrNull(4)?.toString())
                }
                if (move == null) {
                    guide.text = "Stockfish gagal: ${engineError ?: "Mesin tidak mengirim langkah legal (periksa NNUE/UCI)."}"
                    render(); speak(guide.text.toString()); return@runOnUiThread
                }
                suggestion = move
                guide.text = "SARAN STOCKFISH: ${sayMove(move)}. " +
                    "Jalankan di papan fisik, lalu tekan tombol konfirmasi atau ucapkan langkah Anda."
                render(); speak(guide.text.toString())
            }
        }
    }
    private fun promotionLetter(m: Move) = when {
        m.promotion.toString().endsWith("QUEEN") -> "q"
        m.promotion.toString().endsWith("ROOK") -> "r"
        m.promotion.toString().endsWith("BISHOP") -> "b"
        m.promotion.toString().endsWith("KNIGHT") -> "n"
        else -> ""
    }
    private fun confirmSuggestion() {
        val move = suggestion
        if (move == null) { speak("Belum ada saran yang bisa dikonfirmasi"); return }
        submit(move)
    }
    private fun stockfish(fen: String, strength: Int): String? {
        val binary = File(applicationInfo.nativeLibraryDir, "libstockfish.so")
        if (!binary.exists()) throw IllegalStateException("libstockfish.so tidak ditemukan di ${applicationInfo.nativeLibraryDir}")
        if (!binary.canExecute()) throw IllegalStateException("libstockfish.so ada, tetapi tidak dapat dieksekusi")
        val process = ProcessBuilder(binary.absolutePath).redirectErrorStream(true).start()
        try {
            val writer = process.outputStream.bufferedWriter()
            val reader = process.inputStream.bufferedReader()
            fun send(cmd: String) { writer.write(cmd); writer.newLine(); writer.flush() }
            send("uci")
            while (true) { val line = reader.readLine() ?: throw IllegalStateException("Stockfish berhenti sebelum uciok"); if (line == "uciok") break }
            send("setoption name Threads value 1")
            send("setoption name Hash value 16")
            send("setoption name UCI_LimitStrength value true")
            send("setoption name UCI_Elo value ${listOf(1320,1600,1900,2400)[strength-1]}")
            send("isready")
            while (true) { val line = reader.readLine() ?: throw IllegalStateException("Stockfish berhenti sebelum readyok"); if (line == "readyok") break }
            send("position fen $fen")
            send("go movetime ${listOf(350,650,1000,1500)[strength-1]}")
            while (true) {
                val line = reader.readLine() ?: throw IllegalStateException("Stockfish berhenti sebelum bestmove")
                if (line.startsWith("bestmove ")) return line.split(" ").getOrNull(1)?.lowercase()
            }
        } finally { process.destroy() }
    }
    private fun render() {
        status.text = when {
            board.isMated -> "SKAKMAT"
            board.isDraw -> "REMIS"
            busy -> "Stockfish sedang berpikir..."
            board.sideToMove.toString() == myColor -> "Giliran ANDA (${if (myColor=="WHITE") "PUTIH" else "HITAM"})"
            else -> "Giliran LAWAN (${if (myColor=="WHITE") "HITAM" else "PUTIH"})"
        }
        if (::confirm.isInitialized) confirm.isEnabled = !busy && suggestion != null
        if (!::squares.isInitialized) return
        for (i in squares.indices) {
            val sq = displaySquare(i)
            val p = board.getPiece(sq).toString()
            val row = i/8; val col = i%8
            squares[i].text = pieceGlyph[p] ?: ""
            squares[i].setTextColor(if (p.startsWith("WHITE")) Color.WHITE else Color.BLACK)
            squares[i].setShadowLayer(2f,1f,1f,if (p.startsWith("WHITE")) Color.BLACK else Color.WHITE)
            squares[i].setBackgroundColor(when {
                sq == selected -> Color.rgb(255,211,80)
                suggestion?.from == sq -> Color.rgb(250,204,75)
                suggestion?.to == sq -> Color.rgb(107,194,114)
                lastMove?.from == sq || lastMove?.to == sq -> Color.rgb(210,217,115)
                (row+col)%2 == 0 -> Color.rgb(240,217,181)
                else -> Color.rgb(181,136,99)
            })
        }
    }
    private fun listen() {
        if (busy) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 2); return
        }
        try {
            startActivityForResult(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Contoh: E tujuh ke E lima")
            }, 73)
        } catch (_: Exception) { speak("Pengenal suara tidak tersedia") }
    }
    @Deprecated("Legacy speech result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 73 || resultCode != RESULT_OK) return
        val heard = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() ?: return
        val normalized = heard.lowercase(Locale("id","ID"))
            .replace(Regex("\\b(satu|dua|tiga|empat|lima|enam|tujuh|delapan)\\b")) {
                mapOf("satu" to "1","dua" to "2","tiga" to "3","empat" to "4",
                    "lima" to "5","enam" to "6","tujuh" to "7","delapan" to "8")[it.value] ?: it.value
            }
            .replace(Regex("\\b(ef|eff)\\b"), "f")
            .replace(Regex("\\b(be)\\b"), "b")
            .replace(Regex("\\b(ce)\\b"), "c")
        val coords = Regex("(?<![a-z])[a-h]\\s*[1-8](?![0-9])").findAll(normalized)
            .map { it.value.replace(" ","").uppercase() }.toList()
        if (coords.size < 2) { speak("Langkah tidak dikenali. Ucapkan misalnya E dua ke E empat."); return }
        val from = try { Square.valueOf(coords[0]) } catch (_: Exception) { null }
        val to = try { Square.valueOf(coords[1]) } catch (_: Exception) { null }
        val move = board.legalMoves().filter { it.from == from && it.to == to }
            .let { choices -> choices.firstOrNull { it.promotion.toString().endsWith("QUEEN") } ?: choices.firstOrNull() }
        if (move == null) speak("Langkah $heard tidak sah untuk posisi sekarang")
        else submit(move)
    }
    private fun speak(message: String) {
        tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "catursuara")
    }
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) tts?.language = Locale("id","ID")
    }
    override fun onDestroy() {
        gameId++; worker.shutdownNow(); tts?.shutdown(); super.onDestroy()
    }
}
