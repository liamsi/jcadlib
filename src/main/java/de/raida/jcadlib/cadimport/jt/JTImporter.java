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

package de.raida.jcadlib.cadimport.jt;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.DateTimeException;
import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.vecmath.Matrix4d;
import javax.vecmath.Point3d;
import javax.vecmath.Vector3d;

import de.raida.jcadlib.cadimport.jt.model.ElementHeader;
import de.raida.jcadlib.cadimport.jt.model.GUID;
import de.raida.jcadlib.cadimport.jt.model.JTNode;
import de.raida.jcadlib.cadimport.jt.model.LSGNode;
import de.raida.jcadlib.cadimport.jt.model.geometry.PointSetShapeLODElement;
import de.raida.jcadlib.cadimport.jt.model.geometry.PolylineSetShapeLODElement;
import de.raida.jcadlib.cadimport.jt.model.SegmentHeader;
import de.raida.jcadlib.cadimport.jt.model.TOCEntry;
import de.raida.jcadlib.cadimport.jt.model.geometry.TopoMeshCompressedLODData;
import de.raida.jcadlib.cadimport.jt.model.geometry.TopoMeshCompressedRepDataV1;
import de.raida.jcadlib.cadimport.jt.model.geometry.TriStripSetShapeLODElement;
import de.raida.jcadlib.cadimport.jt.model.geometry.VertexBasedShapeCompressedRepData;
import de.raida.jcadlib.cadimport.jt.model.geometry.VertexShapeLODData;
import de.raida.jcadlib.cadimport.jt.model.geometry.VertexShapeLODElement;
import de.raida.jcadlib.cadimport.jt.model.lsg.*;
import de.raida.jcadlib.cadimport.jt.model.precise.PMIMetaDataElement;
import de.raida.jcadlib.cadimport.jt.model.property.*;
import de.raida.jcadlib.cadimport.jt.reader.Helper;
import de.raida.jcadlib.cadimport.jt.reader.UnsupportedCodecException;
import de.raida.jcadlib.cadimport.jt.reader.WorkingContext;
import de.raida.progress.ProgressEvent;
import de.raida.progress.ProgressListenerInterface;



/**
 * Imports the JT file, creates the model and gives access to the models attributes.
 * <br>(c) 2014 by <a href="mailto:j.raida@gmx.net">Johannes Raida</a>
 * @author  <a href="mailto:j.raida@gmx.net">Johannes Raida</a>
 * @version 1.0
 */
public class JTImporter {


	/** Regular expression of the JT signature (version 8) */
	private static final String JT_SIGNATURE_REG_EXP_V8 = "Version (\\d\\.\\d)(.{40,}) {5}";

	/** Regular expression of the JT signature (version 9) */
	private static final String JT_SIGNATURE_REG_EXP_V9 = "Version (\\d\\.\\d)(.{40,}) \n\r\n ";

	/** Regular expression of the JT signature (version 10) */
	private static final String JT_SIGNATURE_REG_EXP_V10 = "Version (\\d{1,2}\\.\\d)(.{40,}) \n\r\n ";

	/** Color of the geometry */
	public final static Color DEFAULT_COLOR = Color.WHITE;

	/** Layer name */
	public final static String DEFAULT_LAYER = "0";

	/** List of load informations */
	private static List<String[]> _loadInformation;

	/** List of unsupported entities */
	private ArrayList<String> _unsupportedEntities;

	/** JT model */
	private JTModel _jtModel;

	/** List of progress listener */
	private static ArrayList<ProgressListenerInterface> _progressListener;

	/** Root node of the LSG */
	private LSGNode _rootNode;

	/** JT nodes, indices by their object ID's */
	private HashMap<Integer, JTNode> _jtNodes;

	/** List of all XSetShapeLODElements bytebuffer positions */
	private HashMap<String, Integer> _xSetShapeLODElements;

	/** List of all XSetShapeLODElements bytebuffer positions */
	private HashMap<String, Object> _segmentObjectTable;

	/** Property table */
	private PropertyTable _propertyTable;

	/** Base URL name */
	private static URL _baseURLName;

	/** Current URL name */
	private static URL _currentURLName;

	/** Length of file in bytes */
	private static HashMap<URL, Integer> _fileLength;

	/** Number of read bytes */
	private static HashMap<URL, Integer> _readBytes;

	/** Number of read bytes for progress intervall */
	private static HashMap<URL, Integer> _progressIntervall;

	/** Mapping of unsupported GUID's: GUID -> Name */
	private HashMap<String, String> _guidMapping;

    /** Limit work to only that needed for parsing out the BOM**/
    private boolean _skipGeometry = false;

    /** Limit work to only that needed for parsing out the BOM**/
    private boolean _skipSubPartitions = false;

    /** Toggles profiling output **/
    private boolean _profilingEnabled = false;

    /**
	 * Constructor.
	 */
	public JTImporter()
    {
	    _progressIntervall = new HashMap<>();
		_readBytes = new HashMap<>();
		_fileLength = new HashMap<>();

		_loadInformation = new ArrayList<>();
		_unsupportedEntities = new ArrayList<>();
		_jtNodes = new HashMap<>();
		_xSetShapeLODElements = new HashMap<>();
		_segmentObjectTable = new HashMap<>();
		_guidMapping = new HashMap<>();
		_guidMapping.put("873a70c0-2ac8-11d1-9b-6b-0-80-c7-bb-59-97", "JT B-Rep Element");
		_guidMapping.put("ce357249-38fb-11d1-a5-6-0-60-97-bd-c6-e1",  "PMI Manager Meta Data");
		_guidMapping.put("10dd1083-2ac8-11d1-9b-6b-0-80-c7-bb-59-97", "Geometric Transform Attribute Element");
		_guidMapping.put("10dd1073-2ac8-11d1-9b-6b-0-80-c7-bb-59-97", "Texture Image Attribute Element");
		_guidMapping.put("10dd1014-2ac8-11d1-9b-6b-0-80-c7-bb-59-97", "Draw Style Attribute Element");
		_guidMapping.put("10dd10c4-2ac8-11d1-9b-6b-0-80-c7-bb-59-97", "Linestyle Attribute Element");
		_guidMapping.put("ce357247-38fb-11d1-a5-6-0-60-97-bd-c6-e1",  "Property Proxy Meta Data Element");
		_guidMapping.put("873a70e0-2ac9-11d1-9b-6b-0-80-c7-bb-59-97", "XT B-Rep Element");
		_guidMapping.put("873a70d0-2ac8-11d1-9b-6b-0-80-c7-bb-59-97", "Wireframe Rep Element");
        _guidMapping.put("73-7802-b01-67-60-60-c8-13-b8-2b-70", "FIXME v9.5 extension?");
	}

    public void setSkipGeometry(boolean skip) {
        _skipGeometry = skip;
    }

    public void setSkipSubPartitions(boolean skip) {
        _skipSubPartitions = skip;
    }

    public LSGNode rootNode() {
	    return this._rootNode;
    }

