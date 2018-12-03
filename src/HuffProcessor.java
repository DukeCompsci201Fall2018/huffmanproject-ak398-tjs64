import java.util.PriorityQueue;

/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Owen Astrachan
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); 
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;

	private final int myDebugLevel;
	
	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;
	
	public HuffProcessor() {
		this(0);
	}
	
	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out){
		int[] counts = readForCounts(in);
		HuffNode root = makeTreeFromCounts(counts);
		String[] codings = makeCodingsFromTree(root);
		
		out.writeBits(BITS_PER_INT, HUFF_TREE);
		writeHeader(root,out);
		
		in.reset();
		writeCompressedBits(codings,in,out);
		out.close();

//		while (true){
//			int val = in.readBits(BITS_PER_WORD);
//			if (val == -1) break;
//			out.writeBits(BITS_PER_WORD, val);
//		}
//		out.close();
	}
	
	/**
	 * Determine the frequency of every 8-bit character/chunk in the file
	 * being compressed.
	 * 
	 * @param in
	 * @return
	 */
	public int[] readForCounts(BitInputStream in) {
		int[] freq = new int[ALPH_SIZE +1];
		freq[PSEUDO_EOF] = 1;
		
		while(true) {
			int val = in.readBits(BITS_PER_WORD);
			if (val == -1) break;
			freq[val] += 1;
		}
		
		return freq;
	}
	
	/**
	 * From the frequencies, create the Huffman trie/tree used to create 
	 * encodings.
	 * 
	 * @param counts
	 * @return
	 */
	public HuffNode makeTreeFromCounts(int[] counts) {
		PriorityQueue<HuffNode> pq = new PriorityQueue<>();
		
		for (int k=0; k < counts.length; k += 1) {
			if (counts[k] > 0) {
				pq.add(new HuffNode(k,counts[k],null,null));
			}
		}
		
		while (pq.size() > 1) {
			HuffNode left = pq.remove();
			HuffNode right = pq.remove();
			HuffNode t = new HuffNode(0,left.myWeight + right.myWeight,left,right);
			pq.add(t);
		}
		HuffNode root = pq.remove();
		return root;
	}
	
	/**
	 * From the trie/tree, create the encodings for each 8-bit character.
	 * 
	 * @param root
	 * @return
	 */
	public String[] makeCodingsFromTree(HuffNode root) {
		String[] codings = new String[ALPH_SIZE +1];
		doPaths(codings,root,"");
		return codings;
	}
	
	/**
	 * Recursive helper method for makeCodingsFromTree method.
	 * 
	 * @param codings
	 * @param root
	 * @param path
	 */
	private void doPaths(String[] codings, HuffNode root, String path) {
		if (root == null) return;
		if (root.myLeft == null && root.myRight == null) {
			codings[root.myValue]= path;
			return;
		}
		doPaths(codings,root.myLeft,path+"0");
		doPaths(codings,root.myRight,path+"1");
	}
	
	/**
	 * Write magic number and the tree to the beginning/header of the
	 * compressed file.
	 * 
	 * @param root
	 * @param out
	 */
	public void writeHeader(HuffNode root, BitOutputStream out) {
		if (root.myLeft != null || root.myRight != null) {
			out.writeBits(1, 0);
			writeHeader(root.myLeft, out);
			writeHeader(root.myRight, out);
		}
		else if (root.myLeft == null && root.myRight == null) {
			out.writeBits(1, 1);
			out.writeBits(BITS_PER_WORD + 1, root.myValue);
			return;
		}
		return;
	}
	
	/**
	 * Write the encoding for each 8-bit chunk, followed by the encoding
	 * for PSEUDO_EOF.
	 * 
	 * @param codings
	 * @param in
	 * @param out
	 */
	public void writeCompressedBits(String[] codings, BitInputStream in, BitOutputStream out) {
		while (true) {
			int val = in.readBits(BITS_PER_WORD);
			if (val == -1) break;
			String code = codings[val];
			out.writeBits(code.length(), Integer.parseInt(code,2));
		}
		String pseudoCode = codings[PSEUDO_EOF];
		out.writeBits(pseudoCode.length(), Integer.parseInt(pseudoCode,2));
	}
	
	
	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out){
		int bits = in.readBits(BITS_PER_INT);
		if (bits != HUFF_TREE) {
			throw new HuffException("illegal header starts with "+bits);
		}
		
		HuffNode root = readTreeHeader(in);
		readCompressedBits(root,in,out);
		out.close();

//		while (true){
//			int val = in.readBits(BITS_PER_WORD);
//			if (val == -1) break;
//			out.writeBits(BITS_PER_WORD, val);
//		}
//		out.close();
	}
	
	/**
	 * Read the tree used to decompress (this is the tree written during compression).
	 * 
	 * @param in
	 * 			  Buffered bit stream of the file to be decompressed
	 * @return a HuffNode that is the root of the tree written during compression
	 */
	public HuffNode readTreeHeader(BitInputStream in) {
		int bit = in.readBits(1);
		if (bit == -1)
			throw new HuffException("bad input, no PSEUDO_EOF");
		if (bit == 0) {
			HuffNode left = readTreeHeader(in);
			HuffNode right = readTreeHeader(in);
			return new HuffNode(0,0,left,right);
		}
		else {
			int val = in.readBits(BITS_PER_WORD + 1);
			return new HuffNode(val,0,null,null);
		}
	}
	
	/**
	 * Read the bits from the compressed file and use them to traverse root-to-leaf
	 * paths, writing leaf values to the output file. Stop when finding PSEUDO_EOF.
	 * 
	 * @param root
	 * 			  HuffNode root of the tree that is being read
	 * @param in
	 * 			  Buffered bit stream of the file to be decompressed
	 * @param out
	 * 			  Buffered bit stream writing to the output file
	 */
	public void readCompressedBits(HuffNode root, BitInputStream in, BitOutputStream out) {
		HuffNode current = root;
		while (true) {
			int bits = in.readBits(1);
			if (bits == -1)
				throw new HuffException("bad input, no PSEUDO_EOF");
			else {
				if (bits == 0 && current.myLeft != null) current = current.myLeft;
				else 
					if (current.myRight != null) current = current.myRight;
				
				if (current.myLeft == null && current.myRight == null) {
					if (current.myValue == PSEUDO_EOF) break;
					else {
						out.writeBits(BITS_PER_WORD, current.myValue);
						current = root;		// start back after leaf
					}
				}
			}
		}
	}
	
}