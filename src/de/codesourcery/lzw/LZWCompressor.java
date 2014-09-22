package de.codesourcery.lzw;

import java.util.Random;


public class LZWCompressor implements ICompressor
{
	protected static final class TableEntry
	{
		public final byte[] pattern;

		public TableEntry(byte[] pattern,int lengthInBytes)
		{
			this.pattern = new byte[ lengthInBytes ];
			System.arraycopy( pattern , 0 , this.pattern , 0 , lengthInBytes );
		}

		private TableEntry(byte[] pattern) {
			this.pattern = pattern;
		}

		public void writePattern(BitStream out) {
			for ( int i = 0 ; i < pattern.length ; i++ ) {
				out.write( pattern[i] , 8 );
			}
		}

		public byte firstByte() {
			return pattern[0];
		}

		public boolean matches(byte[] pattern,final int patternLength)
		{
			if ( this.pattern.length == patternLength )
			{
				for ( int i = 0 ; i < patternLength ; i++ ) {
					if ( this.pattern[i] != pattern[i] ) {
						return false;
					}
				}
				return true;
			}
			return false;
		}

		public TableEntry newEntry(byte nextChar) {
			final byte[] buffer = new byte[ this.pattern.length + 1 ];
			System.arraycopy( this.pattern , 0 , buffer , 0 , this.pattern.length );
			buffer[ buffer.length -1 ] = nextChar;
			return new TableEntry( buffer );
		}

		@Override
		public String toString() {
			return new String( this.pattern );
		}
	}

	private TableEntry[] table = new TableEntry[4096];

	private int findTableEntry(byte[] patternBuffer,int patternPtr)
	{
		final int len = table.length;
		for ( int i = 0 ; i < len ; i++ ) {
			final TableEntry current = table[i];
			if ( current != null && current.matches( patternBuffer ,  patternPtr ) ) {
				return i;
			}
		}
		return -1;
	}

	public static void main(String[] args)
	{
		final char[] chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();

		final StringBuilder buffer = new StringBuilder();
		final Random rnd = new Random(0xdeadbeef);

		for ( int j = 0 ; j < 10 ; j++ )
		{
			final int len = rnd.nextInt( 100* 1024 );
			buffer.setLength( 0 );
			for ( int i = 0 ; i < len ; i++ ) {
				buffer.append( chars[ rnd.nextInt(chars.length ) ] );
			}
			testCompression( buffer.toString().getBytes() );
		}
	}

	private static void testCompression(final byte[] test)
	{
		final BitStream out = new BitStream(1024);

		final int numberOfCodeWords = new LZWCompressor().compress( test , out );

		final float inputBits = test.length*8;
		final float outputBits = numberOfCodeWords*12;
		final float ratio = 100.0f - 100.0f* ( outputBits / inputBits );
		System.out.println("Compressed: "+numberOfCodeWords+" 12-bit words = "+(numberOfCodeWords*12)+" bits (input: "+(test.length*8+" bits), compression: "+ratio+" %"));
		out.reset();

		final byte[] decompressed = new LZWCompressor().decompress( out , numberOfCodeWords );

		if ( decompressed.length != test.length ) {
			throw new RuntimeException("Length mismatch, expected "+test.length+" bytes but got "+decompressed);
		}
		for ( int i = 0 ; i < test.length ; i++ ) {
			if ( test[i] != decompressed[i] ) {
				throw new RuntimeException("Mismatch at offset "+i+" , expected "+test[i]+" bytes but got "+decompressed[i]);
			}
		}
	}

	@Override
	public int compress(byte[] in, BitStream out)
	{
		int codeWords = 0;
		if ( in.length == 0 ) {
			return codeWords;
		}

		initDictionary();

		final byte[] patternBuffer = new byte[4096];
		int patternPtr = 0;

		int tableInsertPtr = 256;

		int previousIdx = -1;
		patternPtr = 0;

		for ( int index = 0 ; index < in.length ; index++ )
		{
			final byte current = in[index];
			patternBuffer[patternPtr++] = current;

			final int existingIndex = findTableEntry(patternBuffer,patternPtr);
			if ( existingIndex == -1 )
			{
				if ( tableInsertPtr == table.length ) {
					initDictionary();
					tableInsertPtr = 256;
				}
				table[tableInsertPtr] = new TableEntry( patternBuffer,patternPtr );

				out.write( previousIdx , 12 );
				codeWords++;

				previousIdx = tableInsertPtr;

				tableInsertPtr++;

				patternBuffer[0] = current;
				previousIdx = current & 0x000000ff;
				patternPtr=1;
			}
			else
			{
				previousIdx = existingIndex;
			}
		}
		final int existingIndex = findTableEntry(patternBuffer,patternPtr);
		if ( existingIndex != -1 ) {
			out.write( existingIndex , 12 );
			codeWords++;
		} else {
			for ( int i = 0 ; i < patternPtr ; i++ )
			{
				int value = patternBuffer[i];
				value = value & 0xFF;
				out.write( value , 12 );
			}
		}
		return codeWords;
	}

	private void initDictionary() {
		for ( int i = 0 ; i < table.length ; i++ )
		{
			if ( i <= 255 ) {
				table[i] = new TableEntry( new byte[] { (byte) i } , 1 );
			} else {
				table[i] = null;
			}
		}
	}

	@Override
	public byte[] decompress(BitStream in,int numberOfCodeWords)
	{
		final BitStream out = new BitStream( 1 + (numberOfCodeWords*12) / 8 );

		if ( numberOfCodeWords == 0 ) {
			return out.getBytes();
		}

		initDictionary();

		int last = in.readInt(12);

		out.write( last , 8 );

		int tablePtr = 256;

		for ( int index = 0 ; index < numberOfCodeWords-1 ; index++ )
		{
			final int next = in.readInt( 12 );

			final TableEntry existing = table[ next ];
			if ( existing != null )
			{
				final TableEntry newEntry = table[ last ].newEntry( table[ next ].firstByte() );

				if ( tablePtr == table.length ) {
					initDictionary();
					tablePtr = 256;
				}
				table[ tablePtr++ ] = newEntry;
			}
			else
			{
				final TableEntry newEntry = table[ last ].newEntry( table[ last ].firstByte() );
				if ( tablePtr == table.length ) {
					initDictionary();
					tablePtr = 256;
				}
				table[ tablePtr++ ] = newEntry;
			}
			table[ next ].writePattern( out );
			last = next;
		}
		return out.getBytes();
	}
}