	/**
	 * Imports the given file and creates the model.
	 * @param  fileName  Name of the file to load
	 * @throws Exception Thrown if something failed
	 */
	public void importFile(String fileName) throws Exception {
		File file = new File(fileName);
		if(!file.exists() || (file.length() == 0)){
			throw new Exception("ERROR: File '" + fileName + "' doesn't exists or is empty!");
		}
		loadFile(file.toURI().toURL());
	}

	/**
	 * Loads the given url and creates the model.
	 * @param  url       Name of the file to load
	 * @throws Exception Thrown if something failed
	 */
	public void loadFile(URL url) throws Exception {
		loadFile(url, false);
	}

	/**
	 * Parses the given file and creates the model.
	 * @param  url            URL of the file to load
	 * @param  referencedFile Is it a referenced file?
	 * @throws Exception      Thrown if something failed
	 */
	private void loadFile(URL url, boolean referencedFile) throws Exception {
		if(!referencedFile){
			_baseURLName = url;
		}

		long startTime = System.nanoTime();

		_currentURLName = url;
		_jtModel = new JTModel();

		_progressIntervall.put(_currentURLName, 0);
		_readBytes.put(_currentURLName, 0);
		_fileLength.put(_currentURLName, url.openConnection().getContentLength());

		if ( isProfilingEnabled() )
			startTime = logTimer("Initialization of loadFile", startTime);

		try ( InputStream inputStream = url.openStream() ) {

			ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(_fileLength.get(_currentURLName));
			byte[] buffer = new byte[1024];
			int readBytes;
			while((readBytes = inputStream.read(buffer)) != -1){
				byteArrayOutputStream.write(buffer, 0, readBytes);
			}
			ByteBuffer byteBuffer = ByteBuffer.wrap(byteArrayOutputStream.toByteArray());
			inputStream.close();
            if ( isProfilingEnabled() )
    			startTime = logTimer("Input file read", startTime);

			WorkingContext workingContext = new WorkingContext();
			workingContext.setByteBuffer(byteBuffer);

			// Check the signature
			String signature = Helper.readStringByLength(byteBuffer, 80);
			readVersionString(signature);

			workingContext.setJTFileVersion(_jtModel.getJTFileVersion());

			// Continue only if the major version is supported
			if((_jtModel.getJTFileVersion() < 8.0) || (_jtModel.getJTFileVersion() >= 11.0)){
				addLoadInformation("ERROR", "Found unsupported JT major version: " + _jtModel.getJTFileVersion());
                throw new Exception("Unsupported JT version: " + _jtModel.getJTFileVersion());
			}

			// Get the byte order (default of ByteBuffer is BIG_ENDIAN)
			if(Helper.readU8(byteBuffer) == 0){
				byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
			}

			// Reserved field
			int reservedField = Helper.readI32(byteBuffer);

			// TOC offset
			int tocOffset;
			if ( _jtModel.getJTFileVersion()>=10.0 ) {
				tocOffset = (int)Helper.readU64(byteBuffer);
			}
			else {
				tocOffset = Helper.readI32(byteBuffer);
			}

			// Read the segment ID of the root Logical Scene Graph (LSG)
			GUID rootLSG = GUID.read(workingContext);
			if(reservedField != 0){
				rootLSG = null;
			}

            readTOC(workingContext, rootLSG, byteBuffer, tocOffset);

            if ( isProfilingEnabled() )
    			startTime = logTimer("Load nodes", startTime);

			// Create the LSG tree
			createLSG(_rootNode.getObjectID(), _jtNodes);

            if ( isProfilingEnabled() )
    			startTime = logTimer("Create LSG", startTime);

			// Extract the geometry and fill the JTModel
			walkLSGTree(null, byteBuffer, workingContext);

            if ( isProfilingEnabled() )
    			logTimer("Walk the LSG", startTime);

		} catch(Exception exception){
			addLoadInformation("ERROR", exception.getMessage());
			throw exception;
		}
	}

	private void readTOC(WorkingContext workingContext, GUID rootLSG, ByteBuffer byteBuffer, int tocOffset) throws Exception {
        // Go to the TOC
        byteBuffer.position(tocOffset);

        // Get all TOC entries
        ArrayList<TOCEntry> tocEntries = new ArrayList<>();
        int tocEntryCount = Helper.readI32(byteBuffer);
        for(int i = 0; i < tocEntryCount; i++){
            tocEntries.add(TOCEntry.read(workingContext));
        }

        // Iterate over elements referenced from TOC
        for ( TOCEntry tocEntry : tocEntries ) {
            workingContext.setByteBuffer(byteBuffer);
            byteBuffer.position((int)tocEntry.getSegmentOffSet()); // Minor HACK:  int-cast of 64-bit value

            SegmentHeader segmentHeader = SegmentHeader.read(workingContext);

            String segmentID = segmentHeader.getSegmentID().toString();
            workingContext.setSegmentType(segmentHeader.getSegmentType());

            ElementHeader elementHeader = ElementHeader.readRoot(workingContext);
            String elementID = elementHeader.getElementID().toString();

            // Extract the geometry information
			switch (elementID) {
				case TriStripSetShapeLODElement.ID:
				case PointSetShapeLODElement.ID:
				case PolylineSetShapeLODElement.ID:
					// Store the position for later reading
					workingContext.setByteBuffer(elementHeader.getByteBuffer());
					_xSetShapeLODElements.put(segmentID, workingContext.getByteBuffer().position());
					break;

				case PartitionNodeElement.ID: {
					if ( segmentID.equals(rootLSG.toString()) && _rootNode!=null ) {
						// For some reason, certain JT files have the LSG segment TOC entry
						// duplicated.  If so, just ignore the second entry.
						break;
					}

					// Extract the assembly information
					workingContext.setByteBuffer(elementHeader.getByteBuffer());
					final int elementEnd = workingContext.getByteBuffer().position() + elementHeader.getElementLength() - 17;
					PartitionNodeElement y = PartitionNodeElement.read(workingContext, elementEnd);
					_jtNodes.put(y.getObjectID(), y);
					if (segmentID.equals(rootLSG.toString())) {
						_rootNode = y;
					}

					readPartitionElement(workingContext, y);
					break;
				}

				case PropertyMetaDataElement.ID: {
					// Store the object for later reading
					workingContext.setByteBuffer(elementHeader.getByteBuffer());
					PropertyMetaDataElement element = PropertyMetaDataElement.read(workingContext);
					_segmentObjectTable.put(segmentID, element);
					break;
				}

				case PMIMetaDataElement.ID: {
					// Store the object for later reading
					workingContext.setByteBuffer(elementHeader.getByteBuffer());
					PMIMetaDataElement element = PMIMetaDataElement.read(workingContext);
					_segmentObjectTable.put(segmentID, element);
					break;
				}

				// Skip unevaluated element
				default:
					addUnsupportedEntity(elementID);
					break;
			}
        } // for tocEntry ...
    }


