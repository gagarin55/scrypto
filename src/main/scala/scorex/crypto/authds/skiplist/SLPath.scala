package scorex.crypto.authds.skiplist

import scorex.crypto.authds.DataProof
import scorex.crypto.authds.merkle.MerkleTree.Position
import scorex.crypto.encode._
import scorex.crypto.hash.CryptographicHash


case class SLPath[HashFunction <: CryptographicHash](index: Position, hashes: Seq[CryptographicHash#Digest]) extends DataProof {
  override def toString: String = s"(Index: $index, hashes: ${hashes.map(Base16.encode)})"
}