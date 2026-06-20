package com.example.player.ui.util

import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.RandomAccessFile

object LyricsExtractor {

    private const val TAG = "LyricsExtractor"

    fun getLyricsContent(audioPath: String?): String? {
        if (audioPath.isNullOrEmpty()) return null

        val embeddedLyrics = AudioLyricsParser.getEmbeddedLyrics(audioPath)
        if (!embeddedLyrics.isNullOrEmpty()) {
            Log.d(TAG, "Found embedded lyrics for: $audioPath")
            return embeddedLyrics
        }

        try {
            val audioFile = File(audioPath)
            val parentDir = audioFile.parent
            val fileName = audioFile.name
            val nameNoExt = fileName.substringBeforeLast('.', fileName)

            val lrcFile = File(parentDir, "$nameNoExt.lrc")
            if (lrcFile.exists() && lrcFile.isFile) {
                val sb = StringBuilder()
                val fis = FileInputStream(lrcFile)
                val br = BufferedReader(InputStreamReader(fis))
                var line: String?
                while (br.readLine().also { line = it } != null) {
                    sb.append(line).append("\n")
                }
                br.close()
                fis.close()
                Log.d(TAG, "Found external lrc file")
                return sb.toString()
            }
        } catch (e: Exception) {
            Log.e(TAG, "读取LRC文件失败: ${e.message}")
        }

        return null
    }

    private object AudioLyricsParser {
        fun getEmbeddedLyrics(path: String?): String? {
            if (path == null) return null
            return when {
                path.lowercase().endsWith(".mp3") -> getMp3Lyrics(path)
                path.lowercase().endsWith(".flac") -> getFlacLyrics(path)
                else -> null
            }
        }

        private fun getFlacLyrics(path: String): String? {
            var raf: RandomAccessFile? = null
            try {
                raf = RandomAccessFile(path, "r")
                val header = ByteArray(4)
                raf.read(header)
                if (String(header) != "fLaC") return null

                var lastBlock = false
                while (!lastBlock) {
                    val blockHeader = raf.readUnsignedByte()
                    lastBlock = (blockHeader and 0x80) != 0
                    val type = blockHeader and 0x7F
                    val len1 = raf.readUnsignedByte()
                    val len2 = raf.readUnsignedByte()
                    val len3 = raf.readUnsignedByte()
                    val length = (len1 shl 16) or (len2 shl 8) or len3

                    if (type == 4) {

                        val vendorLen = Integer.reverseBytes(raf.readInt())
                        raf.skipBytes(vendorLen)

                        val commentListLen = Integer.reverseBytes(raf.readInt())
                        for (i in 0 until commentListLen) {
                            val commentLen = Integer.reverseBytes(raf.readInt())
                            val commentBytes = ByteArray(commentLen)
                            raf.read(commentBytes)
                            val comment = String(commentBytes, Charsets.UTF_8)
                            val upper = comment.uppercase()
                            if (upper.startsWith("LYRICS=") || upper.startsWith("UNSYNCED LYRICS=")) {
                                val eqIndex = comment.indexOf('=')
                                return comment.substring(eqIndex + 1)
                            }
                        }
                        return null
                    } else {
                        raf.skipBytes(length)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "FLAC parse error: ${e.message}")
            } finally {
                try { raf?.close() } catch (_: IOException) {}
            }
            return null
        }

        private fun getMp3Lyrics(path: String): String? {
            var raf: RandomAccessFile? = null
            try {
                raf = RandomAccessFile(path, "r")
                val header = ByteArray(3)
                raf.read(header)
                if (String(header) != "ID3") return null

                raf.skipBytes(2)
                val flags = raf.readByte()
                val size = readSynchsafeInt(raf)
                val endOfTag = raf.filePointer + size

                while (raf.filePointer < endOfTag) {
                    val frameIdBytes = ByteArray(4)
                    if (raf.read(frameIdBytes) < 4) break
                    val frameId = String(frameIdBytes)

                    val frameSize = raf.readInt()
                    raf.skipBytes(2)

                    if (frameId == "USLT") {
                        val encoding = raf.readByte()
                        raf.skipBytes(3)

                        var b: Byte
                        while (raf.readByte().also { b = it } != 0.toByte()) {

                        }

                        if (encoding.toInt() == 1 || encoding.toInt() == 2) {
                            val pos = raf.filePointer
                            if (raf.readByte().toInt() != 0) {
                                raf.seek(pos)
                            }
                        }

                        val readSoFar = 1 + 3 + 1
                        val buffer = ByteArray(minOf(frameSize - readSoFar, 50000))
                        raf.read(buffer)

                        val charset = when (encoding.toInt()) {
                            1 -> "UTF-16"
                            2 -> "UTF-16BE"
                            3 -> "UTF-8"
                            else -> "ISO-8859-1"
                        }

                        return String(buffer, charset(charset)).trim()
                    } else {
                        raf.skipBytes(frameSize)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "MP3 parse error: ${e.message}")
            } finally {
                try { raf?.close() } catch (_: IOException) {}
            }
            return null
        }

        @Throws(IOException::class)
        private fun readSynchsafeInt(raf: RandomAccessFile): Int {
            val b1 = raf.readByte().toInt() and 0xFF
            val b2 = raf.readByte().toInt() and 0xFF
            val b3 = raf.readByte().toInt() and 0xFF
            val b4 = raf.readByte().toInt() and 0xFF
            return (b1 shl 21) or (b2 shl 14) or (b3 shl 7) or b4
        }
    }
}