    private void readPartitionElement(WorkingContext workingContext, PartitionNodeElement y) {
        // Read GraphElements
        //--------------------
        while (true) {
            final int beforeHeader = workingContext.getByteBuffer().position();
            ElementHeader elementHeader2 = ElementHeader.readLSGElement(workingContext);
            final int elementEnd = beforeHeader + elementHeader2.getElementLength() + 4;
            String elementID2 = elementHeader2.getElementID().toString();

            JTNode node = null;
            if (PartNodeElement.ID.equals(elementID2)) {
                node = PartNodeElement.read(workingContext);
            } else if (RangeLODNodeElement.ID.equals(elementID2)) {
                node = RangeLODNodeElement.read(workingContext);
            } else if (GroupNodeElement.ID.equals(elementID2)) {
                node = GroupNodeElement.read(workingContext);
            } else if (TriStripSetShapeNodeElement.ID.equals(elementID2)) {
                node = TriStripSetShapeNodeElement.read(workingContext);
            } else if (MaterialAttributeElement.ID.equals(elementID2)) {
                node = MaterialAttributeElement.read(workingContext);
            } else if (MetaDataNodeElement.ID.equals(elementID2)) {
                node = MetaDataNodeElement.read(workingContext);
            } else if (InstanceNodeElement.ID.equals(elementID2)) {
                node = InstanceNodeElement.read(workingContext);
            } else if (GeometricTransformAttributeElement.ID.equals(elementID2)) {
                node = GeometricTransformAttributeElement.read(workingContext, elementEnd);
            } else if (PartitionNodeElement.ID.equals(elementID2)) {
                node = PartitionNodeElement.read(workingContext, elementEnd);
            } else if (PolylineSetShapeNodeElement.ID.equals(elementID2)) {
                node = PolylineSetShapeNodeElement.read(workingContext);
            } else if (PointSetShapeNodeElement.ID.equals(elementID2)) {
                node = PointSetShapeNodeElement.read(workingContext);
            } else if (LineStyleAttributeElement.ID.equals(elementID2)) {
                node = LineStyleAttributeElement.read(workingContext);
            } else {
                // Skip unevaluated element
                addUnsupportedEntity(elementID2);
            }

            if ( node!=null && node.getObjectID()>=0 ) { // < 0 == NOT FULLY SUPPORTED TYPE
            	int objectId = node.getObjectID();
            	if ( _jtNodes.containsKey(objectId) ) {
            		throw new RuntimeException("Found duplicate ObjectId: " + node.getObjectID());
            	}
                _jtNodes.put(objectId, node);
            }

            // Check whether a next element is available
            workingContext.getByteBuffer().position(elementEnd);

            // Skip next elements length
            Helper.readI32(workingContext.getByteBuffer());

            // Possibly break endless loop
            GUID nextGUID = GUID.read(workingContext);
            if (nextGUID.toString().equals(GUID.END_OF_ELEMENTS)) {
                break;
            }

            workingContext.getByteBuffer().position(elementEnd);
        }

        // Read Property Atom Elements
        //-----------------------------
        while (true) {
            int beforeHeader = workingContext.getByteBuffer().position();
            ElementHeader elementHeader2 = ElementHeader.readLSGElement(workingContext);
            int headerSize = workingContext.getByteBuffer().position() - beforeHeader;
            String elementID2 = elementHeader2.getElementID().toString();

            JTNode element = null;
            if (StringPropertyAtomElement.ID.equals(elementID2)) {
                element = StringPropertyAtomElement.read(workingContext);
            } else if (Float32PropertyAtomElement.ID.equals(elementID2)) {
                element = Float32PropertyAtomElement.read(workingContext);
            } else if (LateLoadedPropertyAtomElement.ID.equals(elementID2)) {
                element = LateLoadedPropertyAtomElement.read(workingContext);
            } else if (DatePropertyAtomElement.ID.equals(elementID2)) {
				try {
					element = DatePropertyAtomElement.read(workingContext);
				} catch (DateTimeException dte) {
					addLoadInformation("WARNING", "At least 1 date property on file " + workingContext.getFileName() + " is invalid");
				}
            } else if (IntegerPropertyAtomElement.ID.equals(elementID2)) {
                element = IntegerPropertyAtomElement.read(workingContext);
            } else {
                // Skip unevaluated element
                addUnsupportedEntity(elementID2);
                int bytesToSkip = elementHeader2.getElementLength() - headerSize + 4;
                Helper.readBytes(workingContext.getByteBuffer(), bytesToSkip);
            }

            if ( element!=null ) {
                _jtNodes.put(element.getObjectID(), element);
            }

            // Check whether a next element is available
            int currentPosition = workingContext.getByteBuffer().position();
			int elementLength = elementHeader2.getElementLength();
			int bytesToSkip = (currentPosition - 4 - elementLength - beforeHeader) * -1;
			if (bytesToSkip > 0) {
				Helper.readBytes(workingContext.getByteBuffer(), bytesToSkip);
				currentPosition = workingContext.getByteBuffer().position();
            } else if ( bytesToSkip<0 ) {
                throw new RuntimeException("Invalid segment size: "+bytesToSkip);
            }

            // Skip next elements length
			Helper.readI32(workingContext.getByteBuffer());

            // Possibly break endless loop
            GUID nextGUID = GUID.read(workingContext);
            if (nextGUID.toString().equals(GUID.END_OF_ELEMENTS)) {
                break;
            }

            workingContext.getByteBuffer().position(currentPosition);
        }

        // Read Property Table
        //---------------------
        _propertyTable = PropertyTable.read(workingContext);
    }


	/**
	 * Create the tree structure.
	 * @param objectID LSG root node ID
	 * @param nodes    Map of all nodes (object ID -> node)
	 */
	private void createLSG(int objectID, HashMap<Integer, JTNode> nodes){
		LSGNode parentNode = (LSGNode)nodes.get(objectID);

		// Attach attributes to node
		for (int attributeObjectID : parentNode.getAttributeObjectIDs()) {
			JTNode attributeNode = nodes.get(attributeObjectID);
			parentNode.addAttributeNode(attributeNode);
		}

		// Attach properties to node
		NodePropertyTable nodePropertyTable = _propertyTable.getNodePropertyTable(objectID);
		if(nodePropertyTable != null) {
			HashMap<Integer, Integer> keyValuePairs = nodePropertyTable.getKeyValuePairs();
			for (Entry<Integer, Integer> entry : keyValuePairs.entrySet()) {
				JTNode kk = nodes.get(entry.getKey());
				JTNode vv = nodes.get(entry.getValue());
				if ( kk==null || vv==null ) {
					addLoadInformation("WARNING", "Ignoring missing prop key/value: "+entry.getKey()+"->"+entry.getValue());
					continue;
				}
				parentNode.addPropertyNode(kk, vv);
			}
		}

		parentNode.queryLLMetadataElement().ifPresent(ll->loadLLMetadata(parentNode,ll));

		// Attach child nodes to node
		for (int childNodeID : parentNode.getChildNodeObjectIDs()) {
			LSGNode childNode = (LSGNode) nodes.get(childNodeID);
			if (childNode == null) {
				addLoadInformation("WARNING", "Object " + parentNode.getObjectID() + " (" + parentNode.getClass().getName() + ") references a not existing / unsupported child node: " + childNodeID);
				continue;
			}

			// For multiple instances, add a copy of the sub tree
			if (childNode.getParentLSGNode() != null) {
				LSGNode clonedChildNode = childNode.copy(parentNode);
				parentNode.addChildLSGNode(clonedChildNode);
				break;
			}

			parentNode.addChildLSGNode(childNode);
			childNode.setParentLSGNode(parentNode);

			// Continue the recursion
			createLSG(childNodeID, nodes);
		}
	}

