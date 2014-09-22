package de.codesourcery.lzw;

import java.io.EOFException;
import java.util.Random;

public class BitStream
{
	private long[] buffer;

	private int writePtr;
	private int writeBit=63;

	private int readPtr;
	private int readBit=63;

	private int sizeInBits;

	public BitStream(byte[] data, int bitsInArray)
	{
		final int bytesInArray = (int) Math.ceil( bitsInArray / 8f );

		this.buffer = new long[ (int) Math.ceil( bytesInArray / 8f ) ];
		this.sizeInBits = bitsInArray;

		int byteIndex = 0;
		int longIndex = 0;

		long currentValue = 0;
		int byteInLong = 3;

		for ( int i = 0 ; i < bytesInArray ; i++ )
		{
			currentValue = currentValue << 8;
			currentValue |= (data[byteIndex++] & 0xffL);
			byteInLong--;
			if ( byteInLong == 0 ) {
				buffer[ longIndex++ ] = currentValue;
			}
		}
		buffer[ longIndex ] = currentValue;

		this.sizeInBits = bitsInArray;
		this.writePtr = longIndex;
		this.writeBit = 63 - ( bitsInArray % 63 );
	}

	public byte[] getBytes(int numberOfBits)
	{
		if ( numberOfBits < 0 ) {
			throw new IllegalArgumentException("numberOfBits must be >= 0");
		}
		if ( numberOfBits == 0 ) {
			return new byte[0];
		}
		if ( numberOfBits > sizeInBits ) {
			throw new IllegalArgumentException("Cannot read "+numberOfBits+" bits from buffer that contains only "+sizeInBits+" bits.");
		}
		final int sizeInBytes = (int) Math.ceil( numberOfBits / 8f );
		final byte[] result = new byte[ sizeInBytes  ];

		int byteIndex = 0;
		int longIndex = 0;

		int bitsRemaining = numberOfBits;

		int toShift = 7*8;

		int byteInLong = 0;

		long currentValue = buffer[ longIndex ];
		while ( bitsRemaining >= 8 )
		{
			result[ byteIndex++ ] = (byte) ( (currentValue >>> toShift) & 0xFF);
			bitsRemaining -= 8;

			if ( byteInLong == 7 )
			{
				toShift = 7*8;
				byteInLong = 0;
				longIndex++;
				if ( bitsRemaining > 0 ) {
					currentValue = buffer[longIndex];
				}
			} else {
				byteInLong++;
				toShift -= 8;
			}
		}

		if ( bitsRemaining > 0 )
		{
			final long mask = (1 << bitsRemaining+1)-1;
			result[ byteIndex ] = (byte) ( ( buffer[longIndex] >> toShift) & mask );
		}
		return result;
	}
	public byte[] getBytes()
	{
		return getBytes( sizeInBits );
	}

	public BitStream(int sizeInBytes)
	{
		if ( sizeInBytes < 1 ) {
			throw new RuntimeException("Internal error,size must be >= 1");
		}
		final int sizeInLongs = (int) Math.ceil( sizeInBytes / 8f );
		this.buffer = new long[ sizeInLongs ];
	}

	public int getSizeInBits() {
		return sizeInBits;
	}

	private static final int BUFFER_SIZE = 1024;
	private static final int KB_TO_WRITE = 2048;
	private static final int WARMUP_ROUNDS = 30;

