package de.raida.jcadlib.cadimport.jt.model.lsg;

import de.raida.jcadlib.cadimport.jt.model.LSGNode;
import de.raida.jcadlib.cadimport.jt.reader.WorkingContext;

import java.nio.ByteBuffer;

public class LineStyleAttributeElement extends LSGNode {
    /** Object type ID */
    public final static String ID = "10dd10c4-2ac8-11d1-9b-6b-0-80-c7-bb-59-97";

    /** Base attribute data */
    private BaseAttributeData _baseAttributeData;

    /** Version number */
    private int _versionNumber;

    /** Data flags */
    private int _dataFlags;

    LineStyleAttributeElement(BaseAttributeData baseAttributeData) {
        _baseAttributeData = baseAttributeData;
    }

    @Override
    public int[] getChildNodeObjectIDs() {
        return new int[0];
    }

    @Override
    public int[] getAttributeObjectIDs() {
        return new int[0];
    }

    @Override
    public LSGNode copy(LSGNode lsgNode) {
        return new LineStyleAttributeElement(_baseAttributeData);
    }

    @Override
    public int getObjectID() {
        return -1;
    }

    @Override
    public String getTypeName() {
        return null;
    }

    public static LineStyleAttributeElement read(WorkingContext workingContext) {
        ByteBuffer byteBuffer = workingContext.getByteBuffer();

        BaseAttributeData baseAttributeData = BaseAttributeData.read(workingContext);

        // TODO

        return new LineStyleAttributeElement(baseAttributeData);
    }
}
