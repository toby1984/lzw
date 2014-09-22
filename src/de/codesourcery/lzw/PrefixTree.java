package de.codesourcery.lzw;

import java.util.ArrayList;
import java.util.List;

public class PrefixTree {

	public static void main(String[] args) {


		final PrefixTree tree = new PrefixTree();

		final byte[] data1 = { 1 , 2 , 3 };
		final byte[] data2 = { 1 , 3 , 4 };
		final byte[] data3 = { 1 , 2 , 4 };

		final byte[] data4 = { 1 , 2 , 5 };
		final byte[] data5 = { 1 , 2 , 3 , 4 };

		tree.put( data1 , 1 );
		tree.put( data2 , 2 );
		tree.put( data3 , 3 );

		int value = tree.lookup( data1 ,  data1.length );
		if ( value != 1 ) throw new IllegalArgumentException("Expected 1 but got "+value);

		value = tree.lookup( data2 ,  data2.length );
		if ( value != 2 ) throw new IllegalArgumentException("Expected 2 but got "+value);

		value = tree.lookup( data3 ,  data3.length );
		if ( value != 3 ) throw new IllegalArgumentException("Expected 3 but got "+value);

		value = tree.lookup( data4 ,  data4.length );
		if ( value != -1 ) throw new IllegalArgumentException("Expected -1 but got "+value);

		value = tree.lookup( data5 ,  data5.length );
		if ( value != -1 ) throw new IllegalArgumentException("Expected -1 but got "+value);
	}

	public static class PrefixNode
	{
		public byte suffix;
		public int value = -1;
		public final List<PrefixNode> children = new ArrayList<>();

		public PrefixNode(byte value) {
			this.suffix = value;
		}

		public PrefixNode put(byte[] pattern,int offset,int len)
		{
			if ( offset >= len ) {
				return this;
			}

			PrefixNode nextNode = null;

			final byte currentValue = pattern[offset];
			for ( final PrefixNode child : children ) {
				if ( child.suffix == currentValue )
				{
					nextNode = child;
					break;
				}
			}
			if ( nextNode == null ) {
				nextNode = new PrefixNode( currentValue );
				System.out.println("Adding "+nextNode+" to "+this);
				children.add( nextNode );
			}
			return nextNode.put( pattern , offset+1 , len );
		}

		public PrefixNode lookup(byte[] pattern,int offset,int len)
		{
			if ( offset >= len ) {
				return this;
			}
			final byte currentValue = pattern[offset];
			for ( final PrefixNode child : children ) {
				if ( child.suffix == currentValue ) {
					return child.lookup( pattern  , offset + 1 , len );
				}
			}
			return null;
		}

		@Override
		public String toString() {
			return "Node( "+this.suffix+") = "+value;
		}
	}

	private PrefixNode root=new PrefixNode((byte)0);

	public void put(byte[] pattern,int nodeValue) {
		put(pattern,pattern.length , nodeValue );
	}

	public void put(byte[] pattern,int len, int nodeValue)
	{
		root.put( pattern, 0 , len ).value = nodeValue;
	}

	public int lookup(byte[] pattern) {
		return lookup(pattern,pattern.length);
	}

	public int lookup(byte[] pattern,int len) {
		final PrefixNode node = root.lookup( pattern, 0 , len );
		return node == null ? -1 : node.value;
	}

	public void clear() {
		root=new PrefixNode((byte)0);
	}
}