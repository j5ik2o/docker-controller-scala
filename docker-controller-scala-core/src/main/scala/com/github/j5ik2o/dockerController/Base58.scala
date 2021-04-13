package com.github.j5ik2o.dockerController

import java.security.SecureRandom

object Base58 {
  private val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toCharArray
  private val RANDOM   = new SecureRandom

  def randomString(length: Int): String = {
    val result = new Array[Char](length)
    for (i <- 0 until length) {
      val pick = ALPHABET(RANDOM.nextInt(ALPHABET.length))
      result(i) = pick
    }
    new String(result)
  }
}
