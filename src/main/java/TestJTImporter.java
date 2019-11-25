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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintStream;
import java.time.ZonedDateTime;
import java.util.*;

import de.raida.jcadlib.cadimport.jt.JTImporter;
import de.raida.jcadlib.cadimport.jt.model.JTNode;
import de.raida.jcadlib.cadimport.jt.model.LSGNode;
import de.raida.jcadlib.cadimport.jt.model.lsg.GeometricTransformAttributeElement;
import de.raida.jcadlib.cadimport.jt.model.lsg.MaterialAttributeElement;
import de.raida.jcadlib.cadimport.jt.model.lsg.PartitionNodeElement;
import de.raida.jcadlib.cadimport.jt.model.lsg.TriStripSetShapeNodeElement;
import de.raida.jcadlib.cadimport.jt.model.property.*;

import javax.vecmath.Matrix4d;

class ToFile {
    public static void write(String abiStr,String fname) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File("abi/"+fname),true))) {
            //create a temporary file
            writer.write(abiStr);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}


/**
 * Test class for the JT importer.
 */
public class TestJTImporter {

    public String filename = null;
    public boolean printBOM = false;
    public boolean printLSG = false;
    public boolean profileOnly = true;
    public boolean skipGeometry = true;
    public boolean skipAttribs = true;
    public boolean skipProperties = true;
    public boolean verbose = false;
    public boolean removeMatrixScale= true;

    public void extract() {
        System.out.println("\nModel file: " + this.filename);
        if ( printLSG || profileOnly ) {
            extractGraph();
        }

        System.out.println("Done");
    }

    /**
	 * Test export.
	 */
	private void extractGraph() {
		try {
            JTImporter importer = new JTImporter();
            importer.setSkipGeometry(skipGeometry);

			// Load the JT file
			System.out.println("\nLoading: " + filename);
			long startTime = System.nanoTime();

            importer.loadFile(new File(filename).toURI().toURL());

			// Print all available information
			if(! profileOnly) {
				printLSGSummary(System.out, importer);
			}
            importer.logTimer("Complete processing", startTime);

		} catch(Exception exception){
			exception.printStackTrace();
		}
	}

	/**
	 * Prints information, available after loading the file.
	 * @param  jtImporter JT importer
	 * @throws Exception  Thrown when something happens
	 */
	private void printLSGSummary(PrintStream out, JTImporter jtImporter) throws Exception {
        out.println("\nLoad information:");
        out.println("--------------------------------------------------");
        List<String[]> loadInformation = jtImporter.getLoadInformation();
        if (loadInformation.size() == 0) {
            out.println("   ---");
        } else {
            for (String[] information : loadInformation) {
                out.println("   " + information[0] + ": " + information[1]);
            }
        }

        out.println("\nModel information:");
        out.println("--------------------------------------------------");
        for (String[] modelInformation : jtImporter.getModelInformation()) {
            if (!modelInformation[0].equals("") && !modelInformation[1].equals("")) {
                out.println("   " + modelInformation[0] + ": " + modelInformation[1]);
            } else if (!modelInformation[0].equals("")) {
                out.println("   " + modelInformation[0]);
            } else if (!modelInformation[1].equals("")) {
                out.println("   " + modelInformation[1]);
            } else {
                out.println();
            }
        }

        out.println("\nUnsupported entities:");
        out.println("--------------------------------------------------");
        for (String unsupportedEntity : jtImporter.getUnsupportedEntities()) {
            out.println("   " + unsupportedEntity);
        }

        if ( !skipGeometry ) {
            printGeometry(out, jtImporter);
        }

        out.println("\nLogical Scene Graph:");
        out.println("--------------------------------------------------");
        long startTime = System.nanoTime();
        printLSG(out, jtImporter.rootNode(),0);

        jtImporter.logTimer("Time to extract BOM", startTime);
    }

    static int toRGBA(float[] v) {
	    if ( v==null )
	        return 0;
	    return ( ((int)(v[0]*255)<<24)
                | ((int)(v[1]*255)<<16)
                | ((int)(v[2]*255)<<8)
                | ((int)(v[3]*255)<<0) );
    }

