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

	public BitStream(int sizeInBytes)
	{
		if ( sizeInBytes < 1 ) {
			throw new RuntimeException("Internal error,size must be >= 1");
		}
		final int sizeInWords = (int) Math.ceil( sizeInBytes / 8 );
		this.buffer = new long[ sizeInWords ];
	}

	public static void main(String[] args) throws EOFException {

		final int BUFFER_SIZE = 1024;
		final int KB_TO_WRITE = 2048;

		final byte[] data = new byte[BUFFER_SIZE];

		final Random rnd = new Random(0xdeadbeef);
		rnd.nextBytes( data );

		final BitStream buffer = new BitStream( (KB_TO_WRITE+1)*1024 );


		long t = benchmark( () ->
		{
			buffer.reset();
			for ( int i = 0 ; i < KB_TO_WRITE; i++ )
			{
				for ( final long b : data )
				{
					buffer.output( b & 0xffL , 8 );
				}
			}
		});
		float dataPerSecond = (KB_TO_WRITE/1024f) / (t/1000f);
		System.out.println("\nWrote "+KB_TO_WRITE+" in "+t+" ms ("+dataPerSecond+" MB/s)");

		/*
		 * Sanity check
		 */
		buffer.reset();
		for ( int i = 0 ; i < data.length ; i++ )
		{
			final long expected = data[i] & 0xffL;
			final long actual = buffer.read( 8 );
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
					sum += buffer.read( 8 );
				}
			}
			System.out.print("sum: "+sum);
		});
		dataPerSecond = (KB_TO_WRITE/1024f) / (t/1000f);
		System.out.println("\n\nRead "+KB_TO_WRITE+" in "+t+" ms ("+dataPerSecond+" MB/s)");
	}

	protected  static final long benchmark(Runnable r)
	{
		for ( int warmup = 40 ; warmup > 0 ; warmup --)
		{
			System.out.println("Warumup: "+time(r)+" ms");
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

	public void output(final long value,final int numberOfBits)
	{
		long readMask = 1L << (numberOfBits-1);
		long setMask = (1L << writeBit);

		long currentValue = buffer[ writePtr ];
		for ( int i = 0 ; i < numberOfBits ; i++ )
		{
			if ( (value & readMask ) != 0 ) {
				currentValue |= setMask;
			}
			readMask = readMask >>> 1;
			writeBit--;
			if ( writeBit < 0 )
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
				setMask  =  setMask >>> 1;
			}
		}
		buffer[writePtr] = currentValue;
	}

	public void reset() {
		this.readBit = 63;
		this.readPtr = 0;
		this.writeBit = 63;
		this.writePtr = 0;
	}

	public long read(final int numberOfBits)
	{
		long result = 0;
		long readMask = 1L << readBit;
		for ( int i = 0 ; i < numberOfBits ; i++ )
		{
			result = result << 1;
			if ( ( buffer[ readPtr ] & readMask ) != 0 ) {
				result |= 1;
			}
			readBit--;
			if ( readBit < 0 )
			{
				readPtr++;
				if ( writePtr == buffer.length ) {
					throw new RuntimeException("Trying to read beyond end of buffer ?");
				}
				readBit = 63;
				readMask = 1L << 63;
			} else {
				readMask = readMask >>> 1;
			}
		}
		return result;
	}

	private void resizeBuffer()
	{
		final long[] newBuffer = new long[ buffer.length * 2];
		System.arraycopy( this.buffer , 0 , newBuffer , 0 , this.buffer.length );
		buffer = newBuffer;
		System.out.println("resized buffer to "+buffer.length);
	}
}