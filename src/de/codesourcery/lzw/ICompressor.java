package de.codesourcery.lzw;

public interface ICompressor {

	/**
	 *
	 * @param in
	 * @param out
	 * @return number of code words
	 */
	public int compress(byte[] in, BitStream out);

	public byte[] decompress(BitStream in,int numberOfCodeWords);
}
