import java.awt.Rectangle
import java.awt.Robot
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
        0x18AC39
)
fun main(args : Array<String>) {
    val field = FieldGetter(rect = Rectangle(291, 187, 1627 - 291, 896 - 187))
    field.update()
    field.print()
}

class FieldGetter(
        private val w : Int = 30,
        private val h : Int = 16,
        private val rect : Rectangle
) {
    private val r = Robot()
    private val fieldArray : Array<IntArray> = Array(w, {
        val arr = kotlin.IntArray(h)
        Arrays.fill(arr, -1)
        arr
    })
    private val updateArray : Array<BooleanArray> = Array(w, {kotlin.BooleanArray(h) })
    operator fun get(w : Int) = fieldArray[w]

    fun invalidate(x : Int, y : Int) { updateArray[x][y] = false }
    fun update() {
        val startTm = System.currentTimeMillis()

        val img = r.createScreenCapture(rect)
        val cellW = rect.width / w
        val cellH = rect.height / h
        for(i in 0..h - 1) {
            for(j in 0..w - 1) {
                if(!updateArray[j][i]) {
                    val subImage = img.getSubimage(
                            j * rect.width / w, i * rect.height / h, cellW, cellH)
                    fieldArray[j][i] = if( subImage.find(colorUncovered)) {
                        subImage.extract(colorList)
                        subImage.cut(0x0)?.save("D:\\i\\$i$j.png")
                        0
                    } else {
                        -1
                    }
                }
                updateArray[j][i] = true
            }
        }

        println(System.currentTimeMillis() - startTm)
    }


    fun print() {
        for(i in 0..h - 1) {
            for(j in 0..w - 1) {
                print(String.format("%2d ", this[j][i]))
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
    return getSubimage(minX, minY, maxX - minX, maxY - minY)

}