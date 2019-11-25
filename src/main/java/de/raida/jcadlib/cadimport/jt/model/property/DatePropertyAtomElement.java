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

package de.raida.jcadlib.cadimport.jt.model.property;

import java.nio.ByteBuffer;
import java.time.ZonedDateTime;

import de.raida.jcadlib.cadimport.jt.model.JTNode;
import de.raida.jcadlib.cadimport.jt.reader.Helper;
import de.raida.jcadlib.cadimport.jt.reader.WorkingContext;

/**
 * <h>7.2.1.2.6 Date Property Atom Element</h>
 * Object Type ID: <code>0xce357246, 0x38fb, 0x11d1, 0xa5, 0x6, 0x0, 0x60, 0x97, 0xbd, 0xc6, 0xe1</code>
 * <br>Date Property Atom Element represents a property atom whose value is a 'date'.
 * <br>(c) 2014 by <a href="mailto:j.raida@gmx.net">Johannes Raida</a>
 * @author  <a href="mailto:j.raida@gmx.net">Johannes Raida</a>
 * @version 1.0
 */
public class DatePropertyAtomElement implements JTNode {
	/** Object type ID */
	public final static String ID = "ce357246-38fb-11d1-a5-6-0-60-97-bd-c6-e1";

	/** Base property atom data */
	private BasePropertyAtomData _basePropertyAtomData;
	private ZonedDateTime _value;

	/**
	 * Constructor.
	 * @param basePropertyAtomData Base property atom data
	 * @param dt                   DateTime
	 */
	public DatePropertyAtomElement(BasePropertyAtomData basePropertyAtomData, ZonedDateTime dt){
		_basePropertyAtomData = basePropertyAtomData;
		_value = dt;
	}

	/**
	 * Returns the object ID.
	 * @return Object ID
	 */
	public int getObjectID(){
		return _basePropertyAtomData.getObjectID();
	}

	/**
	 * Reads a DatePropertyAtomElement object.
	 * @param  workingContext Working context
	 * @return                DatePropertyAtomElement instance
	 */
	public static DatePropertyAtomElement read(WorkingContext workingContext){
		ByteBuffer byteBuffer = workingContext.getByteBuffer();

		BasePropertyAtomData baseData = BasePropertyAtomData.read(workingContext);

		if ( workingContext.getJTFileVersion()>=10.0 ) {
			int versionNumber = Helper.readLocalVersionNum(byteBuffer, workingContext.getJTFileVersion());
			if (versionNumber > 5) {
				throw new IllegalArgumentException("Found invalid version number: " + versionNumber);
			}
		}

		return new DatePropertyAtomElement(	baseData,
											Helper.readDateTime(byteBuffer));
	}

	@Override
	public String getTypeName() {
		return "DateProperty";
	}

	public ZonedDateTime getValue() {
		return _value;
	}
}
