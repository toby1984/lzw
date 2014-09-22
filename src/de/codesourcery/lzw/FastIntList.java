package de.codesourcery.lzw;


public final class FastIntList {

	public int[] data;
	public int length;

	private int maxSize ;

	public FastIntList(int initialSize)
	{
		this.data = new int[initialSize];
		this.maxSize = initialSize;
	}

	public void resize()
	{
		final int newSize = this.maxSize*2;
		System.out.println("FastIntList resize: "+this.maxSize+" -> "+newSize);
		final int[] data = new int[ newSize ];
		System.arraycopy( this.data , 0 , data , 0 , this.data.length );
		this.data = data;
		this.maxSize = newSize;
	}

	public void append(int value)
	{
		data[length++]=value;
		if ( length >= maxSize ) {
			resize();
		}
	}

	public void insert(int index,int value)
	{
		if ( index < length )
		{
			System.arraycopy( this.data , index , this.data , index+1 , length - index );
			data[index] = value;
		} else {
			data[index] = value;
		}
		length++;
		if ( length >= maxSize ) {
			resize();
		}
	}

	public void clear() {
		this.length = 0;
	}
}
