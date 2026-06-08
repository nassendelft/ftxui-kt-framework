package nl.ncaj.ftxui.framework

object Spinners {

    // ==========================================
    // 1. CLASSICS & BASIC ASCII (Max Compatibility)
    // ==========================================
    val pipeClassic = listOf("|", "/", "-", "\\")
    val pipeSimple = listOf("-", "\\", "|", "/")
    val plusMinus = listOf("+", "-", "±")
    val mathSymbols = listOf("÷", "×", "+", "-")
    val binaryBlink = listOf("0", "1")
    val hashSpin = listOf("#", "♯", "⌗")
    val cleanDotChase = listOf(".  ", ".. ", "...", "   ")
    val bouncyVee = listOf("v", "<", "^", ">")
    val asciiFlasher = listOf("X", " ")
    val inequalityShift = listOf("<", "=", ">", "=")
    val cornerSlash = listOf("⌞", "⌜", "⌝", "⌟")

    // ==========================================
    // 2. ADVANCED BRAILLE CONFIGURATIONS
    // ==========================================
    val brailleWheel = listOf("⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏")
    val brailleArc = listOf("⠋", "⠙", "⠚", "⠞", "⠖", "⠦", "⠴", "⠲", "⠖", "⠓")
    val brailleWave = listOf("⠄", "⠆", "⠔", "⠢", "⠔", "⠆")
    val brailleBounce = listOf("⠁", "⠂", "⠄", "⡀", "⠄", "⠂")
    val dotOrbit = listOf("⠁", "⠂", "⠄", "⡀", "⢀", "⠠", "⠐", "⠈")
    val brailleDoubleChaser = listOf("⠋", "⠓", "⠚", "⠞", "⠖", "⠦", "⠴", "⠲", "⠳", "⠟")
    val brailleDna = listOf("⠋", "⠉", "⠙", "⠚", "⠒", "⠖", "⠦", "⠤", "⠦", "⠖", "⠒", "⠚", "⠙", "⠉")
    val brailleXPattern = listOf("⠎", "⠦", "⠴", "⠱", "⠎")
    val brailleInnerOrbit = listOf("⠔", "⠢", "⠔", "⠦", "⠴", "⠦")
    val brailleExpandingRing = listOf("⠠", "⠤", "⠦", "⠴", "⠤", "⠠")

    // ==========================================
    // 3. BLOCKS, BARS & SEGMENTS
    // ==========================================
    val quadrantClockwise = listOf("▖", "▘", "▝", "▗")
    val quadrantCounter = listOf("▖", "▗", "▝", "▘")
    val quadrantChase = listOf("▗", "▖", "▘", "▝")
    val verticalGrow = listOf(" ", "▃", "▄", "▅", "▆", "▇", "█", "▇", "▆", "▅", "▄", "▃")
    val horizontalGrow = listOf("▏", "▎", "▍", "▌", "▋", "▊", "▉", "▊", "▋", "▌", "▍", "▎")
    val blockFlash = listOf("▰", "▱")
    val blockStep = listOf("◢", "◣", "◤", "◥")
    val blockRotate = listOf("◰", "◳", "◲", "◱")
    val horizontalFill = listOf(" ", "▏", "▎", "▍", "▌", "▋", "▊", "▉", "█", "▉", "▊", "▋", "▌", "▍", "▎", "▏")
    val boxCorners = listOf("▤", "▥", "▨", "▧")
    val thickLines = listOf("━", "┃")
    val blockBlink = listOf("█", " ")
    val diagonalBlocks = listOf("▚", "▞")
    val layeredBlocks = listOf("░", "▒", "▓", "█", "▓", "▒")

    // ==========================================
    // 4. CIRCLES & ACCENTS
    // ==========================================
    val circleHalves = listOf("◐", "◓", "◑", "◒")
    val circleQuarters = listOf("◴", "◷", "◶", "◵")
    val circleBlink = listOf("⊙", "☉", "◎")
    val circleArc = listOf("⌒", "f", "⌢", "⏊")
    val rings = listOf("◦", "◯", "◦", " ")
    val radarPulse = listOf("•", "◦", "◎", "◯", "◎", "◦")
    val diamondPulse = listOf("◇", "◆", "◈", "◆")
    val burstingStar = listOf("․", "﹒", "◦", "○", "°", " ")

    // ==========================================
    // 5. GEOMETRIC DIRECTIVES
    // ==========================================
    val arrowsClockwise = listOf("↑", "↗", "→", "↘", "↓", "↙", "←", "↖")
    val arrowsCardinal = listOf("↑", "→", "↓", "←")
    val triangleBounce = listOf("▲", "►", "▼", "◄")
    val openTriangleSpin = listOf("ᐃ", "ᐄ", "ᐃ", "ᐁ")
    val cornerBracket = listOf("┌", "┐", "┘", "└")
    val crossHair = listOf("┼", "╫", "╪")
    val starTwinkle = listOf("+", "*", "×", "✶", "●")
    val pipeJoints = listOf("┤", "┘", "┴", "└", "├", "┌", "┬", "┐")
    val inlineDashSweep = listOf("╼", "╽", "╾", "╿")
    val lineOscillator = listOf("⎺", "⎻", "⎼", "⎽", "⎼", "⎻")

    // ==========================================
    // 6. TEXT / ALPHABET SEQS (Creative Minimal)
    // ==========================================
    val abcChaser = listOf("A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z")
    val loadingTextWord = listOf("L", "o", "a", "d", "i", "n", "g")
    val countingHex = listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "A", "B", "C", "D", "E", "F")
    val subScriptSpin = listOf("₊", "⠕", "⁺", "⠪")

    // ==========================================
    // 7. EMOJI & SPECIAL ENCODINGS (Verify Font)
    // ==========================================
    val hourglassEmoji = listOf("⌛", "⏳")
    val clockEmoji = listOf("🕐", "🕑", "🕒", "🕓", "🕔", "🕕", "🕖", "🕗", "🕘", "🕙", "🕚", "🕛")
    val moonPhases = listOf("🌑", "🌒", "🌓", "🌔", "🌕", "🌖", "🌗", "🌘")
    val diceRoll = listOf("⚀", "⚁", "⚂", "⚃", "⚄", "⚅")
    val heartBeat = listOf("🖤", "❤️", "💖", "❤️")
    val earthSpin = listOf("🌍", "🌎", "🌏")
    val pacmanRetro = listOf("⢗", "⢺", "⢝", "⢼")
    val happyFaceWink = listOf("😊", "😉")
}