	private void loadLLMetadata(LSGNode node, LateLoadedPropertyAtomElement llPropElement) {
		PropertyMetaDataElement mdElement = (PropertyMetaDataElement)_segmentObjectTable.get(llPropElement.getSegmentID());
		node.setLLProperties(mdElement.getPropertyTable());
	}

	private void readVersionString(String signature)
	{
		String[] vStrings = new String[3];
		vStrings[0] = JT_SIGNATURE_REG_EXP_V8;
		vStrings[1] = JT_SIGNATURE_REG_EXP_V9;
		vStrings[2] = JT_SIGNATURE_REG_EXP_V10;

		String version = null;
		String comment = null;
		for ( String vstring : vStrings ) {
			Pattern vpattern = Pattern.compile(vstring);
			Matcher m = vpattern.matcher(signature);
			if (m.find()) {
				version = m.group(1);
				comment = m.group(2);
				break;
			}
		}

		// Extract some information from the valid signature
		_jtModel.setVersion(version);
		_jtModel.setComment(comment);

		addLoadInformation("JT File Version", version);
	}

	private static boolean isShapeNode(LSGNode node) {
        return ( ((node instanceof TriStripSetShapeNodeElement)
                || (node instanceof PolylineSetShapeNodeElement)
                || (node instanceof PointSetShapeNodeElement)) );
    }

    private static void xformVec3s(double[] vertices, Matrix4d transformation) {
		for (int i = 0; i < vertices.length; i += 3) {
			Point3d vertex = new Point3d(vertices[i], vertices[i + 1], vertices[i + 2]);
			transformation.transform(vertex);
			vertices[i] = vertex.getX();
			vertices[i + 1] = vertex.getY();
			vertices[i + 2] = vertex.getZ();
		}

	}

	/**
	 * Walks down the LSG and creates the geometry.
	 * @param lsgNode        LSG node to process
	 * @param byteBuffer     Byte buffer
	 * @param workingContext Working context
	 */
	private void walkLSGTree(LSGNode lsgNode, ByteBuffer byteBuffer, WorkingContext workingContext){
		if(lsgNode == null){
			lsgNode = _rootNode;
		}

		// Create local defined geometry
		if( !_skipGeometry && isShapeNode(lsgNode) ){
			// Get the color
			Color color = getColorFromParentNodes(lsgNode);

			// Get the transformation matrix
			Matrix4d transformation = getTransformationFromParentNodes(lsgNode);

			// Get the layer name
			String nodeName = getLayerName(lsgNode);

			// Get the geometry
			boolean foundLateLoadedPropertyAtomElement = false;
			for (Entry<JTNode, JTNode> entry : lsgNode.getPropertyNodes().entrySet()) {
				if (entry.getValue() instanceof LateLoadedPropertyAtomElement) {
					if (!foundLateLoadedPropertyAtomElement) {
						foundLateLoadedPropertyAtomElement = true;
						LateLoadedPropertyAtomElement lateLoadedPropertyAtomElement = (LateLoadedPropertyAtomElement) entry.getValue();
						String segmentID = lateLoadedPropertyAtomElement.getSegmentID();

						// Faces
						if (lsgNode instanceof TriStripSetShapeNodeElement) {
							int currentPosition = byteBuffer.position();
							byteBuffer.position(_xSetShapeLODElements.get(segmentID));
							TriStripSetShapeLODElement triStripSetShapeLODElement = null;
							try {
								workingContext.setByteBuffer(byteBuffer);
								triStripSetShapeLODElement = TriStripSetShapeLODElement.read(workingContext);

							} catch (UnsupportedCodecException exception) {
								addLoadInformation("WARNING", exception.getMessage());
							}
							byteBuffer.position(currentPosition);

							if (triStripSetShapeLODElement != null) {
								prepareGeometry(lsgNode.getObjectID(), triStripSetShapeLODElement, null, null, transformation, color, nodeName);
							}

							// Polylines
						} else if (lsgNode instanceof PolylineSetShapeNodeElement) {
							int currentPosition = byteBuffer.position();
							byteBuffer.position(_xSetShapeLODElements.get(segmentID));
							PolylineSetShapeLODElement polylineSetShapeLODElement = null;
							try {
								workingContext.setByteBuffer(byteBuffer);
								polylineSetShapeLODElement = PolylineSetShapeLODElement.read(workingContext);

							} catch (UnsupportedCodecException exception) {
								addLoadInformation("WARNING", exception.getMessage());
							}
							byteBuffer.position(currentPosition);

							if (polylineSetShapeLODElement != null) {
								prepareGeometry(lsgNode.getObjectID(), null, polylineSetShapeLODElement, null, transformation, color, nodeName);
							}

							// Points
						} else if (lsgNode instanceof PointSetShapeNodeElement) {
							int currentPosition = byteBuffer.position();
							byteBuffer.position(_xSetShapeLODElements.get(segmentID));
							PointSetShapeLODElement pointSetShapeLODElement = null;
							try {
								workingContext.setByteBuffer(byteBuffer);
								pointSetShapeLODElement = PointSetShapeLODElement.read(workingContext);

							} catch (UnsupportedCodecException exception) {
								addLoadInformation("WARNING", exception.getMessage());
							}
							byteBuffer.position(currentPosition);

							if (pointSetShapeLODElement != null) {
								prepareGeometry(lsgNode.getObjectID(), null, null, pointSetShapeLODElement, transformation, color, nodeName);
							}
						}

					} else {
						addLoadInformation("WARNING", "Object " + lsgNode.getObjectID() + " has multiple LateLoadedPropertyAtomElement assignments!");
					}
				}
			}

		// Load external referenced geometry
		} else if(lsgNode instanceof PartitionNodeElement && !_skipSubPartitions ) {
			PartitionNodeElement partitionNodeElement = (PartitionNodeElement)lsgNode;
			if(lsgNode.getParentLSGNode() != null){
				String urlAsString = _baseURLName.toString();

				// Get the absolute external reference file name
				URL externalReference = null;
				try {
					urlAsString = urlAsString.substring(0, urlAsString.lastIndexOf("/")) + File.separator + partitionNodeElement.getFileName();
					externalReference = new URL(urlAsString);
				} catch(Exception exception){
					_jtModel.addExternalReference(partitionNodeElement.getFileName(), false);
					addLoadInformation("WARNING", "Found malformed external reference: " + urlAsString);
				}

				if( externalReference==null || !existsURL(externalReference.toString())){
					_jtModel.addExternalReference(partitionNodeElement.getFileName(), false);
					addLoadInformation("WARNING", "Found missing external reference: " + externalReference);

				} else {
					_jtModel.addExternalReference(partitionNodeElement.getFileName(), true);


					Matrix4d transformation = null;
					Matrix4d rotation = null;
					if(!_skipGeometry) {
						// Get the transformation matrix
						transformation = getTransformationFromParentNodes(lsgNode);

						// Extract the rotation from the transformation
						rotation = (Matrix4d) transformation.clone();
						rotation.setTranslation(new Vector3d());
					}

					// Load the referenced file
					try {
						URL oldURLName = _currentURLName;
						JTImporter jtImporter = new JTImporter();
						jtImporter.setSkipGeometry(_skipGeometry);
						jtImporter.loadFile(externalReference, true);

						// Transfer the load information
						for(String[] loadInformation : jtImporter.getLoadInformation()){
							addLoadInformation(loadInformation[0], loadInformation[1]);
						}

						// Transfer the unsupported entities
						for(String unsupportedEntity : jtImporter.getUnsupportedEntities()){
							addUnsupportedEntity(unsupportedEntity);
						}

						if(!_skipGeometry) {
							HashMap<String, ArrayList<Object[]>> jtEntities = jtImporter.getFaces();
							for (String layerName : jtEntities.keySet()) {
								ArrayList<Object[]> faces = jtEntities.get(layerName);
								for (Object[] faceList : faces) {
									double[] vertices = (double[]) faceList[0];
									int[] indices = (int[]) faceList[1];
									double[] colors = (double[]) faceList[2];
									double[] normals = (double[]) faceList[3];

									// Apply the transformation to all vertices
									xformVec3s(vertices, transformation);

									// Apply the transformation to all normals
									xformVec3s(normals, rotation);

									// Add the new positioned face
									_jtModel.addTriangles(vertices, indices, colors, normals, layerName);
								}
							}
						}

						_currentURLName = oldURLName;

					} catch(Exception exception){
						addLoadInformation("WARNING", "Failed loading external reference: " + externalReference.toString());
					}
				}
			}
		}

		for(LSGNode childNode : lsgNode.getChildLSGNodes()){
			walkLSGTree(childNode, byteBuffer, workingContext);

			// Skip all other LOD's
			if(lsgNode instanceof RangeLODNodeElement){
				break;
			}
		}
	}

