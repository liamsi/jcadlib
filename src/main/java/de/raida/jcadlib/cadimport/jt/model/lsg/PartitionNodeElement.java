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

package de.raida.jcadlib.cadimport.jt.model.lsg;

import java.nio.ByteBuffer;

import de.raida.jcadlib.cadimport.jt.model.LSGNode;
import de.raida.jcadlib.cadimport.jt.reader.Helper;
import de.raida.jcadlib.cadimport.jt.reader.WorkingContext;

/**
 * <h>7.2.1.1.1.2 Partition Node Element</h>
 * Object Type ID: <code>0x10dd103e, 0x2ac8, 0x11d1, 0x9b, 0x6b, 0x00, 0x80, 0xc7, 0xbb, 0x59, 0x97</code>
 * <br>A Partition Node represents an external JT file reference and provides a means to partition a model
 * into multiple physical JT files (e.g. separate JT file per part in an assembly). When the referenced JT
 * file is opened, the Partition Node's children are really the children of the LSG root node for the
 * underlying JT file. Usage of Partition Nodes in LSG also aids in supporting JT file loader/reader 'best
 * practice' of late loading data (i.e. can delay opening and loading the externally referenced JT file until
 * the data is needed).
 * <br>(c) 2014 by <a href="mailto:j.raida@gmx.net">Johannes Raida</a>
 * @author  <a href="mailto:j.raida@gmx.net">Johannes Raida</a>
 * @version 1.0
 */
public class PartitionNodeElement extends LSGNode {
	/** Object type ID */
	public final static String ID = "10dd103e-2ac8-11d1-9b-6b-0-80-c7-bb-59-97";

	/** Group node data */
	private GroupNodeData _groupNodeData;

	/** Partition flags */
	private int _partitionFlags;

	/** File name */
	private String _fileName;

	/** Bounding box */
	private float[][] _boundingBox;

	/** Area */
	private float _area;

	/** Vertex count range */
	private int[] _vertexCountRange;

	/** Node count range */
	private int[] _nodeCountRange;

	/** Polygon count range */
	private int[] _polygonCountRange;

	/** Untransformed bounding box */
	private float[][] _untransformedBoundingBox;

	/**
	 * Constructor.
	 * @param groupNodeData            Group node data
	 * @param partitionFlags           Partition flags
	 * @param fileName                 Name of referenced file
	 * @param boundingBox              Bounding box
	 * @param area                     Area
	 * @param vertexCountRange         Vertex count range
	 * @param nodeCountRange           Node count range
	 * @param polygonCountRange        Polygon count range
	 * @param untransformedBoundingBox Bounding box (untransformed)
	 */
	public PartitionNodeElement(GroupNodeData groupNodeData, int partitionFlags, String fileName, float[][] boundingBox, float area, int[] vertexCountRange, int[] nodeCountRange, int[] polygonCountRange, float[][] untransformedBoundingBox){
		_groupNodeData = groupNodeData;
		_partitionFlags = partitionFlags;
		_fileName = fileName;
		_boundingBox = boundingBox;
		_area = area;
		_vertexCountRange = vertexCountRange;
		_nodeCountRange = nodeCountRange;
		_polygonCountRange = polygonCountRange;
		_untransformedBoundingBox = untransformedBoundingBox;
	}

	/**
	 * Returns the object ID.
	 * @return Object ID
	 */
	public int getObjectID(){
		return _groupNodeData.getObjectID();
	}

	/**
	 * Returns the child node object IDs.
	 * @return Child node object IDs
	 */
	public int[] getChildNodeObjectIDs(){
		return _groupNodeData.getChildNodeObjectIDs();
	}

	/**
	 * Returns the attribute object IDs.
	 * @return Attribute object IDs
	 */
	public int[] getAttributeObjectIDs(){
		return _groupNodeData.getAttributeObjectIDs();
	}

	/**
	 * Returns the file name.
	 * @return File name
	 */
	public String getFileName(){
		return _fileName;
	}

	public float[][] getBoundingBox() {
		return _boundingBox;
	}

	public float[][] getRawBoundingBox() {
		return _untransformedBoundingBox;
	}

	/**
	 * Returns a copy of the current class.
	 * @param  lsgNode Parent node
	 * @return         Copy of the current class
	 */
	public LSGNode copy(LSGNode lsgNode){
		PartitionNodeElement partitionNodeElement = new PartitionNodeElement(_groupNodeData.copy(),
				_partitionFlags,
				_fileName,
				Helper.copy(_boundingBox),
				_area,
				Helper.copy(_vertexCountRange),
				Helper.copy(_nodeCountRange),
				Helper.copy(_polygonCountRange),
				Helper.copy(_untransformedBoundingBox));
		partitionNodeElement.setAttributeNodes(getAttributeNodes());
		partitionNodeElement.setPropertyNodes(getPropertyNodes());
		partitionNodeElement.setParentLSGNode(lsgNode);
		for(LSGNode childNode : getChildLSGNodes()){
			partitionNodeElement.addChildLSGNode(childNode.copy(partitionNodeElement));
		}
		return partitionNodeElement;
	}

	/**
	 * Reads a PartitionNodeElement object.
	 * @param  workingContext Working context
	 * @return                PartitionNodeElement instance
	 */
	public static PartitionNodeElement read(WorkingContext workingContext, int elementEnd){
		ByteBuffer byteBuffer = workingContext.getByteBuffer();

		GroupNodeData groupNodeData = GroupNodeData.read(workingContext);

		if ( workingContext.getJTFileVersion()>=10.0 ) {
			int versionNumber = Helper.readU8(byteBuffer);
			if(versionNumber > 10){
				throw new IllegalArgumentException("Found invalid version number: " + versionNumber);
			}
		}

		int partitionFlags = Helper.readI32(byteBuffer);
		String fileName = Helper.readMultiByteString(byteBuffer);

		float[][] boundingBox = Helper.readBBoxF32(byteBuffer);

		final boolean hasUnxformedBox = (partitionFlags & 0x1) != 0;

		float area = Helper.readF32(byteBuffer);
		int[] vertexCountRange = Helper.readRange(byteBuffer);
		int[] nodeCountRange = Helper.readRange(byteBuffer);
		int[] polygonCountRange = Helper.readRange(byteBuffer);

		float[][] untransformedBoundingBox = null;
		int bytesRemaining = (elementEnd-byteBuffer.position());
		if ( hasUnxformedBox && bytesRemaining>=24 ) {
			// Untransformed bounding box
			untransformedBoundingBox = Helper.readBBoxF32(byteBuffer);

			if ( untransformedBoundingBox[1][0] < untransformedBoundingBox[0][0]
            || untransformedBoundingBox[1][1] < untransformedBoundingBox[0][1]
            || untransformedBoundingBox[1][2] < untransformedBoundingBox[0][2] ) {
                //throw new IllegalArgumentException("Bad bounding box!");
				untransformedBoundingBox = null;
            }
		}

		return new PartitionNodeElement(groupNodeData,
										partitionFlags,
										fileName,
										boundingBox,
										area,
										vertexCountRange,
										nodeCountRange,
										polygonCountRange,
										untransformedBoundingBox);
	}

	@Override
	public String getTypeName() {
		return "Partition";
	}


}