    private void printLSG(PrintStream out, LSGNode lsgNode, int depth) {
	    StringBuilder sbuilder = new StringBuilder();
        for ( int dd=0; dd<depth; ++dd) {
            sbuilder.append("    ");
        }

        String indent = sbuilder.toString();
        out.printf("%1$s- %2$s[%3$s] ",indent, lsgNode.getTypeName(),lsgNode.getObjectID());

        Optional<String> optName = lsgNode.get_JT_PROP_NAME();
        if ( optName.isPresent() ) {
            out.println("\"" + optName.get() + "\"");
        }
        else {
            out.println("<>");
        }

        if ( !this.skipAttribs ) {
            printLSGAttributes(out, lsgNode, indent);
        }

        if ( !this.skipProperties ) {
            printLSGProperties(out, lsgNode, indent);
        }

        for ( LSGNode child : lsgNode.getChildLSGNodes() ) {
            printLSG(out, child, depth+1);
        }
    }

    private static String propAsString(JTNode pNode) {
        String pString;
        if ( pNode instanceof StringPropertyAtomElement ) {
            pString = "'"+ ((StringPropertyAtomElement)pNode).getValue() +"'";
        }
        else if ( pNode instanceof IntegerPropertyAtomElement) {
            pString = Integer.toString(((IntegerPropertyAtomElement)pNode).getValue());
        }
        else if ( pNode instanceof Float32PropertyAtomElement) {
            pString = Float.toString(((Float32PropertyAtomElement)pNode).getValue());
        }
        else if ( pNode instanceof DatePropertyAtomElement) {
            pString = "<"+ ((DatePropertyAtomElement)pNode).getValue().toString() +">";
        }
        else if ( pNode instanceof LateLoadedPropertyAtomElement) {
            pString = "{"+ ((LateLoadedPropertyAtomElement)pNode).getSegmentID() +"}";
        }
        else {
            throw new RuntimeException("what is this?");
        }

        return pString;
    }

    private static String objAsString(Object value) {
        String pString;
        if ( value instanceof String ) {
            pString = "'"+ value.toString() +"'";
        }
        else if ( value instanceof ZonedDateTime ) {
            pString = "<"+ value.toString() +">";
        }
        else if ( value instanceof Integer || value instanceof Float) {
            pString = value.toString();
        }
        else {
            throw new RuntimeException("what is this?");
        }

        return pString;
    }

    private static String toString_BBox(float[][] bbox) {
        return "[[" +
                bbox[0][0] + "," +
                bbox[0][1] + "," +
                bbox[0][2] + "]-[" +
                bbox[1][0] + "," +
                bbox[1][1] + "," +
                bbox[1][2] + "]]";
    }

    private void printLSGProperties(PrintStream out, LSGNode lsgNode, String indent) {
        if ( lsgNode instanceof PartitionNodeElement ) {
            PartitionNodeElement partition = (PartitionNodeElement)lsgNode;
            out.print(indent);
            out.print("    ");
            if ( partition.getFileName()!=null ) {
                out.printf("File name: '%s'\n", (partition.getFileName()!=null ? partition.getFileName() : "<none>"));
            }
        }
	    for ( Map.Entry<JTNode, JTNode> pair : lsgNode.getPropertyNodes().entrySet() ) {
            String kString = propAsString(pair.getKey());
            String vString = propAsString(pair.getValue());
            out.print(indent);
            out.print("    ");
            out.printf("*%s ==> %s\n", kString, vString);
        }

	    for ( Map.Entry<String, Object> pair : lsgNode.getLLProperties().entrySet() ) {
            out.print(indent);
            out.print("    ");
            out.printf("'%s' ==> %s\n", pair.getKey(), objAsString(pair.getValue()));
        }
    }

