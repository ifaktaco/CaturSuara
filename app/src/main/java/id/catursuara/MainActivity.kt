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
import java.util.Locale

/** Prototype: two-human chess with spoken coordinates. No Stockfish binary is bundled. */
class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private var board = Board()
    private lateinit var status: TextView
    private lateinit var squares: Array<Button>
    private var from: Square? = null
    private var tts: TextToSpeech? = null
    private val speechRequest = 73
    private val files = "abcdefgh"
    private val symbols = mapOf(
        "WHITE_KING" to "♔", "WHITE_QUEEN" to "♕", "WHITE_ROOK" to "♖", "WHITE_BISHOP" to "♗", "WHITE_KNIGHT" to "♘", "WHITE_PAWN" to "♙",
        "BLACK_KING" to "♚", "BLACK_QUEEN" to "♛", "BLACK_ROOK" to "♜", "BLACK_BISHOP" to "♝", "BLACK_KNIGHT" to "♞", "BLACK_PAWN" to "♟"
    )
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(12, 12, 12, 12); setBackgroundColor(Color.rgb(245,245,245)) }
        status = TextView(this).apply { textSize = 20f; gravity = Gravity.CENTER; setTextColor(Color.BLACK }
        root.addView(status, LinearLayout.LayoutParams(-1, 65))
        val grid = GridLayout(this).apply { rowCount = 8; columnCount = 8 }
        squares = Array(64) { index ->
            val rank = 8 - index / 8
            val file = index % 8
            Button(this).apply {
                textSize = 29f; minHeight = 0; minimumHeight = 0; minWidth = 0; minimumWidth = 0; setPadding(0,0,0,0)
                setOnClickListener { choose(Square.valueOf("${files[file].uppercaseChar()}$rank")) }
                grid.addView(this, GridLayout.LayoutParams(GridLayout.spec(index / 8), GridLayout.spec(file, 1f)).apply { width = 0; height = 0 })
            }
        }
        root.addView(grid, LinearLayout.LayoutParams(-1, 0, 1f))
        val voice = Button(this).apply { text = "🎤 UCAPKAN LANGKAH"; setOnClickListener { listen() } }
        root.addView(voice)
        root.addView(Button(this).apply { text = "PERMAINAN BARU"; setOnClickListener { board = Board(); from = null; render(); speak("Permainan baru") } })
        root.addView(TextView(this).apply { text = "Contoh suara: E dua ke E empat. Mode dua pemain. Pengenalan suara bergantung pada layanan Android yang tersedia."; textSize = 13f })
        setContentView(root)
        render()
    }
    private fun choose(square: Square) {
        if (from == null) { from = square; render(); return }
        val src = from!!; from = null
        val moves = board.legalMoves().filter { it.from == src && it.to == square }
        if (moves.isEmpty()) { speak("Langkah tidak sah"); render(); return }
        val move = moves.firstOrNull { it.promotion.toString().endsWith("QUEEN") } ?: moves.first()
        board.doMove(move)
        val msg = "${src.toString().lowercase()} ke ${square.toString().lowercase()}"
        speak(if (board.isMated) "$msg. Skakmat" else if (board.isDraw) "$msg. Remis" else if (board.isKingAttacked) "$msg. Skak" else msg)
        render()
    }
    private fun render() {
        status.text = when { board.isMated -> "SKAKMAT"; board.isDraw -> "REMIS"; else -> "Giliran ${if (board.sideToMove.toString() == "WHITE") "PUTIH" else "HITAM"}${if (board.isKingAttacked) " — SKAK" else ""}" }
        for (i in squares.indices) {
            val row = i / 8; val col = i % 8
            val sq = Square.valueOf("${files[col].uppercaseChar()}${8-row}")
            val piece = board.getPiece(sq).toString()
            squares[i].text = symbols[piece] ?: ""
            squares[i].setBackgroundColor(if (sq == from) Color.YELLOW else if ((row+col)%2 == 0) Color.rgb(240,217,181) else Color.rgb(181,136,99))
        }
    }
    private fun listen() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 2); return }
        try {
            startActivityForResult(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Ucapkan: E dua ke E empat")
            }, speechRequest)
        } catch (_: Exception) { Toast.makeText(this, "Layanan pengenal suara belum tersedia", Toast.LENGTH_LONG).show() }
    }
    @Deprecated("Activity result API used for minimum-dependency prototype")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != speechRequest || resultCode != RESULT_OK) return
        val heard = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() ?: return
        val normalized = heard.lowercase(Locale("id", "ID")).replace("satu", "1").replace("dua", "2").replace("tiga", "3").replace("empat", "4").replace("lima", "5").replace("enam", "6").replace("tujuh", "7").replace("delapan", "8")
        val coords = Regex("[a-h]\\s*[1-8]").findAll(normalized).map { it.value.replace(" ", "").uppercase() }.toList()
        if (coords.size < 2) { speak("Langkah tidak dikenali: $heard"); return }
        try { from = Square.valueOf(coords[0]); choose(Square.valueOf(coords[1])) } catch (_: Exception) { speak("Koordinat tidak dikenali") }
    }
    private fun speak(text: String) { tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "chess") }
    override fun onInit(status: Int) { if (status == TextToSpeech.SUCCESS) tts?.language = Locale("id", "ID") }
    override fun onDestroy() { tts?.shutdown(); super.onDestroy() }
}
