import java.awt.Color
import java.awt.Font
import java.awt.Rectangle
import java.awt.Robot
import java.awt.event.InputEvent
import java.awt.image.BufferedImage
import java.io.File
import java.util.*
import javax.imageio.ImageIO

val colorUncovered = 0x50371b
val colorList = arrayOf(
        0x37AABD,
        0x94BE08,
        0x59c339,
        0xB557BD,
        0xC92410,
        0x18AC39,
        0x8C59B5
)
fun main(args : Array<String>) {
    Thread.sleep(1000)
    val field = FieldOperator(rect = Rectangle(291, 187, 1627 - 291, 896 - 187))
    AutoMiner(field).work()
}

class FieldOperator(
        private val w : Int = 30,
        private val h : Int = 16,
        private val rect : Rectangle
) {
    private val r = Robot()
    private val recognizer = TextRecognizer()
    private val fieldArray : Array<IntArray> = Array(w, {
        val arr = kotlin.IntArray(h)
        Arrays.fill(arr, -1)
        arr
    })
    /*private val updateArray : Array<BooleanArray> = Array(w, {kotlin.BooleanArray(h) })*/
    private val updateQueue = ArrayDeque<Pair<Int, Int>>()
    var isOver = false
        private set

    operator fun get(w : Int) = fieldArray[w]
    fun open(x : Int, y : Int) {
        if(isOver) return
        val cellW = rect.width / w
        val cellH = rect.height / h
        r.mouseMove(rect.x + x * rect.width / w + cellW / 2, rect.y + y * rect.height / h + cellH / 2)
        r.delay(10)
        r.mousePress(InputEvent.BUTTON1_DOWN_MASK)
        r.delay(20)
        r.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
        r.delay(10)

        updateQueue.addLast(Pair(x, y))
    }


    fun update() {
        if(isOver) return

        val img = r.createScreenCapture(rect)
        if(img.getRGB(573 - rect.x, 778 - rect.y).and(0xFFFFFF) == 0) {
            isOver = true
            return
        }
        while (updateQueue.isNotEmpty()) {
            val (x,y) = updateQueue.pollFirst()
            if(fieldArray[x][y] >= 0) {
                continue
            }
            val newValue = recognizeOne(img, x, y)
            if(newValue != fieldArray[x][y]) {
                fieldArray[x][y] = newValue
                for(dx in -1..1) {
                    for(dy in -1..1) {
                        if(dx == 0 && dy == 0) continue
                        val nx = x + dx
                        val ny = y + dy
                        if(nx in 0..(w - 1) && ny in 0..(h - 1) && fieldArray[nx][ny] < 0) {
                            updateQueue.addLast(Pair(nx, ny))
                        }
                    }
                }
            }
        }
    }

    private fun recognizeOne(img: BufferedImage, x: Int, y: Int) : Int{
        val cellW = rect.width / w
        val cellH = rect.height / h
        val subImage = img.getSubimage(
                x * rect.width / w, y * rect.height / h, cellW, cellH)
        return if (subImage.find(colorUncovered)) {
            subImage.extract(colorList)
            val cut = subImage.cut(0x0)
            if (cut == null) {
                0
            } else {
                recognizer.recognize(cut.resize(16, 16))
            }
        } else {
            -1
        }
    }


    @Suppress("unused")
    fun print() {
        for(i in 0..h - 1) {
            for(j in 0..w - 1) {
                print(when (this[j][i]){
                    0 -> " + "
                    -1 -> "   "
                    else -> String.format("%2d ", this[j][i])
                })
            }
            println()
        }
    }
}

fun Int.colorDist(another : Int) = maxOf(
        Math.abs(another.shr(16).and(0xFF) - shr(16).and(0xFF)),
        Math.abs(another.shr(8).and(0xFF) - shr(8).and(0xFF)),
        Math.abs(another.and(0xFF) - and(0xFF))
    )

@Suppress("unused")
fun BufferedImage.save(path : String) = ImageIO.write(this, "png", File(path))
fun BufferedImage.find(color : Int, threshold: Int = 20) : Boolean {
    return (0..width - 1).any { i ->
        (0..height - 1)
                .find {
                    getRGB(i, it).colorDist(color) < threshold
                } != null
    }
}
fun BufferedImage.extract(color : Array<Int>, dest : Int = 0xFFFFFF, threshold: Int = 20) {
    (0..width - 1).forEach { i ->
        (0..height - 1)
                .forEach {
                    val c = getRGB(i, it)
                    setRGB(i, it, if(color.find { it.colorDist(c) < threshold} == null) {
                        dest
                    } else {
                        0x0
                    })
                }
        }
}

fun BufferedImage.cut(color : Int) : BufferedImage? {
    var minX = Int.MAX_VALUE
    var maxX = Int.MIN_VALUE
    var minY = Int.MAX_VALUE
    var maxY = Int.MIN_VALUE
    (0..width - 1).forEach { i ->
        (0..height - 1)
                .forEach {
                    if(color == getRGB(i, it).and(0xFFFFFF)) {
                        minX = minOf(i, minX)
                        maxX = maxOf(i, maxX)
                        minY = minOf(it, minY)
                        maxY = maxOf(it, maxY)
                    }
                }
    }
    if(minX == Int.MAX_VALUE) return null
    return getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1)
}
fun BufferedImage.resize(w : Int, h : Int) : BufferedImage {
    val bi = BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR)
    bi.graphics.drawImage(this, 0, 0, w, h, null)
    return bi
}


class TextRecognizer {
    val textArray = Array(7, {
        val img = BufferedImage(16, 16, BufferedImage.TYPE_3BYTE_BGR)
        val g = img.graphics
        g.font = Font("Arial",Font.BOLD, 14)
        g.color = Color.white
        g.fillRect(0, 0, 16, 16)
        g.color = Color.BLACK
        g.drawString("${it + 1}", 2, 12)
        //val ret = (img.cut(0x0)?: img).resize(16, 16)
        img.cut(0x0)!!.resize(16,16)
    })
    fun recognize(img : BufferedImage) : Int {
        var max = 12
        var maxIndex = -1
        for(index in 0..textArray.size - 1) {
            val tImg = textArray[index]
            var cnt = 0
            for(i in 0..15) {
                (0..15).filter {
                    img.getRGB(i, it).and(0xFFFFFF) == tImg.getRGB(i, it).and(0xFFFFFF)
                }.forEach { cnt++ }
            }
            if(cnt > max) {
                max = cnt
                maxIndex = index
            }
        }

        return maxIndex + 1
    }
}

class AutoMiner (
        private val operator: FieldOperator
){
    fun work() {
        // randomly open first cell
        val x = (Math.random() * 30).toInt()
        val y = (Math.random() * 16).toInt()

        operator.open(x, y)
        Thread.sleep(500)
        operator.update()
        // begin operation loop
        while (!operator.isOver) {

        }
        operator.print()
    }

}