	/**
	 * Verifies, whether the given URL points to a valid target. If the URL points to
	 * a HTTP folder, some server deny the access and return a HTTP_FORBIDDEN (403).
	 * @param  urlAsString Full URL
	 * @return             Does the target of the URL exists?
	 */
	public static boolean existsURL(final String urlAsString){
		// The URL points to a local file
		if(urlAsString.startsWith("file:")){
			File file = new File(urlAsString.substring(5));
			return file.exists();
		}

		// The URL points to a protocol file
		try {
			URLConnection connection = new URL(urlAsString).openConnection();
			if(connection instanceof HttpURLConnection){
				HttpURLConnection.setFollowRedirects(false);
				HttpURLConnection httpURLConnection = (HttpURLConnection)connection;
				httpURLConnection.setRequestMethod("HEAD");
				return (httpURLConnection.getResponseCode() == HttpURLConnection.HTTP_OK);

			} else {
				System.err.println("URLConnection '" + connection + "' not supported!");
				return false;
			}

		} catch(ConnectException exception){
			return false;

		} catch(Exception exception){
			exception.printStackTrace();
			return false;
		}
	}

	/**
	 * Detects the color for the given node.
	 * @param  lsgNode LSG node to examine
	 * @return         Detected color or<br>
	 *                 <b>null</b> if the color couldn't be found
	 */
	private Color getColorFromParentNodes(LSGNode lsgNode){
		Color color = null;
		boolean foundMaterialAttributeElement = false;
		for(JTNode jtNode : lsgNode.getAttributeNodes()){
			if(jtNode instanceof MaterialAttributeElement){
				if(!foundMaterialAttributeElement){
					foundMaterialAttributeElement = true;
					color = ((MaterialAttributeElement)jtNode).getDiffuseColor();
				}
			}
		}
		if(color == null){
			if(lsgNode.getParentLSGNode() != null){
				color = getColorFromParentNodes(lsgNode.getParentLSGNode());
			}
		}
		if(color == null){
			color = DEFAULT_COLOR;
		}
		return color;
	}

	/**
	 * Transformation of the given node.
	 * @param  lsgNode LSG node to examine
	 * @return         Detected transformation
	 */
	private Matrix4d getTransformationFromParentNodes(LSGNode lsgNode){
		Matrix4d transformation = new Matrix4d();
		transformation.setIdentity();

		do {
			for(JTNode jtNode : lsgNode.getAttributeNodes()){
				if(jtNode instanceof GeometricTransformAttributeElement){
					Matrix4d tmp = (Matrix4d)((GeometricTransformAttributeElement)jtNode).getTransformationMatrix().clone();
					tmp.mul(transformation);
					transformation = tmp;
				}
			}
			lsgNode = lsgNode.getParentLSGNode();
		} while(lsgNode.getParentLSGNode() != null);

		return transformation;
	}

	/**
	 * Layer name of the given node.
	 * @param  lsgNode LSG node to examine
	 * @return         Detected layer name or<br>
	 *                 <b>JTImporter.DEFAULT_LAYER</b> if the layer name couldn't be found
	 */
	private String getLayerName(LSGNode lsgNode){
		List<String> nodeNameList = new ArrayList<>();
		while(lsgNode != null){
			if(	(lsgNode instanceof MetaDataNodeElement) || (lsgNode instanceof InstanceNodeElement) ||
				(lsgNode instanceof PartNodeElement) || (lsgNode instanceof PartitionNodeElement)){
				String nodeName = getNodeName(lsgNode).orElse(null);
				if(nodeName != null){
					if((nodeNameList.size() == 0) || !nodeNameList.get(0).equals(nodeName)){
						if(lsgNode instanceof InstanceNodeElement){
							if(nodeName.endsWith("_SOLIDS")){
								nodeName = nodeName.substring(0, nodeName.length() - 7);
							} else if(nodeName.endsWith("_FACETS")){
								nodeName = nodeName.substring(0, nodeName.length() - 7);
							} else if(nodeName.endsWith("_WF")){
								nodeName = nodeName.substring(0, nodeName.length() - 3);
							}
						}
						nodeNameList.add(0, nodeName);
					}
				}
			}
			lsgNode = lsgNode.getParentLSGNode();
		}

		StringBuilder stringBuffer = new StringBuilder();
		Iterator<String> iterator = nodeNameList.iterator();
		while(iterator.hasNext()){
			stringBuffer.append(iterator.next());
			if(iterator.hasNext()){
				stringBuffer.append("#");
			}
		}
		return stringBuffer.length() > 0 ? stringBuffer.toString() : JTImporter.DEFAULT_LAYER;
	}

    public String getLSGAsString() {
	    StringBuilder sbuilder = new StringBuilder();
        getLSGStructure(_rootNode,sbuilder,0);
        return sbuilder.toString();
    }

    public boolean isMonolithic() {
        Stack<LSGNode> nodeStack = new Stack<>();
        nodeStack.push(this.rootNode());
        while ( !nodeStack.empty() ) {
            LSGNode parent = nodeStack.pop();
            for ( LSGNode child : parent.getChildLSGNodes() ) {
                if ( child.isJtPartition() ) {
                    return false;
                }
                nodeStack.push(child);
            }
        }

        return true;
    }

