package de.raida.jcadlib.cadimport.jt.model.precise;

import de.raida.jcadlib.cadimport.jt.model.JTNode;
import de.raida.jcadlib.cadimport.jt.reader.Helper;
import de.raida.jcadlib.cadimport.jt.reader.WorkingContext;

import java.nio.ByteBuffer;

public class PMIMetaDataElement implements JTNode {
    /** Object type ID */
    public final static String ID = "ce357249-38fb-11d1-a5-6-0-60-97-bd-c6-e1";

    private int _objectID;

    private PMIMetaDataElement(int objectId) {
        _objectID = objectId;
    }

    public static PMIMetaDataElement read(WorkingContext workingContext) {
        ByteBuffer byteBuffer = workingContext.getByteBuffer();
        int objectID = 0; // Helper.readI32(byteBuffer);
        int version = Helper.readI16(byteBuffer);
        if(version > 10 || version < 0 ){
            throw new IllegalArgumentException("Found invalid version number: " + version);
        }

        Helper.readI16(byteBuffer); // Empty field

        // TODO

        return new PMIMetaDataElement(objectID);
    }

        @Override
    public int getObjectID() {
        return _objectID;
    }

    @Override
    public String getTypeName() {
        return "PMIMetaDataElement";
    }
}
