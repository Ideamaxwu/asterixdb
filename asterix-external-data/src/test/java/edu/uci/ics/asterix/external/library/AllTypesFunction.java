/*
 * Copyright 2009-2013 by The Regents of the University of California
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * you may obtain a copy of the License from
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.uci.ics.asterix.external.library;

import edu.uci.ics.asterix.external.library.IExternalScalarFunction;
import edu.uci.ics.asterix.external.library.IFunctionHelper;
import edu.uci.ics.asterix.external.library.java.JObjects.JBoolean;
import edu.uci.ics.asterix.external.library.java.JObjects.JCircle;
import edu.uci.ics.asterix.external.library.java.JObjects.JDate;
import edu.uci.ics.asterix.external.library.java.JObjects.JDateTime;
import edu.uci.ics.asterix.external.library.java.JObjects.JDouble;
import edu.uci.ics.asterix.external.library.java.JObjects.JDuration;
import edu.uci.ics.asterix.external.library.java.JObjects.JFloat;
import edu.uci.ics.asterix.external.library.java.JObjects.JInt;
import edu.uci.ics.asterix.external.library.java.JObjects.JLine;
import edu.uci.ics.asterix.external.library.java.JObjects.JOrderedList;
import edu.uci.ics.asterix.external.library.java.JObjects.JPoint;
import edu.uci.ics.asterix.external.library.java.JObjects.JPoint3D;
import edu.uci.ics.asterix.external.library.java.JObjects.JPolygon;
import edu.uci.ics.asterix.external.library.java.JObjects.JRecord;
import edu.uci.ics.asterix.external.library.java.JObjects.JString;
import edu.uci.ics.asterix.external.library.java.JObjects.JTime;
import edu.uci.ics.asterix.external.library.java.JObjects.JUnorderedList;
import edu.uci.ics.asterix.external.library.java.JTypeTag;

public class AllTypesFunction implements IExternalScalarFunction {

	private JOrderedList newFieldList;

	@Override
	public void initialize(IFunctionHelper functionHelper) {
		newFieldList = new JOrderedList(functionHelper.getObject(JTypeTag.INT));
	}

	@Override
	public void deinitialize() {
	}

	@Override
	public void evaluate(IFunctionHelper functionHelper) throws Exception {
		newFieldList.clear();
		JRecord inputRecord = (JRecord) functionHelper.getArgument(0);
		JInt id = (JInt) inputRecord.getValueByName("id");
		JString name = (JString) inputRecord.getValueByName("name");
		JFloat age = (JFloat) inputRecord.getValueByName("age");
		JDouble salary = (JDouble) inputRecord.getValueByName("salary");
		JBoolean married = (JBoolean) inputRecord.getValueByName("married");
		JUnorderedList interest = (JUnorderedList) inputRecord
				.getValueByName("interests");
		JOrderedList children = (JOrderedList) inputRecord
				.getValueByName("children");
		JRecord address = (JRecord) inputRecord.getValueByName("address");
		JDate dob = (JDate) inputRecord.getValueByName("dob");
		JTime time = (JTime) inputRecord.getValueByName("time");
		JDateTime dateTime = (JDateTime) inputRecord.getValueByName("datetime");
		JDuration duration = (JDuration) inputRecord.getValueByName("duration");
		JPoint location2d = (JPoint) inputRecord.getValueByName("location2d");
		JPoint3D location3d = (JPoint3D) inputRecord
				.getValueByName("location3d");
		JLine line = (JLine) inputRecord.getValueByName("line");
		JPolygon polygon = (JPolygon) inputRecord.getValueByName("polygon");
		JCircle circle = (JCircle) inputRecord.getValueByName("circle");

		JRecord result = (JRecord) functionHelper.getResultObject();
		result.setField("id", id);
		result.setField("name", name);
		result.setField("age", age);
		result.setField("salary", salary);
		result.setField("married", married);
		result.setField("interests", interest);
		result.setField("children", children);
		JInt zipCode = (JInt) functionHelper.getObject(JTypeTag.INT);
		zipCode.setValue(92841);
		address.addField("Zipcode", zipCode);
		result.setField("address", address);
		result.setField("dob", dob);
		result.setField("time", time);
		result.setField("datetime", dateTime);
		result.setField("duration", duration);
		result.setField("location2d", location2d);
		result.setField("location3d", location3d);
		result.setField("line", line);
		result.setField("polygon", polygon);
		result.setField("circle", circle);

		JString newFieldString = (JString) functionHelper
				.getObject(JTypeTag.STRING);
		newFieldString.setValue("processed");
		result.addField("status", newFieldString);

		/*
		 * JString element = (JString)
		 * functionHelper.getObject(JTypeTag.STRING); element.setValue("raman");
		 * newFieldList.add(element); result.addField("mylist", newFieldList);
		 */

		JString newFieldString2 = (JString) functionHelper
				.getObject(JTypeTag.STRING);
		newFieldString2.setValue("this is working");
		result.addField("working", newFieldString);
		functionHelper.setResult(result);
	}
}