    static private void getLSGStructure(LSGNode lsgNode, StringBuilder result, int depth) {

        for ( int dd=0; dd<depth; ++dd) {
            result.append("    ");
        }

        result.append(lsgNode.getTypeName());
        result.append("["+lsgNode.getObjectID()+"] ");

        Optional<String> optName = lsgNode.get_JT_PROP_NAME();
        if ( optName.isPresent() ) {
            result.append("\"" + optName.get() + "\"\n");
        }
        else {
            result.append("<>\n");
        }

        for ( LSGNode child : lsgNode.getChildLSGNodes() ) {
            getLSGStructure(child, result, depth+1);
        }
    }

    /**
	 * Returns the node name.
	 * @param  lsgNode LSG node
	 * @return         Name of the node
	 */
	public Optional<String> getNodeName(LSGNode lsgNode){
	    return lsgNode.getName();
	}

	/**
	 * Fills the JT models with the triangulated faces.
	 * @param parentNodeObjectID         Object ID of the parent LSG node
	 * @param triStripSetShapeLODElement TriStripSetShapeLODElement
	 * @param polylineSetShapeLODElement PolylineSetShapeLODElement
	 * @param pointSetShapeLODElement    PointSetShapeLODElement
	 * @param transformation             Transformation
	 * @param globalColor                Default color
	 * @param layerName                  Layer name
	 */
	private void prepareGeometry(int parentNodeObjectID, TriStripSetShapeLODElement triStripSetShapeLODElement, PolylineSetShapeLODElement polylineSetShapeLODElement, PointSetShapeLODElement pointSetShapeLODElement, Matrix4d transformation, Color globalColor, String layerName){
		try {
			if(_jtModel.getJTFileVersion() < 9.0) {
                prepareGeometry_v8(parentNodeObjectID, triStripSetShapeLODElement, polylineSetShapeLODElement, pointSetShapeLODElement, transformation, globalColor, layerName);
			// JT version 9+
			} else {
                prepareGeometry_v9(parentNodeObjectID, triStripSetShapeLODElement, polylineSetShapeLODElement, pointSetShapeLODElement, transformation, globalColor, layerName);
            }
		} catch(Exception exception){
			exception.printStackTrace();
			addLoadInformation("WARNING", "Failed decoding node element: " + layerName + " (" + exception.getMessage() + ")");
		}
	}

    private void prepareGeometry_v8(int parentNodeObjectID, TriStripSetShapeLODElement triStripSetShapeLODElement, PolylineSetShapeLODElement polylineSetShapeLODElement, PointSetShapeLODElement pointSetShapeLODElement, Matrix4d transformation, Color globalColor, String layerName) {
        if(triStripSetShapeLODElement != null){
            VertexBasedShapeCompressedRepData vertexBasedShapeCompressedRepData = triStripSetShapeLODElement.getVertexBasedShapeCompressedRepData();
            List<Double> normalsAsList = vertexBasedShapeCompressedRepData.getNormals();
            List<Float> colorsAsList = vertexBasedShapeCompressedRepData.getColors();
            List<Integer> indicesAsList = vertexBasedShapeCompressedRepData.getIndices();
            List<Double> verticesAsList = vertexBasedShapeCompressedRepData.getVertices();
            if((verticesAsList == null) || (verticesAsList.size() == 0)){
                return;
            }

            // Extract the rotation from the transformation
            Matrix4d rotation = (Matrix4d)transformation.clone();
            rotation.setTranslation(new Vector3d());

            // Calculate the number of vertices and faces
            int vertexCount = 0;
            int faceCount = 0;
            for(int i = 0; i < (indicesAsList.size() - 1); i++){
                int startIndex = indicesAsList.get(i);
                int endIndex = indicesAsList.get(i + 1);
                vertexCount += (endIndex - startIndex);
                faceCount += (endIndex - startIndex - 2);
            }

            // Possibly create color list
            double[] colors;
            if((colorsAsList == null) || (colorsAsList.size() == 0)){
                float[] color = globalColor.getColorComponents(null);
                colors = new double[faceCount * 3];
                for(int i = 0; i < colors.length; i += 3){
                    colors[i]     = color[0];
                    colors[i + 1] = color[1];
                    colors[i + 2] = color[2];
                }
            } else {
                colors = new double[colorsAsList.size()];
                for(int i = 0; i < colors.length; i++){
                    colors[i] = colorsAsList.get(i);
                }
            }

            // Create the vertex and index list
            double[] vertices = new double[vertexCount * 3];
            double[] normals = new double[vertexCount * 3];
            int[] faceIndices = new int[faceCount * 3];

            int l = 0;
            for(int i = 0; i < (indicesAsList.size() - 1); i++){
                int startIndex = indicesAsList.get(i);
                int endIndex = indicesAsList.get(i + 1);
                // Fill the vertex list
                for(int j = startIndex; j < endIndex; j++){
                    int k = j * 3;

                    // Apply the transformation to each vertex
                    Point3d vertex = new Point3d(verticesAsList.get(k), verticesAsList.get(k + 1), verticesAsList.get(k + 2));
                    transformation.transform(vertex);
                    vertices[k]     = vertex.getX();
                    vertices[k + 1] = vertex.getY();
                    vertices[k + 2] = vertex.getZ();


                    // Apply the rotation to each normal
                    Point3d normal = new Point3d(normalsAsList.get(k), normalsAsList.get(k + 1), normalsAsList.get(k + 2));
                    rotation.transform(normal);
                    normals[k]     = normal.getX();
                    normals[k + 1] = normal.getY();
                    normals[k + 2] = normal.getZ();
                }



                // Fill the index list
                for(int j = startIndex; j < (endIndex - 2); j++){
                    faceIndices[l]     = j;
                    faceIndices[l + 1] = j + 1;
                    faceIndices[l + 2] = j + 2;

                    l += 3;
                }
            }

            _jtModel.addTriangles(vertices, faceIndices, colors, normals, layerName);

        } else if(pointSetShapeLODElement != null){
            VertexBasedShapeCompressedRepData vertexBasedShapeCompressedRepData = pointSetShapeLODElement.getVertexBasedShapeCompressedRepData();
            List<Double> vertices = vertexBasedShapeCompressedRepData.getVertices();
            List<Float> colors = vertexBasedShapeCompressedRepData.getColors();
            if((colors == null) || (colors.size() == 0)){
                colors = new ArrayList<>();
                float[] color = globalColor.getColorComponents(null);
                for(int i = 0; i < vertices.size(); i += 3){
                    colors.add(color[0]);
                    colors.add(color[1]);
                    colors.add(color[2]);
                }
            }
            _jtModel.addPoints(vertices, colors, layerName);
        }
    }

