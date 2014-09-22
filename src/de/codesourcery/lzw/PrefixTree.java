package de.codesourcery.lzw;


public class PrefixTree {

	private static final int INITIAL_CHILD_ARRAY_SIZE = 300;

	private static final int BINARY_SEARCH_CUTOFF = 40;

	private static final boolean USE_BINARY_SEARCH = false;

	private byte[] suffixes;
	private int[] values;
	private FastIntList[] children;

	private int maxNodeCount;
	private int nodeCount;

	public static void main(String[] args) {

		final PrefixTree tree = new PrefixTree(1024);

		final byte[] data1 = { 1 , 2 , 3 };
		final byte[] data2 = { 1 , 3 , 4 };
		final byte[] data3 = { 1 , 2 , 4 };

		final byte[] data4 = { 1 , 2 , 5 };
		final byte[] data5 = { 1 , 2 , 3 , 4 };

		tree.put( data1 , 1 );
		tree.put( data2 , 2 );
		tree.put( data3 , 3 );

		int value = tree.lookup( data1 ,  data1.length );
		if ( value != 1 ) {
			throw new IllegalArgumentException("Expected 1 but got "+value);
		}

		value = tree.lookup( data2 ,  data2.length );
		if ( value != 2 ) {
			throw new IllegalArgumentException("Expected 2 but got "+value);
		}

		value = tree.lookup( data3 ,  data3.length );
		if ( value != 3 ) {
			throw new IllegalArgumentException("Expected 3 but got "+value);
		}

		value = tree.lookup( data4 ,  data4.length );
		if ( value != -1 ) {
			throw new IllegalArgumentException("Expected -1 but got "+value);
		}

		value = tree.lookup( data5 ,  data5.length );
		if ( value != -1 ) {
			throw new IllegalArgumentException("Expected -1 but got "+value);
		}
		System.out.println("All tests passed");
	}

	public PrefixTree(int nodeCount)
	{
		suffixes = new byte[ nodeCount ];
		values = new int[ nodeCount ];
		children = new FastIntList[ nodeCount ];
		for ( int i = 0 ; i < nodeCount ; i++ )
		{
			suffixes[i] = 0;
			values[i] = -1;
			children[i] = new FastIntList(INITIAL_CHILD_ARRAY_SIZE);
		}
		this.maxNodeCount = nodeCount;
		this.nodeCount = 1; // root node occupies first slot
	}

	private void resize()
	{
		final int newSize = maxNodeCount * 2;

		final byte[] suffixes = new byte[ newSize ];
		System.arraycopy( this.suffixes , 0 , suffixes , 0 , maxNodeCount );

		final int[] values = new int[ newSize ];
		System.arraycopy( this.values , 0 , values , 0 , maxNodeCount );

		final FastIntList[] children = new FastIntList[ newSize ];
		System.arraycopy( this.children , 0 , children , 0 , maxNodeCount );

		for ( int i = maxNodeCount ; i < newSize ; i++ )
		{
			suffixes[i] = 0;
			values[i] = -1;
			children[i] = new FastIntList(INITIAL_CHILD_ARRAY_SIZE);
		}

		this.suffixes = suffixes;
		this.values = values;
		this.children = children;
		this.maxNodeCount = newSize;
	}

	public int allocNode()
	{
		final int result = nodeCount++;
		if ( result >= maxNodeCount ) {
			resize();
		}
		return result;
	}

	public void put(byte[] pattern,int nodeValue) {
		put(pattern,pattern.length , nodeValue );
	}

	public void put(byte[] pattern,int len, int nodeValue)
	{
		if ( USE_BINARY_SEARCH) {
			putWithSorting(pattern, len, nodeValue);
		} else {
			putWithoutSorting(pattern,len,nodeValue);
		}
	}

