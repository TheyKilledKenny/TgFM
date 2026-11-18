package it.tgfm.core

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile

/** Seekable concatenation for numbered 7z volumes, without an assembled disk copy. */
class VolumeInput(files: List<File>) : Closeable {
 private val inputs = mutableListOf<RandomAccessFile>()
 private val lengths: List<Long>
 val size: Long
 var position = 0L
  private set
 init {
  try { files.forEach { inputs += RandomAccessFile(it, "r") } } catch(e: Exception) { close(); throw e }
  lengths = inputs.map { it.length() }
  size = lengths.sum()
 }
 fun seek(offset: Long, whence: Int): Long {
  val next = when(whence) { 0 -> offset; 1 -> position + offset; 2 -> size + offset; else -> error("Origine seek non valida") }
  require(next >= 0)
  position = next
  return next
 }
 fun read(buffer: ByteArray): Int {
  if(position >= size) return -1
  var base = 0L
  for(i in inputs.indices) {
   if(position < base + lengths[i]) {
    inputs[i].seek(position-base)
    val n = inputs[i].read(buffer, 0, minOf(buffer.size.toLong(), base+lengths[i]-position).toInt())
    check(n > 0) { "Volume troncato durante la lettura." }
    position += n
    return n
   }
   base += lengths[i]
  }
  return -1
 }
 override fun close() { inputs.forEach { it.close() } }
}