    private void prepareGeometry_v9(int parentNodeObjectID, TriStripSetShapeLODElement triStripSetShapeLODElement, PolylineSetShapeLODElement polylineSetShapeLODElement, PointSetShapeLODElement pointSetShapeLODElement, Matrix4d transformation, Color globalColor, String layerName) {
        VertexShapeLODElement vertexShapeLODElement;
        if(triStripSetShapeLODElement != null){
            vertexShapeLODElement = triStripSetShapeLODElement.getVertexShapeLODElement();
            List<Double> normalsAsList = vertexShapeLODElement.getNormals();
            List<Double> colorsAsList = vertexShapeLODElement.getColors();
            List<List<Integer>> indexLists = vertexShapeLODElement.getIndices();
            List<Double> verticesAsList = vertexShapeLODElement.getVertices();

            if((verticesAsList == null) || (verticesAsList.size() == 0) || (indexLists.get(0).size() == 0)){
                addLoadInformation("WARNING", "Found empty element!");
                return;
            }

            // Extract the rotation from the transformation
            Matrix4d rotation = (Matrix4d)transformation.clone();
            rotation.setTranslation(new Vector3d());

            List<Integer> vertexIndicesList = indexLists.get(0);
            List<Integer> normalIndicesList = indexLists.get(1);

            double[] verticesNew = new double[vertexIndicesList.size() * 3];
            int[] indicesNew = new int[vertexIndicesList.size()];
            double[] normalsNew = new double[vertexIndicesList.size() * 3];
            int lastNormalIndex = -1;
            for(int i = 0, vertexCount = 0, normalCount = 0; i < (vertexIndicesList.size() / 3); i++){
                int baseIndex = (i * 3);

                int faceIndex1 = vertexIndicesList.get(baseIndex);
                int faceIndex2 = vertexIndicesList.get(baseIndex + 1);
                int faceIndex3 = vertexIndicesList.get(baseIndex + 2);

                int normalIndex1 = normalIndicesList.get(baseIndex);
                int normalIndex2 = normalIndicesList.get(baseIndex + 1);
                int normalIndex3 = normalIndicesList.get(baseIndex + 2);

                if(normalIndex1 == -1){
                    normalIndex1 = lastNormalIndex;
                }
                lastNormalIndex = normalIndex1;
                if(normalIndex2 == -1){
                    normalIndex2 = lastNormalIndex;
                }
                lastNormalIndex = normalIndex2;
                if(normalIndex3 == -1){
                    normalIndex3 = lastNormalIndex;
                }
                lastNormalIndex = normalIndex3;

                indicesNew[baseIndex]     = baseIndex;
                indicesNew[baseIndex + 1] = baseIndex + 1;
                indicesNew[baseIndex + 2] = baseIndex + 2;

                // Apply the transformation to each vertex
                Point3d vertex = new Point3d(	verticesAsList.get((faceIndex1 * 3)),
                        verticesAsList.get((faceIndex1 * 3) + 1),
                        verticesAsList.get((faceIndex1 * 3) + 2));
                transformation.transform(vertex);
                verticesNew[vertexCount++] = vertex.getX();
                verticesNew[vertexCount++] = vertex.getY();
                verticesNew[vertexCount++] = vertex.getZ();

                vertex = new Point3d(	verticesAsList.get((faceIndex2 * 3)),
                        verticesAsList.get((faceIndex2 * 3) + 1),
                        verticesAsList.get((faceIndex2 * 3) + 2));
                transformation.transform(vertex);
                verticesNew[vertexCount++] = vertex.getX();
                verticesNew[vertexCount++] = vertex.getY();
                verticesNew[vertexCount++] = vertex.getZ();

                vertex = new Point3d(	verticesAsList.get((faceIndex3 * 3)),
                        verticesAsList.get((faceIndex3 * 3) + 1),
                        verticesAsList.get((faceIndex3 * 3) + 2));
                transformation.transform(vertex);
                verticesNew[vertexCount++] = vertex.getX();
                verticesNew[vertexCount++] = vertex.getY();
                verticesNew[vertexCount++] = vertex.getZ();

                // Apply the rotation to each normal
                Point3d normal = new Point3d(	normalsAsList.get((normalIndex1 * 3)),
                        normalsAsList.get((normalIndex1 * 3) + 1),
                        normalsAsList.get((normalIndex1 * 3) + 2));
                rotation.transform(normal);
                normalsNew[normalCount++] = normal.getX();
                normalsNew[normalCount++] = normal.getY();
                normalsNew[normalCount++] = normal.getZ();

                normal = new Point3d(	normalsAsList.get((normalIndex2 * 3)),
                        normalsAsList.get((normalIndex2 * 3) + 1),
                        normalsAsList.get((normalIndex2 * 3) + 2));
                rotation.transform(normal);
                normalsNew[normalCount++] = normal.getX();
                normalsNew[normalCount++] = normal.getY();
                normalsNew[normalCount++] = normal.getZ();

                normal = new Point3d(	normalsAsList.get((normalIndex3 * 3)),
                        normalsAsList.get((normalIndex3 * 3) + 1),
                        normalsAsList.get((normalIndex3 * 3) + 2));
                rotation.transform(normal);
                normalsNew[normalCount++] = normal.getX();
                normalsNew[normalCount++] = normal.getY();
                normalsNew[normalCount++] = normal.getZ();
            }

            // Possibly create color list
            double[] colors;
            if((colorsAsList == null) || (colorsAsList.size() == 0)){
                float[] color = globalColor.getColorComponents(null);
                colors = new double[vertexIndicesList.size()];
                for(int i = 0; i < colors.length; i += 3){
                    colors[i]     = color[0];
                    colors[i + 1] = color[1];
                    colors[i + 2] = color[2];
                }
            } else {
                colors = new double[colorsAsList.size()];
                for(int i = 0; i < colors.length; i++){
                    colors[i] = colorsAsList.get(i);
                }
            }

            _jtModel.addTriangles(verticesNew, indicesNew, colors, normalsNew, layerName);

        } else if(polylineSetShapeLODElement != null) {
            VertexShapeLODData vertexShapeLODData = polylineSetShapeLODElement.getVertexShapeLODData();
            TopoMeshCompressedLODData topoMeshCompressedLODData = vertexShapeLODData.getTopoMeshCompressedLODData();
            TopoMeshCompressedRepDataV1 topoMeshCompressedRepDataV1 = topoMeshCompressedLODData.getTopoMeshCompressedRepDataV1();
            if(topoMeshCompressedRepDataV1 == null){
                topoMeshCompressedRepDataV1 = topoMeshCompressedLODData.getTopoMeshCompressedRepDataV2().getTopoMeshCompressedRepDataV1();
            }

            List<Double> colorsAsList = null;
            if(topoMeshCompressedRepDataV1.getCompressedVertexColorArray() != null){
                colorsAsList = topoMeshCompressedRepDataV1.getCompressedVertexColorArray().getColors();
            }

            List<Integer> vertexIndicesList = topoMeshCompressedRepDataV1.getVertexListIndices();
            List<Integer> primitiveIndicesList = topoMeshCompressedRepDataV1.getPrimitiveListIndices();
            if(topoMeshCompressedRepDataV1.getCompressedVertexCoordinateArray() == null){
                return;
            }
            List<Double> verticesAsList = topoMeshCompressedRepDataV1.getCompressedVertexCoordinateArray().getVertices();

            if(colorsAsList == null){
                colorsAsList = new ArrayList<>();
                float[] color = globalColor.getColorComponents(null);
                for(int i = 0; i < verticesAsList.size(); i += 3){
                    colorsAsList.add((double)color[0]);
                    colorsAsList.add((double)color[1]);
                    colorsAsList.add((double)color[2]);
                }
            }

            // Extract the rotation from the transformation
            Matrix4d rotation = (Matrix4d)transformation.clone();
            rotation.setTranslation(new Vector3d());

            for(int i = 0; i < (primitiveIndicesList.size() - 1); i++){
                int startIndex = primitiveIndicesList.get(i);
                int endIndex = primitiveIndicesList.get(i + 1);

                // Fill the vertex list
                List<Double[]> polylineVertices = new ArrayList<>();
                List<Double[]> polylineColors = new ArrayList<>();
                for(int j = startIndex; j < endIndex; j++){
                    int vertexIndex = vertexIndicesList.get(j) * 3;
                    double x = verticesAsList.get(vertexIndex);
                    double y = verticesAsList.get(vertexIndex + 1);
                    double z = verticesAsList.get(vertexIndex + 2);

                    // Apply the transformation to each vertex
                    Point3d vertex = new Point3d(x, y, z);
                    transformation.transform(vertex);

                    // Add the transformed vertex
                    polylineVertices.add(new Double[]{vertex.getX(), vertex.getY(), vertex.getZ()});
                    polylineColors.add(new Double[]{colorsAsList.get(vertexIndex),
                            colorsAsList.get(vertexIndex + 1),
                            colorsAsList.get(vertexIndex + 2)});
                }
                _jtModel.addPolyline(polylineVertices, polylineColors, layerName);
            }
        }
    }


