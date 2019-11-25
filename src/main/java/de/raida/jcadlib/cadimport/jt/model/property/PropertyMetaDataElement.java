package de.raida.jcadlib.cadimport.jt.model.property;

import de.raida.jcadlib.cadimport.jt.model.JTNode;
import de.raida.jcadlib.cadimport.jt.reader.Helper;
import de.raida.jcadlib.cadimport.jt.reader.WorkingContext;

import java.nio.ByteBuffer;
import java.util.HashMap;

public class PropertyMetaDataElement implements JTNode {
    /** Object type ID */
    public final static String ID = "ce357247-38fb-11d1-a5-6-0-60-97-bd-c6-e1";

    private int _objectID;
    private HashMap<String, Object> propertyTable;

    public PropertyMetaDataElement(int objectId, HashMap<String, Object> map) {
        _objectID = objectId;
        propertyTable = map;
    }

    /**
     * Reads a LateLoadedPropertyAtomElement object.
     * @param  workingContext Working context
     * @return                LateLoadedPropertyAtomElement instance
     */
    public static PropertyMetaDataElement read(WorkingContext workingContext){
        ByteBuffer byteBuffer = workingContext.getByteBuffer();
        int objectID = -1;
        if ( workingContext.getJTFileVersion()>=9.0 ) {
            objectID = Helper.readI32(byteBuffer);
            int version = Helper.readLocalVersionNum(byteBuffer, workingContext.getJTFileVersion());
            if (version > 2 || version < 0) {
                throw new IllegalArgumentException("Found invalid version number: " + version);
            }
        }

        HashMap<String, Object> props = new HashMap<>();

        do {
            String key = Helper.readMultiByteString(byteBuffer);
            if ( key == null ) {
                break;
            }

            Object value;
            int valueType = Helper.readU8(byteBuffer);
            switch ( valueType ) {
                case 1: // String
                    value = Helper.readMultiByteString(byteBuffer);
                    if ( value == null ) {
                        value = "";
                    }
                    break;
                case 2: // Int32
                    value = Helper.readI32(byteBuffer);
                    break;
                case 3: // Float32
                    value = Helper.readF32(byteBuffer);
                    break;
                case 4: // Date
                    value = Helper.readDateTime(byteBuffer);
                    break;
                default:
                    throw new RuntimeException("Unexpected value type: " + valueType);
            }

            props.put(key, value);
        } while ( true );

        return new PropertyMetaDataElement(objectID, props);
    }

    public HashMap<String, Object> getPropertyTable() {
        return propertyTable;
    }

    @Override
    public int getObjectID() {
        return _objectID;
    }

    @Override
    public String getTypeName() {
        return "PropertyMetaDataElement";
    }
}