	public void putWithoutSorting(byte[] pattern,int len, int nodeValue)
	{
		int offset = 0;
		int currentNode = 0;

		while ( offset < len)
		{
			int nextNode = -1;
			final byte currentValue = pattern[offset];
			final int[] children = this.children[ currentNode ].data;
			final int childCount = this.children[ currentNode ].length;
			for (int i = 0; i < childCount ; i++)
			{
				final int child = children[i];
				if ( suffixes[child] == currentValue )
				{
					nextNode = child;
					break;
				}
			}

			if ( nextNode == -1 )
			{
				nextNode = allocNode();
				this.suffixes[nextNode] = currentValue;
				this.children[currentNode].append( nextNode );
			}
			offset++;
			currentNode = nextNode;
		}
		this.values[currentNode] = nodeValue;
	}

	public void putWithSorting(byte[] pattern,int len, int nodeValue)
	{
		int offset = 0;
		int currentNode = 0;

		while ( offset < len)
		{
			int nextNode = -1;
			final byte currentValue = pattern[offset];
			final int[] children = this.children[ currentNode ].data;
			final int childCount = this.children[ currentNode ].length;
			int insertionPoint = 0;
			for (int i = 0; i < childCount ; i++)
			{
				final int child = children[i];
				final int childSuffix = suffixes[child];

				if ( childSuffix < currentValue ) {
					insertionPoint++;
				}
				else if ( childSuffix == currentValue )
				{
					nextNode = child;
					break;
				}
			}

			if ( nextNode == -1 )
			{
				nextNode = allocNode();
				this.suffixes[nextNode] = currentValue;
				this.children[currentNode].insert( insertionPoint , nextNode );
			}
			offset++;
			currentNode = nextNode;
		}
		this.values[currentNode] = nodeValue;
	}

	private int bruteForceGetChildIndex(FastIntList children, byte suffix)
	{
		final int len = children.length;
		final int[] dataArray = children.data;
		final byte[] suffixArray = this.suffixes;

		for ( int i = 0 ; i < len ; i++ )
		{
			final int childIdx = dataArray[i];
			if ( suffixArray[childIdx] == suffix ) {
				return childIdx;
			}
		}
		return -1;
	}

	private int binaryGetChildIndex(FastIntList children, byte suffix)
	{
		final int len = children.length;
		final int[] dataArray = children.data;
		final byte[] suffixArray = this.suffixes;

		if ( len < BINARY_SEARCH_CUTOFF )
		{
			for ( int i = 0 ; i < len ; i++ )
			{
				final int childIdx = dataArray[i];
				if ( suffixArray[childIdx] == suffix ) {
					return childIdx;
				}
			}
			return -1;
		}

		int start = 0;
		int end = len-1;
		while ( start <= end )
		{
			final int pivot = start + ( ( end - start ) / 2 );
			final int childIdx = dataArray[pivot];
			final byte currentValue = suffixArray[childIdx];

			if ( currentValue < suffix )
			{
				start = pivot+1;// search upper half
			}
			else if ( currentValue > suffix )
			{
				end = pivot-1;  // search lower half
			} else {
				return childIdx;
			}
		}
		return -1;
	}

	public int lookup(byte[] pattern) {
		return lookup(pattern,pattern.length);
	}

	public int lookup(byte[] pattern,int len)
	{
		int offset = 0;

		int currentNode = 0;
		while ( offset < len )
		{
			final byte currentValue = pattern[offset];

			final FastIntList fastIntList = this.children[ currentNode ];
			final int childIdx;
			if ( USE_BINARY_SEARCH ) {
				childIdx = binaryGetChildIndex( fastIntList , currentValue );
			} else {
				childIdx = bruteForceGetChildIndex( fastIntList , currentValue );
			}
			if ( childIdx == -1 )
			{
				return -1;
			}
			currentNode = childIdx;
			offset++;
		}
		return values[currentNode];
	}

	public void clear()
	{
		for ( int i = 0 ; i < nodeCount ; i++ )
		{
			suffixes[i] = 0;
			values[i] = -1;
			children[i].clear();
		}
		this.nodeCount = 1; // root node
	}
}