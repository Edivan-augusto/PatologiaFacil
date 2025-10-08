package com.hitsu.patologiafacil.analysis

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.*

/**
 * Pipeline 100% Kotlin/SDK (sem OpenCV)
 * 1) Resize (maior lado ~720)
 * 2) Grayscale
 * 3) Equalização local simples (janela 8x8)
 * 4) Mediana 3x3
 * 5) Sobel Gx/Gy + Magnitude + Non-Max Suppression
 * 6) Duplo limiar + histerese (Canny simplificado)
 * 7) Abertura + fechamento (3x3)
 * 8) Afinamento (Zhang–Suen)
 * 9) Componentes conectados + seleção dos maiores traços finos
 * 10) Extração de features + regras heurísticas
 */
object ImageAnalyzer {

    data class Mask(val w:Int, val h:Int, val data: BooleanArray)

    fun analyze(input: Bitmap): AnalysisOut {
        val bmp = resizeMax(input, 720)
        val gray = toGray(bmp)
        val eq = localEqualize(gray, 8)
        val den = median3(eq)
        val (mag, dir) = sobel(den)
        val nms = nonMaxSuppression(mag, dir)
        val edges = hysteresis(nms, low = 25.0, high = 60.0)
        val morph = close(open(edges))
        val thin = zhangSuen(morph)

        // Connected components and features
        val comps = connected(thin)
        val crack = selectMajorStrokes(comps, minLen = (min(bmp.width, bmp.height) * 0.15).toInt())
        val feats = features(crack, bmp)

        val (severity, charText) = severityAndText(feats)
        val causes = rankCauses(feats)
        val advice = listOf(
            "Monitorar evolução e registrar novas fotos em 30 dias.",
            "Evitar sobrecargas no pavimento até avaliação.",
            "Consultar profissional habilitado se aumentar a abertura."
        )
        return AnalysisOut(feats, severity, charText, causes, advice)
    }

    data class FeatureSet(
        val orientation:String, val lengthPx:Int, val widthMmEst:Double,
        val branches:Int, val nearOpening:Boolean, val nearBase:Boolean,
        val nearCorners:Boolean, val moisturePattern:Boolean, val mapCrack:Boolean
    )
    data class CauseScore(val name:String, val score:Double, val why:String)
    data class AnalysisOut(
        val features: FeatureSet,
        val severity: String,
        val characterization: String,
        val causes: List<CauseScore>,
        val advice: List<String>
    )

    private fun resizeMax(b: Bitmap, maxSide:Int): Bitmap {
        val scale = max(b.width, b.height).toDouble() / maxSide.toDouble()
        if (scale <= 1.0) return b
        val w = (b.width / scale).toInt()
        val h = (b.height / scale).toInt()
        return Bitmap.createScaledBitmap(b, w, h, true)
    }