    private void printLSGAttributes(PrintStream out, LSGNode lsgNode, String indent) {
        if ( lsgNode instanceof PartitionNodeElement ) {
            PartitionNodeElement partition = (PartitionNodeElement)lsgNode;
            out.print(indent);
            out.print("    ");
            if ( partition.getBoundingBox()!=null ) {
                out.printf("BBox: %s\n", toString_BBox(partition.getBoundingBox()));
            }
            if ( partition.getRawBoundingBox()!=null ) {
                out.printf("RawBBox: %s\n", toString_BBox(partition.getRawBoundingBox()));
            }
        }
        else if ( lsgNode instanceof TriStripSetShapeNodeElement ) {
            TriStripSetShapeNodeElement ts = (TriStripSetShapeNodeElement)lsgNode;
            out.print(indent);
            out.print("    ");
            if ( ts.getRawBoundingBox()!=null ) {
                out.printf("BBox: %s\n", toString_BBox(ts.getRawBoundingBox()));
            }
        }

        for (JTNode jtNode : lsgNode.getAttributeNodes()) {
            out.print(indent);
            out.print("    ");

            if (jtNode instanceof MaterialAttributeElement) {
                MaterialAttributeElement attr = ((MaterialAttributeElement) jtNode);
                out.printf("M : Kd=%1$08x, Ka=%2$08x, Ks=%3$08x, Ke=%4$08x, Ns=%5$f\n",
                        toRGBA(attr.getDiffuseComponents()),
                        toRGBA(attr.getAmbientComponents()),
                        toRGBA(attr.getSpecularComponents()),
                        toRGBA(attr.getEmissionComponents()),
                        attr.getShininess() );
            } else if (jtNode instanceof GeometricTransformAttributeElement) {
                GeometricTransformAttributeElement attr = ((GeometricTransformAttributeElement) jtNode);
                Matrix4d mm = attr.getTransformationMatrix();
                out.printf("X : [[%1$g, %2$g, %3$g, %4$g]", mm.m00, mm.m01, mm.m02, mm.m03);
                out.printf(" [%1$g, %2$g, %3$g, %4$g]", mm.m10, mm.m11, mm.m12, mm.m13);
                out.printf(" [%1$g, %2$g, %3$g, %4$g]", mm.m20, mm.m21, mm.m22, mm.m23);
                out.printf(" [%1$g, %2$g, %3$g, %4$g]]\n", mm.m30, mm.m31, mm.m32, mm.m33);
            } else {
                out.print("Attribute: ");
                out.println(jtNode.getTypeName());
            }
        }
    }

