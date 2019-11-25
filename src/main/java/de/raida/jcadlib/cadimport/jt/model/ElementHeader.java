//################################################################################
//	The MIT License
//
//	Copyright (c) 2014 Johannes Raida
//
//	Permission is hereby granted, free of charge, to any person obtaining a copy
//	of this software and associated documentation files (the "Software"), to deal
//	in the Software without restriction, including without limitation the rights
//	to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
//	copies of the Software, and to permit persons to whom the Software is
//	furnished to do so, subject to the following conditions:
//
//	The above copyright notice and this permission notice shall be included in
//	all copies or substantial portions of the Software.
//
//	THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
//	IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
//	FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
//	AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
//	LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
//	OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
//	THE SOFTWARE.
//################################################################################

package de.raida.jcadlib.cadimport.jt.model;

import java.nio.ByteBuffer;

import de.raida.jcadlib.cadimport.jt.JTImporter;
import de.raida.jcadlib.cadimport.jt.reader.Helper;
import de.raida.jcadlib.cadimport.jt.reader.WorkingContext;

/**
 * <h>7.1.3.2.1 Element Header</h>
 * Element Header contains data defining the length in bytes of the Element along
 * with information describing the object type contained in the Element.
 * <h>7.1.3.2.2 Element Header ZLIB</h>
 * Element Header ZLIB data collection is the format of Element Header data used by
 * all Elements within Segment Types that support ZLIB compression on all data in
 * the Segment.
 * <br>(c) 2014 by <a href="mailto:j.raida@gmx.net">Johannes Raida</a>
 * @author  <a href="mailto:j.raida@gmx.net">Johannes Raida</a>
 * @version 1.0
 */
public class ElementHeader {
	/** Element length */
	private int _elementLength;

	/** Element ID */
	private GUID _elementID;

	/** Byte buffer to read from */
	private ByteBuffer _byteBuffer;

	/** Length of the compressed data */
	private int _compressedDataLength;

	/** Length of the compressed data */
	private int _elementBaseType;

	/**
	 * Constructor for uncompressed elements.
	 * @param elementLength   Element length
	 * @param elementID       Element ID
	 * @param elementBaseType Element type
	 * @param byteBuffer      Byte buffer to read from
	 */
	public ElementHeader(int elementLength, GUID elementID, int elementBaseType, ByteBuffer byteBuffer){
		JTImporter.updateProgress(elementLength);

		_elementLength = elementLength;
		_elementID = elementID;
		_elementBaseType = elementBaseType;
		_byteBuffer = byteBuffer;
	}

	/**
	 * Constructor for compressed elements.
	 * @param elementLength        Element length
	 * @param elementID            Element ID
	 * @param elementBaseType      Element type
	 * @param compressionFlag      Compression flag
	 * @param compressionAlgorithm Compression algorithm
	 * @param compressedDataLength Length of the compressed data
	 * @param byteBuffer           Byte buffer to read from
	 */
	public ElementHeader(int elementLength, GUID elementID, int elementBaseType, long compressionFlag, int compressionAlgorithm, int compressedDataLength, ByteBuffer byteBuffer){
		JTImporter.updateProgress(compressedDataLength);

		_elementLength = elementLength;
		_elementID = elementID;
		_elementBaseType = elementBaseType;

		_compressedDataLength = compressedDataLength;
		_byteBuffer = byteBuffer;
	}

	/**
	 * Returns the ID of the element.
	 * @return ID of the element
	 */
	public GUID getElementID(){
		return _elementID;
	}

	/**
	 * Returns the length of the element.
	 * @return Length of the element
	 */
	public int getElementLength(){
		return _elementLength;
	}

	/**
	 * Returns the length of the compressed data.
	 * @return Length of the compressed data
	 */
	public int getCompressedDataLength(){
		return _compressedDataLength;
	}

	/**
	 * Returns the byte buffer of the element.
	 * @return Byte buffer of the element
	 */
	public ByteBuffer getByteBuffer(){
		return _byteBuffer;
	}

	public static ElementHeader readLSGElement(WorkingContext workingContext) {
		ByteBuffer byteBuffer = workingContext.getByteBuffer();

		// Read uncompressed ElementHeader
		int elemLength = Helper.readI32(byteBuffer);
		GUID objType = GUID.read(byteBuffer);
		int objBaseType = Helper.readU8(byteBuffer);
		return new ElementHeader(elemLength, objType, objBaseType, byteBuffer);
	}

	/**
	 * Reads an element header.
	 * @param  workingContext Working context
	 * @return                ElementHeader instance
	 */
	public static ElementHeader readRoot(WorkingContext workingContext) {
		ByteBuffer byteBuffer = workingContext.getByteBuffer();

		if( !workingContext.getSegmentType().isZipped()) {
			return readLSGElement(workingContext);
		}

		// Read compressed ElementHeader
		int compressionFlag = Helper.readI32(byteBuffer);
		int compressedDataLength = Helper.readI32(byteBuffer) - 1;	// Remove one byte for the compression algorithm
		int compressionAlgorithm = Helper.readU8(byteBuffer);

		boolean zlibCompressed = compressionFlag == 2 && compressionAlgorithm==2;
		boolean lzmaCompressed = compressionFlag == 3 && compressionAlgorithm==3;

		if ( zlibCompressed || lzmaCompressed ) {

            // Uncompress ElementHeader and data section
            byte[] compressedBytes = new byte[compressedDataLength];
            byteBuffer.get(compressedBytes);
            byte[] plaintextBytes;

            if ( zlibCompressed ) {
                plaintextBytes = Helper.decompressByZLIB(compressedBytes);
            }
            else {
                plaintextBytes = Helper.decompressByLZMA(compressedBytes);
            }

            ByteBuffer plaintextBuffer = ByteBuffer.wrap(plaintextBytes);
            plaintextBuffer.order(byteBuffer.order());

            int elementLength = Helper.readI32(plaintextBuffer);
            GUID objType = GUID.read(plaintextBuffer);
            int elementBaseType = Helper.readU8(plaintextBuffer);

            return new ElementHeader( elementLength,
                    objType,
                    elementBaseType,
                    compressionFlag,
                    compressionAlgorithm,
                    compressedDataLength,
                    plaintextBuffer);
        }
        else { // Compression disabled
            int elemLength = Helper.readI32(byteBuffer);
            GUID objType = GUID.read(byteBuffer);
            int objBaseType = Helper.readU8(byteBuffer);
            return new ElementHeader(elemLength, objType, objBaseType, byteBuffer);
		}
	}
}