	public static void main(String[] args) throws EOFException {

		final byte[] data = new byte[BUFFER_SIZE];

		final Random rnd = new Random(0xdeadbeef);
		rnd.nextBytes( data );

		final BitStream buffer = new BitStream( (KB_TO_WRITE+1)*1024 );

		long t = benchmark( () ->
		{
			for ( int i = 0 ; i < KB_TO_WRITE; i++ )
			{
				for ( final long b : data )
				{
					buffer.write( b & 0xffL , 8 );
				}
			}
		});

		float dataPerSecond = (KB_TO_WRITE/1024f) / (t/1000f);
		System.out.println("\nWrote "+KB_TO_WRITE+" in "+t+" ms ("+dataPerSecond+" MB/s)");

		/*
		 * Sanity check
		 */
		final byte[] bufferContents = buffer.getBytes(1024*8);
		if ( bufferContents.length != data.length ) {
			throw new RuntimeException("Length mismatch , expected: "+data.length+" , actual: "+bufferContents.length);
		}
		for ( int i = 0 ; i < bufferContents.length ; i++ )
		{
			final byte actual   = bufferContents[i];
			final byte expected = data[i];
			if ( expected != actual ) {
				throw new RuntimeException("Read error at offset "+i+" , expected 0b"+Long.toBinaryString( expected )+" , got 0b"+Long.toBinaryString( actual ) );
			}
		}

		t = benchmark( () ->
		{
			long sum = 0;
			for ( int j = 0 ; j < KB_TO_WRITE ; j++)
			{
				buffer.reset();
				for ( int i = 0 ; i < BUFFER_SIZE ; i++ )
				{
					sum += buffer.readLong( 8 );
				}
			}
			System.out.print("sum: "+sum);
		});
		dataPerSecond = (KB_TO_WRITE/1024f) / (t/1000f);
		System.out.println("\n\nRead "+KB_TO_WRITE+" in "+t+" ms ("+dataPerSecond+" MB/s)");
	}

	protected  static final long benchmark(Runnable r)
	{
		for ( int warmup = WARMUP_ROUNDS ; warmup > 0 ; warmup --)
		{
			System.out.println("WARMUP - "+time(r)+" ms");
		}
		return time( r );
	}

	protected static final long time(Runnable block)
	{
		long time = -System.currentTimeMillis();
		block.run();
		time += System.currentTimeMillis();
		return time;
	}

	public void write(final long value,final int numberOfBits)
	{
		long readMask = 1L << (numberOfBits-1);

		int writeBit = this.writeBit;
		int writePtr = this.writePtr;

		long setMask = (1L << writeBit);

		long currentValue = buffer[ writePtr ];
		for ( int i = 0 ; i < numberOfBits ; i++ )
		{
			if ( (value & readMask ) != 0 ) {
				currentValue |= setMask;
			}
			readMask = readMask >>> 1;

			if ( writeBit == 0 )
			{
				buffer[ writePtr ] = currentValue;
				writePtr++;
				if ( writePtr == buffer.length ) {
					resizeBuffer();
				}
				currentValue = 0;
				writeBit = 63;
				setMask = 1L << 63;
			} else {
				writeBit--;
				setMask  =  setMask >>> 1;
			}
		}
		this.writePtr = writePtr;
		this.writeBit = writeBit;
		buffer[writePtr] = currentValue;
		sizeInBits += numberOfBits;
	}

	public void reset() {
		this.readBit = 63;
		this.readPtr = 0;
		this.writeBit = 63;
		this.writePtr = 0;
		this.sizeInBits = 0;
	}

	public long readLong(final int numberOfBits)
	{
		long result = 0;

		int readBit = this.readBit;
		int readPtr = this.readPtr;

		long readMask = 1L << readBit;
		long currentValue = buffer[ readPtr ];

		for ( int i = 0 ; i < numberOfBits ; i++ )
		{
			result = result << 1;
			if ( ( currentValue & readMask ) != 0 ) {
				result |= 1;
			}
			if ( readBit == 0 )
			{
				readPtr++;
				currentValue = buffer[ readPtr ];
				readBit = 63;
				readMask = 1L << 63;
			} else {
				readBit--;
				readMask = readMask >>> 1;
			}
		}
		this.readBit = readBit;
		this.readPtr = readPtr;
		return result;
	}

	public int readInt(final int numberOfBits)
	{
		int result = 0;

		int readBit = this.readBit;
		int readPtr = this.readPtr;

		long readMask = 1L << readBit;
		long currentValue = buffer[ readPtr ];

		for ( int i = 0 ; i < numberOfBits ; i++ )
		{
			result = result << 1;
			if ( ( currentValue & readMask ) != 0 ) {
				result |= 1;
			}
			if ( readBit == 0 )
			{
				readPtr++;
				currentValue = buffer[ readPtr ];
				readBit = 63;
				readMask = 1L << 63;
			} else {
				readBit--;
				readMask = readMask >>> 1;
			}
		}
		this.readBit = readBit;
		this.readPtr = readPtr;
		return result;
	}

	private void resizeBuffer()
	{
		final long[] newBuffer = new long[ buffer.length * 2];
		System.arraycopy( this.buffer , 0 , newBuffer , 0 , this.buffer.length );
		buffer = newBuffer;
	}
}