        /**
         * Adds an unique load information message.
         * @param type    Message type
         * @param message Message text
         */
	public static void addLoadInformation(String type, String message){
		for(String[] information : _loadInformation){
			if(information[0].equals(type) && information[1].equals(message)){
				return;
			}
		}
		_loadInformation.add(new String[]{type, message});
	}

	/**
	 * Adds an unique unsupprted element.
	 * @param elementID Element ID
	 */
	private void addUnsupportedEntity(String elementID){
		String newUnsupportedEntityString = _guidMapping.containsKey(elementID) ? elementID + " (" + _guidMapping.get(elementID) + ")" : elementID;
		for(String unsupportedEntityString : _unsupportedEntities){
			if(unsupportedEntityString.equals(newUnsupportedEntityString)){
				return;
			}
		}
		_unsupportedEntities.add(newUnsupportedEntityString);
	}

	/**
	 * Returns the list of faces.
	 * @return List of faces, sorted by their layer
	 */
	public HashMap<String, ArrayList<Object[]>> getFaces(){
		return _jtModel.getFaces();
	}

	/**
	 * Returns the list of polylines.
	 * @return List of polylines sorted by their layers
	 */
	public HashMap<String, ArrayList<Object[]>> getPolylines(){
		return _jtModel.getPolylines();
	}

	/**
	 * Returns the list of points.
	 * @return List of points sorted by their layers
	 */
	public HashMap<String, ArrayList<Object[]>> getPoints(){
		return _jtModel.getPoints();
	}

	/**
	 * Returns a list of layer names with their visibility.
	 * @return List of layer names with their visibility
	 */
	public HashMap<String, Boolean> getLayerMetaData(){
		return _jtModel.getLayerMetaData();
	}

	/**
	 * Returns the initial transformation matrix, stored by the manufacturer in the
	 * file. It specifies how the camera is directed to the model.
	 * @return 4x4 matrix with the initial transformation or<br>
	 *         <b>null</b> if no such information has been stored
	 */
	public double[] getInitialTransformationMatrix(){
		return new double[]{1.0, 0.0, 0.0, 0.0,
							0.0, 1.0, 0.0, 0.0,
							0.0, 0.0, 1.0, 0.0,
							0.0, 0.0, 0.0, 1.0};
	}

	/**
	 * Returns the flag, telling whether the paper space is used or not.
	 * @return Is the paper space used?
	 */
	public boolean isPaperSpaceIsUsed(){
		return false;
	}

	/**
	 * Returns a flag, telling whether the loaded model is 2D (or 3D).
	 * @return Is the loaded model 2D?
	 */
	public boolean is2D(){
		double[][] extremeValues = _jtModel.getExtremeValues();
		return ((extremeValues[0][0] == extremeValues[0][1]) ||
				(extremeValues[1][0] == extremeValues[1][1]) ||
				(extremeValues[2][0] == extremeValues[2][1]));
	}

	/**
	 * Returns a flag, telling whether the loaded model contains faces.
	 * @return Does the loaded model contains faces?
	 */
	public boolean isShadingPossible(){
		return true;
	}

	/**
	 * Returns the extreme values.
	 * @return Extreme values (double[2][3] [x1, y1, z1] and [x2, y2, z2])
	 */
	public double[][] getExtremeValues(){
		return _jtModel.getExtremeValues();
	}

	/**
	 * Returns a list of infos and errors, occured while reading the file and
	 * building the model.
	 * @return List of string[2] with the infos and errors
	 */
	public List<String[]> getLoadInformation(){
		return _loadInformation;
	}

	/**
	 * Returns specific information about the file and the model.
	 * @return List of string[2] containing all information
	 */
	public ArrayList<String[]> getModelInformation(){
		return _jtModel.getModelInformation();
	}

	/**
	 * Called every time when a group code has been read.
	 * @param readBytes Number of read bytes
	 */
	public static void updateProgress(int readBytes){
		if(_readBytes == null){
			return;
		}

		_readBytes.put(_currentURLName, _readBytes.get(_currentURLName) + readBytes);
		_progressIntervall.put(_currentURLName, _progressIntervall.get(_currentURLName) + readBytes);

		if(_progressListener != null){
			if(_progressIntervall.get(_currentURLName) > ProgressListenerInterface.PROGRESS_UPDATER_FREQUENCY_PARSER){
				_progressIntervall.put(_currentURLName, _progressIntervall.get(_currentURLName) - ProgressListenerInterface.PROGRESS_UPDATER_FREQUENCY_PARSER);
				for (ProgressListenerInterface progressListenerInterface : _progressListener) {
					if (progressListenerInterface != null) {
						progressListenerInterface.progressChanged(new ProgressEvent((byte) ((_readBytes.get(_currentURLName) * 100.0) / _fileLength.get(_currentURLName))));
					}
				}
			}
		}
	}

	/**
	 * Adds a progress listener, called when the progress has changed.
	 * @param progressListenerInterface Progress listener
	 */
	public void addProgressListener(ProgressListenerInterface progressListenerInterface){
		if(_progressListener == null){
			_progressListener = new ArrayList<>();
		}

		_progressListener.add(progressListenerInterface);
	}

	/**
	 * Returns the unsupported entities.
	 * @return           List of unsupported entities
	 * @throws Exception Thrown if something failed
	 */
	public ArrayList<String> getUnsupportedEntities() throws Exception {
		return _unsupportedEntities;
	}

	/**
	 * Logs the time between a given starting time and the current time
	 * @param description Description of the log event for logging
	 * @param startTime The starting time in nanoseconds
	 * @return current time in nanoseconds
	 *
	 */
	public long logTimer(String description, long startTime) {
		long currentTime = System.nanoTime();
		System.out.println(description + " Time: " + (currentTime - startTime) / 1000000.0 + " ms");
		return currentTime;
	}

    public boolean isProfilingEnabled() {
        return this._profilingEnabled;
    }

    public void setProfilingEnabled(boolean profilingEnabled) {
        this._profilingEnabled = profilingEnabled;
    }
}