        @SuppressWarnings("unchecked")
    private static void printGeometry(PrintStream out, JTImporter jtImporter) {
        out.println("\nMeta data:");
        out.println("--------------------------------------------------");
        double[][] boundingBox = jtImporter.getExtremeValues();
        out.println(" ... BBox min: " + boundingBox[0][0] + ", " + boundingBox[0][1] + ", " + boundingBox[0][2]);
        out.println(" ... BBox max: " + boundingBox[1][0] + ", " + boundingBox[1][1] + ", " + boundingBox[1][2]);
        HashMap<String, Boolean> layerMetadata = jtImporter.getLayerMetaData();
        out.println(" ... Number of layers: " + layerMetadata.size());
        for(String layerName : layerMetadata.keySet()){
            out.println("     ... visibility: " + layerName + " => " + layerMetadata.get(layerName));
        }

        out.println("\nPoints:");
        out.println("--------------------------------------------------");
        HashMap<String, ArrayList<Object[]>> pointEntities = jtImporter.getPoints();
        out.println(" ... # layers with points: " + pointEntities.size());
        for ( String layerName : pointEntities.keySet() ) {
            out.println("     ... layer: " + layerName);
            ArrayList<Object[]> points = pointEntities.get(layerName);
            out.println("         ... # entities: " + points.size());
            for(Object[] point : points){
                List<Double> vertices = (List<Double>)point[0];
                out.println("             ... [entity 1] vertices: " + (vertices.size() / 3) + " => (showing 1) " + vertices.subList(0, 3));

                List<Float> colors = (List<Float>)point[1];
                out.println("             ... [entity 1] colors: " + (colors.size() / 3) + " => (showing 1) " + colors.subList(0, 3));

                if(points.size() > 1){
                    out.println("             ...");
                    break;
                }
            }
        }

        out.println("\nPolylines:");
        out.println("--------------------------------------------------");
        HashMap<String, ArrayList<Object[]>> polylineEntities = jtImporter.getPolylines();
        out.println(" ... # layers with polylines: " + polylineEntities.size());
        for ( String layerName : polylineEntities.keySet() ) {
            out.println("     ... layer: " + layerName);
            ArrayList<Object[]> polylines = polylineEntities.get(layerName);
            out.println("         ... # entities: " + polylines.size());
            for(Object[] polyline : polylines){
                List<Double[]> vertices = (List<Double[]>)polyline[0];
                out.println("             ... [entity 1] vertices: " + vertices.size() + " => (showing 1) " + Arrays.toString(vertices.get(0)));

                List<Double[]> colors = (List<Double[]>)polyline[1];
                out.println("             ... [entity 1] colors: " + colors.size() + " => (showing 1) " + Arrays.toString(colors.get(0)));

                if(polylines.size() > 1){
                    out.println("             ...");
                    break;
                }
            }
        }

        out.println("\nFaces:");
        out.println("--------------------------------------------------");
        HashMap<String, ArrayList<Object[]>> faceEntities = jtImporter.getFaces();
        out.println(" ... # layers with faces: " + faceEntities.size());
        for ( String layerName : faceEntities.keySet() ) {
            out.println("     ... layer: " + layerName);
            ArrayList<Object[]> faces = faceEntities.get(layerName);
            out.println("         ... # entities: " + faces.size());
            ToFile.write("\n" + layerName, "layers");
            String tmp;
            for (Object[] faceList : faces) {
                double[] vertices = (double[]) faceList[0];
                int[] indices = (int[]) faceList[1];
                double[] colors = (double[]) faceList[2];
                double[] normals = (double[]) faceList[3];
                out.println("             ... [entity 1] vertices: " + vertices.length + " => (showing 1) [" + vertices[0] + ", " + vertices[1] + ", " + vertices[2] + "]");
                out.println("             ... [entity 1] indices: " + indices.length + " => (showing 3) [" + indices[0] + ", " + indices[1] + ", " + indices[2] + "]");
                out.println("             ... [entity 1] colors: " + colors.length + " => (showing 1) [" + colors[0] + ", " + colors[1] + ", " + colors[2] + "]");
                out.println("             ... [entity 1] normals: " + normals.length + " => (showing 1) [" + normals[0] + ", " + normals[1] + ", " + normals[2] + "]");
                for(int x=0;x<vertices.length-2;x+=3){
                	tmp=vertices[x] + "," + vertices[x+1] + ", " + vertices[x+2] + "\n";
                	ToFile.write(tmp, "vert_"+layerName);
                }
                for(int x=0;x<indices.length-2;x+=3){
                	tmp=indices[x] + "," + indices[x+1] + "," + indices[x+2] + "\n";
                	ToFile.write(tmp, "face_"+layerName);
                }

                if(faces.size() > 1){
                	out.println("             ...");
                    break;
                }
            }
        }
	}

        /**
         * Main entry point.
         * @param arguments Arguments of the command line
         */
	public static void main(String[] arguments){

	    TestJTImporter test = new TestJTImporter();

        for (String arg : arguments) {
            if (arg.startsWith("--")) {
                switch (arg) {
                    case "--lsg":
                        test.printLSG = true;
                        test.profileOnly = false;
                        break;
                    case "--bom":
                        test.printBOM = true;
                        test.profileOnly = false;
                        break;
                    case "--geometry":
                        test.skipGeometry = false;
                        break;
                    case "--attributes":
                        test.skipAttribs = false;
                        break;
                    case "--properties":
                        test.skipProperties = false;
                        break;
                    case "--verbose":
                    case "--v":
                        test.verbose = true;
                        break;
                    case "--keepMatrixScale":
                        test.removeMatrixScale= false;
                        break;
                    default:
                        System.err.println("Unknown option: '" + arg + "'");
                        break;
                }
            } else {
                test.filename = arg;
            }
        }

		test.extract();
	}
}
