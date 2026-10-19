package com.yywspace.anethack

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.util.Log


class NHTileSet(val nh: NetHack) {
    private val tileCache = mutableMapOf<Int, Bitmap>()
    private var tileBitmap: Bitmap? = null
    private var tileSetName:String? = null
    var tileWidth = 0
    var tileHeight = 0
    // 图集统一的背景色，浅色主题下用它把瓦片底色并入地图背景
    private var tileBackgroundColor: Int? = null
    private val recoloredTileCache = mutableMapOf<Int, Bitmap>()
    private var recoloredBackground = 0

    fun getTile(tile:Int):Bitmap? {
        tileBitmap?.apply {
            var bitmap = tileCache[tile]
            if (bitmap == null) {
                val tx = tile % (width / tileWidth)
                val ty = tile / (width / tileWidth)
                if (tx < 0 || ty < 0)
                    return null
                bitmap = Bitmap.createBitmap(this, tx * tileWidth, ty * tileHeight, tileWidth, tileHeight)
                tileCache[tile] = bitmap
            }
            return bitmap
        }
        return null
    }

    /**
     * 返回把图集统一背景色替换为 [backgroundColor] 后的瓦片。
     * 浅色主题下瓦片自带的深色底会盖住地图背景，替换后才能得到柔和的地图底色。
     */
    fun getTile(tile:Int, backgroundColor:Int?):Bitmap? {
        val bitmap = getTile(tile) ?: return null
        val sourceBackground = tileBackgroundColor ?: return bitmap
        if (backgroundColor == null) return bitmap
        if (recoloredBackground != backgroundColor) {
            recoloredTileCache.clear()
            recoloredBackground = backgroundColor
        }
        return recoloredTileCache.getOrPut(tile) {
            val copy = bitmap.copy(Bitmap.Config.ARGB_8888, true) ?: return bitmap
            val pixels = IntArray(copy.width * copy.height)
            copy.getPixels(pixels, 0, copy.width, 0, 0, copy.width, copy.height)
            var changed = false
            for (i in pixels.indices) {
                if (pixels[i] == sourceBackground) {
                    pixels[i] = backgroundColor
                    changed = true
                }
            }
            if (changed) {
                copy.setPixels(pixels, 0, copy.width, 0, 0, copy.width, copy.height)
                copy
            } else {
                copy.recycle()
                bitmap
            }
        }
    }

    fun updateTileSet() {
        when(nh.prefs.tileSet) {
            // default
            "2" -> {
                val am: AssetManager = nh.context.resources.assets
                am.open("tiles/default_tiles_16.bmp").use {
                    tileBitmap = BitmapFactory.decodeStream(it)
                }
                tileHeight = 16
                tileWidth = 16
                tileCache.clear()
            }
            "3" -> {
                val am: AssetManager = nh.context.resources.assets
                am.open("tiles/nevanda_32.png").use {
                    tileBitmap = BitmapFactory.decodeStream(it)
                }
                tileHeight = 32
                tileWidth = 32
                tileCache.clear()
            }
            "4" -> {
                val am: AssetManager = nh.context.resources.assets
                am.open("tiles/PixelHack.png").use {
                    tileBitmap = BitmapFactory.decodeStream(it)
                }
                tileHeight = 32
                tileWidth = 32
                tileCache.clear()
            }
        }
        // 图集所有瓦片共用同一个底色（如 PixelHack 的灰绿色），记录它用于浅色主题替换
        tileBackgroundColor = tileBitmap?.getPixel(0, 0)
        recoloredTileCache.clear()
        recoloredBackground = 0
    }

    fun isTileSetChange():Boolean {
        if (tileSetName == null)
            tileSetName = nh.prefs.tileSet
        if (tileSetName != nh.prefs.tileSet) {
            tileSetName = nh.prefs.tileSet?:"1"
            return true
        }
        return false
    }
    fun isTTY(): Boolean {
        return nh.prefs.tileSet == "1" // ascii
    }

    fun getOverlayRect(overlay: Int): Rect {
        if (overlay and MG_PET != 0
            || overlay and MG_OBJPILE != 0
            || overlay and MG_DETECT != 0
            || overlay and MG_INVIS != 0)
            return Rect(0, 0, 32, 32)
        return Rect(0, 0, 32, 32)
    }

    fun getTileOverlay(overlay: Int): Bitmap? {
        val overlayPath = if (overlay and MG_PET != 0)
            "tiles/overlay_pet.png"
        else if (overlay and MG_OBJPILE != 0)
            "tiles/overlay_pile.png"
        else if (overlay and MG_INVIS != 0)
            "tiles/overlay_invis.png"
        else if (overlay and MG_DETECT != 0)
            "tiles/overlay_default.png"
        else
            ""
        if(overlayPath.isNotEmpty()) {
            nh.context.resources.assets.open(overlayPath).use {
                return BitmapFactory.decodeStream(it)
            }
        }
        return null
    }

    companion object {
        const val MG_INVIS = 0x00004
        const val MG_DETECT = 0x00008
        const val MG_PET = 0x00010
        const val MG_OBJPILE = 0x00080

    }
}