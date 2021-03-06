package scorex.crypto.authds.skiplist

import com.google.common.primitives.{Ints, Longs}
import play.api.libs.json._
import scorex.crypto.authds.AuthData
import scorex.crypto.authds.merkle.MerkleTree.Position
import scorex.crypto.encode.Base58
import scorex.crypto.hash.CryptographicHash

import scala.annotation.tailrec
import scala.util.Try


/**
  * @param data - data block
  * @param proof - segment position and merkle path, complementary to data block
  */
case class SLAuthData[HashFunction <: CryptographicHash]
(data: Array[Byte], proof: SLPath[HashFunction]) extends AuthData[SLPath[HashFunction]] {

  type Digest = HashFunction#Digest

  lazy val bytes: Array[Byte] = {
    require(this.merklePathHashes.nonEmpty, "Merkle path cannot be empty")
    val dataSize = Ints.toByteArray(this.data.length)
    val merklePathLength = Ints.toByteArray(this.merklePathHashes.length)
    val merklePathSize = Ints.toByteArray(this.merklePathHashes.head.length)
    val merklePathBytes = this.merklePathHashes.foldLeft(Array.empty: Array[Byte])((b, mp) => b ++ mp)
    dataSize ++ merklePathLength ++ merklePathSize ++ data ++ merklePathBytes ++ Longs.toByteArray(proof.index)
  }

  lazy val merklePathHashes = proof.hashes

  /**
    * Checks that this block is at position $index in tree with root hash = $rootHash
    */
  def check(rootHash: Digest)(implicit hashFunction: HashFunction): Boolean = ???

}

object SLAuthData {
  def decode[HashFunction <: CryptographicHash](bytes: Array[Byte]): Try[SLAuthData[HashFunction]] = Try {
    val dataSize = Ints.fromByteArray(bytes.slice(0, 4))
    val merklePathLength = Ints.fromByteArray(bytes.slice(4, 8))
    val merklePathSize = Ints.fromByteArray(bytes.slice(8, 12))
    val data = bytes.slice(12, 12 + dataSize)
    val merklePathStart = 12 + dataSize
    val merklePath = (0 until merklePathLength).map { i =>
      bytes.slice(merklePathStart + i * merklePathSize, merklePathStart + (i + 1) * merklePathSize)
    }
    val index = Longs.fromByteArray(bytes.takeRight(8))
    SLAuthData(data, SLPath(index, merklePath))
  }

  implicit def authDataBlockReads[T, HashFunction <: CryptographicHash]
  (implicit fmt: Reads[T]): Reads[SLAuthData[HashFunction]] = new Reads[SLAuthData[HashFunction]] {
    def reads(json: JsValue): JsResult[SLAuthData[HashFunction]] = JsSuccess(SLAuthData[HashFunction](
      Base58.decode((json \ "data").as[String]).get,
      SLPath(
        (json \ "index").as[Long],
        (json \ "merklePath").get match {
          case JsArray(ts) => ts.map { t =>
            t match {
              case JsString(digest) =>
                Base58.decode(digest)
              case m =>
                throw new RuntimeException("MerklePath MUST be array of strings" + m + " given")
            }
          }.map(_.get)
          case m =>
            throw new RuntimeException("MerklePath MUST be a list " + m + " given")
        })
    ))
  }

  implicit def authDataBlockWrites[T, HashFunction <: CryptographicHash](implicit fmt: Writes[T]): Writes[SLAuthData[HashFunction]]
  = new Writes[SLAuthData[HashFunction]] {
    def writes(ts: SLAuthData[HashFunction]) = JsObject(Seq(
      "data" -> JsString(Base58.encode(ts.data)),
      "index" -> JsNumber(ts.proof.index),
      "merklePath" -> JsArray(
        ts.merklePathHashes.map(digest => JsString(Base58.encode(digest)))
      )
    ))
  }
}