    private fun toGray(b: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(b.width, b.height, Bitmap.Config.ARGB_8888)
        val p = IntArray(b.width * b.height)
        b.getPixels(p, 0, b.width, 0, 0, b.width, b.height)
        for (i in p.indices) {
            val c = p[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val bl = c and 0xFF
            val y = (0.299*r + 0.587*g + 0.114*bl).toInt()
            p[i] = Color.rgb(y,y,y)
        }
        out.setPixels(p,0,b.width,0,0,b.width,b.height)
        return out
    }

    private fun localEqualize(b: Bitmap, tile:Int): Bitmap {
        val w=b.width; val h=b.height
        val out = Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
        val p = IntArray(w*h); b.getPixels(p,0,w,0,0,w,h)
        fun clamp(v:Int)= if(v<0)0 else if(v>255)255 else v
        val gridX = (w + tile -1)/tile
        val gridY = (h + tile -1)/tile
        val avg = Array(gridY){IntArray(gridX)}
        for (gy in 0 until gridY) for (gx in 0 until gridX) {
            var sum=0; var cnt=0
            for (y in gy*tile until min((gy+1)*tile,h)) for (x in gx*tile until min((gx+1)*tile,w)) {
                val c = p[y*w+x] and 0xFF
                sum+=c; cnt++
            }
            avg[gy][gx] = if (cnt>0) sum/cnt else 128
        }
        for (y in 0 until h) for (x in 0 until w) {
            val gy = y/tile; val gx = x/tile
            val v = p[y*w+x] and 0xFF
            val a = avg[gy][gx]
            val nv = clamp((v - a) * 2 + 128)
            p[y*w+x] = Color.rgb(nv,nv,nv)
        }
        out.setPixels(p,0,w,0,0,w,h)
        return out
    }

    private fun median3(b: Bitmap): Bitmap {
        val w=b.width; val h=b.height
        val inP = IntArray(w*h); val outP = IntArray(w*h)
        b.getPixels(inP,0,w,0,0,w,h)
        fun g(i:Int)= inP[i] and 0xFF
        for (y in 1 until h-1) {
            for (x in 1 until w-1) {
                val idx = y*w+x
                val arr = intArrayOf(
                    g(idx-w-1),g(idx-w),g(idx-w+1),
                    g(idx-1),g(idx),g(idx+1),
                    g(idx+w-1),g(idx+w),g(idx+w+1)
                )
                arr.sort()
                val m = arr[4]
                outP[idx] = Color.rgb(m,m,m)
            }
        }
        return Bitmap.createBitmap(outP, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun sobel(b: Bitmap): Pair<Array<DoubleArray>, Array<DoubleArray>> {
        val w=b.width; val h=b.height
        val p = IntArray(w*h); b.getPixels(p,0,w,0,0,w,h)
        val mag = Array(h){ DoubleArray(w) }
        val dir = Array(h){ DoubleArray(w) }
        fun g(x:Int,y:Int)= (p[y*w+x] and 0xFF).toDouble()
        for (y in 1 until h-1) for (x in 1 until w-1) {
            val gx = (-1*g(x-1,y-1) + 1*g(x+1,y-1)
                     -2*g(x-1,y  ) + 2*g(x+1,y  )
                     -1*g(x-1,y+1) + 1*g(x+1,y+1))
            val gy = ( 1*g(x-1,y-1) + 2*g(x,y-1) + 1*g(x+1,y-1)
                      -1*g(x-1,y+1) - 2*g(x,y+1) -1*g(x+1,y+1))
            val m = hypot(gx,gy)
            mag[y][x] = m
            dir[y][x] = atan2(gy,gx)
        }
        return Pair(mag,dir)
    }

    private fun nonMaxSuppression(mag:Array<DoubleArray>, dir:Array<DoubleArray>): Array<DoubleArray> {
        val h=mag.size; val w=mag[0].size
        val out = Array(h){ DoubleArray(w) }
        fun angleBucket(a:Double):Int{
            val deg = Math.toDegrees(a)
            val d = ((deg+180)%180)
            return when {
                d < 22.5 || d >= 157.5 -> 0
                d < 67.5 -> 1
                d < 112.5 -> 2
                else -> 3
            }
        }
        for (y in 1 until h-1) for (x in 1 until w-1) {
            val b = angleBucket(dir[y][x])
            val m = mag[y][x]
            val (m1,m2) = when (b) {
                0 -> Pair(mag[y][x-1], mag[y][x+1])
                1 -> Pair(mag[y-1][x+1], mag[y+1][x-1])
                2 -> Pair(mag[y-1][x], mag[y+1][x])
                else -> Pair(mag[y-1][x-1], mag[y+1][x+1])
            }
            out[y][x] = if (m >= m1 && m >= m2) m else 0.0
        }
        return out
    }

    private fun hysteresis(nms:Array<DoubleArray>, low:Double, high:Double): Mask {
        val h=nms.size; val w=nms[0].size
        val strong = Array(h){BooleanArray(w)}
        val weak = Array(h){BooleanArray(w)}
        for (y in 0 until h) for (x in 0 until w) {
            val v = nms[y][x]
            if (v >= high) strong[y][x]=true
            else if (v >= low) weak[y][x]=true
        }
        val res = BooleanArray(w*h)
        fun idx(x:Int,y:Int)=y*w+x
        // grow from strong edges
        val q = ArrayDeque<Pair<Int,Int>>()
        for (y in 1 until h-1) for (x in 1 until w-1) if (strong[y][x]) {
            res[idx(x,y)]=true; q.add(Pair(x,y))
        }
        val dirs = listOf(-1,-1, -1,0, -1,1, 0,-1, 0,1, 1,-1, 1,0, 1,1)
        while(q.isNotEmpty()){
            val (x,y)=q.removeFirst()
            for (i in dirs.indices step 2){
                val nx=x+dirs[i]; val ny=y+dirs[i+1]
                if (nx in 1 until w-1 && ny in 1 until h-1){
                    if (!res[idx(nx,ny)] && weak[ny][nx]){
                        res[idx(nx,ny)]=true
                        q.add(Pair(nx,ny))
                    }
                }
            }
        }
        return Mask(w,h,res)
    }

    private fun open(m:Mask)= dilate(erode(m))
    private fun close(m:Mask)= erode(dilate(m))

    private fun erode(m:Mask):Mask{
        val w=m.w; val h=m.h; val out=BooleanArray(w*h)
        fun idx(x:Int,y:Int)=y*w+x
        for (y in 1 until h-1) for (x in 1 until w-1){
            var ok=true
            for (dy in -1..1) for (dx in -1..1){
                if (!m.data[idx(x+dx,y+dy)]) { ok=false; break }
            }
            out[idx(x,y)]=ok
        }
        return Mask(w,h,out)
    }
    private fun dilate(m:Mask):Mask{
        val w=m.w; val h=m.h; val out=BooleanArray(w*h)
        fun idx(x:Int,y:Int)=y*w+x
        for (y in 1 until h-1) for (x in 1 until w-1){
            var ok=false
            for (dy in -1..1) for (dx in -1..1){
                if (m.data[idx(x+dx,y+dy)]) { ok=true; break }
            }
            out[idx(x,y)]=ok
        }
        return Mask(w,h,out)
    }

    private fun zhangSuen(m:Mask):Mask{
        val w=m.w; val h=m.h
        val a = m.data.copyOf()
        fun idx(x:Int,y:Int)=y*w+x
        var changed:Boolean
        do {
            changed=false
            val rem1 = mutableListOf<Int>()
            for (y in 1 until h-1) for (x in 1 until w-1){
                val p = neighbors(a,w,h,x,y)
                val bp = p.count { it }
                if (!a[idx(x,y)] || bp<2 || bp>6) continue
                val ap = transitions(p)
                if (ap!=1) continue
                if (!( !p[0] || !p[2] || !p[4])) continue
                if (!( !p[2] || !p[4] || !p[6])) continue
                rem1.add(idx(x,y))
            }
            if (rem1.isNotEmpty()){ changed=true; rem1.forEach{ a[it]=false } }

            val rem2 = mutableListOf<Int>()
            for (y in 1 until h-1) for (x in 1 until w-1){
                val p = neighbors(a,w,h,x,y)
                val bp = p.count { it }
                if (!a[idx(x,y)] || bp<2 || bp>6) continue
                val ap = transitions(p)
                if (ap!=1) continue
                if (!( !p[0] || !p[2] || !p[6])) continue
                if (!( !p[0] || !p[4] || !p[6])) continue
                rem2.add(idx(x,y))
            }
            if (rem2.isNotEmpty()){ changed=true; rem2.forEach{ a[it]=false } }
        } while(changed)
        return Mask(w,h,a)
    }
    private fun neighbors(a:BooleanArray,w:Int,h:Int,x:Int,y:Int):BooleanArray{
        fun id(xx:Int,yy:Int)= yy*w+xx
        return booleanArrayOf(
            a[id(x, y-1)], a[id(x+1, y-1)],
            a[id(x+1, y)], a[id(x+1, y+1)],
            a[id(x, y+1)], a[id(x-1, y+1)],
            a[id(x-1, y)], a[id(x-1, y-1)]
        )
    }
    private fun transitions(p:BooleanArray):Int{
        var count=0
        for (i in 0 until 8){
            val a = p[i]
            val b = p[(i+1)%8]
            if (!a && b) count++
        }
        return count
    }

    private data class Component(val pixels: List<Pair<Int,Int>>)

    private fun connected(m:Mask): List<Component> {
        val w=m.w; val h=m.h; val vis=BooleanArray(w*h)
        fun idx(x:Int,y:Int)=y*w+x
        val res = mutableListOf<Component>()
        for (y in 1 until h-1) for (x in 1 until w-1){
            if (m.data[idx(x,y)] && !vis[idx(x,y)]){
                val q=ArrayDeque<Pair<Int,Int>>()
                val pix=mutableListOf<Pair<Int,Int>>()
                q.add(Pair(x,y)); vis[idx(x,y)]=true
                while(q.isNotEmpty()){
                    val (cx,cy)=q.removeFirst()
                    pix.add(Pair(cx,cy))
                    for (dy in -1..1) for (dx in -1..1){
                        val nx=cx+dx; val ny=cy+dy
                        if (nx in 1 until w-1 && ny in 1 until h-1){
                            val id=idx(nx,ny)
                            if (!vis[id] && m.data[id]){ vis[id]=true; q.add(Pair(nx,ny)) }
                        }
                    }
                }
                res.add(Component(pix))
            }
        }
        return res
    }

    private fun selectMajorStrokes(cs: List<Component>, minLen:Int): Component {
        if (cs.isEmpty()) return Component(emptyList())
        return cs.maxBy { it.pixels.size.coerceAtLeast(minLen) }
    }

    private fun features(c:Component, b:Bitmap): FeatureSet {
        val w=b.width; val h=b.height
        val pts = c.pixels
        if (pts.isEmpty()){
            return FeatureSet("indefinida",0,0.2,0,false,false,false,false,false)
        }
        var xMin=Int.MAX_VALUE; var xMax=Int.MIN_VALUE
        var yMin=Int.MAX_VALUE; var yMax=Int.MIN_VALUE
        pts.forEach{ (x,y)->
            if (x<xMin) xMin=x
            if (x>xMax) xMax=x
            if (y<yMin) yMin=y
            if (y>yMax) yMax=y
        }
        val dx=(xMax-xMin).toDouble(); val dy=(yMax-yMin).toDouble()
        val orientation = when {
            dy > dx*1.5 -> "vertical"
            dx > dy*1.5 -> "horizontal"
            else -> "diagonal"
        }
        val lengthPx = sqrt(dx*dx + dy*dy).toInt()
        val widthMmEst = 1.0 + (pts.size / 5000.0) // heurística boba para demo
        val branches = (pts.size / 1200).coerceAtLeast(0)

        val nearBase = yMax > h*0.85
        val nearCorners = (xMin < w*0.1 && yMin < h*0.1) || (xMax > w*0.9 && yMax > h*0.9)
        val nearOpening = (xMin < w*0.35 && yMin < h*0.35) || (xMax > w*0.65 && yMax > h*0.65)
        val moisturePattern = nearBase
        val mapCrack = branches >= 3 && orientation=="horizontal"

        return FeatureSet(orientation, lengthPx, widthMmEst, branches, nearOpening, nearBase, nearCorners, moisturePattern, mapCrack)
    }

    private fun severityAndText(f: FeatureSet): Pair<String,String> {
        val sev = when {
            f.widthMmEst <= 0.5 -> "Baixa"
            f.widthMmEst <= 3.0 -> "Moderada"
            else -> "Alta"
        }
        val text = "Fissura ${f.orientation} (~${"%.1f".format(f.widthMmEst)} mm). Comprimento relativo ${f.lengthPx}px."
        return Pair(sev, text)
    }

    private fun rankCauses(f: FeatureSet): List<CauseScore> {
        val causes = mutableListOf<CauseScore>()
        // Movimentação higroscópica
        var score = 0.0
        if (f.orientation=="horizontal" && f.nearBase && f.moisturePattern) score += 0.9
        causes += CauseScore("Movimentação higroscópica", score, "horizontais na base + padrão de umidade")

        // Variação térmica
        score = 0.0
        if (f.orientation=="horizontal" && !f.moisturePattern) score += 0.7
        causes += CauseScore("Variação térmica", score, "horizontais distribuídas, sem umidade")

        // Deformabilidade (vigas/lajes)
        score = 0.0
        if (f.nearCorners && f.orientation=="diagonal") score += 0.7
        causes += CauseScore("Deformabilidade de vigas/lajes", score, "inclinações (~45°) próximas a cantos")

        // Aberturas (portas/janelas)
        score = 0.0
        if (f.nearOpening && f.orientation=="diagonal") score += 0.8
        causes += CauseScore("Aberturas (portas/janelas)", score, "trincas partem de vértices de vãos")

        // Sobrecarga
        score = 0.0
        if (f.orientation=="vertical") score += 0.7
        causes += CauseScore("Sobrecarga estrutural", score, "verticais marcantes no centro")

        // Recalque diferencial
        score = 0.0
        if (f.orientation=="diagonal" && f.nearBase) score += 0.7
        causes += CauseScore("Recalque diferencial", score, "diagonais (~45°) base→topo")

        // Reações químicas/revestimento
        score = 0.0
        if (f.mapCrack) score += 0.6
        causes += CauseScore("Reações químicas/revestimento", score, "fissuras em 'mapa' e descascamento")

        // Falhas de projeto/execução (fallback)
        score = 0.0
        val max = causes.maxOfOrNull { it.score } ?: 0.0
        if (max < 0.35) score = 0.5
        causes += CauseScore("Falhas de projeto/execução", score, "nenhuma hipótese dominante")

        return causes.sortedByDescending { it.score }.take(3)
    }
}
