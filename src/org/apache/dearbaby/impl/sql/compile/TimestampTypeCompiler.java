/*

   Derby - Class org.apache.derby.impl.sql.compile.TimestampTypeCompiler

   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to you under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

 */

package org.apache.dearbaby.impl.sql.compile;

import java.sql.Types;
import org.apache.derby.iapi.reference.ClassName;
import org.apache.derby.iapi.services.loader.ClassFactory;
import org.apache.derby.iapi.sql.compile.TypeCompiler;
import org.apache.derby.iapi.types.DataTypeDescriptor;
import org.apache.derby.iapi.types.TypeId;

class TimestampTypeCompiler extends BaseTypeCompiler
{
	/* TypeCompiler methods */	
	/**
	 * User types are convertible to other user types only if
	 * (for now) they are the same type and are being used to
	 * implement some JDBC type.  This is sufficient for
	 * date/time types; it may be generalized later for e.g.
	 * comparison of any user type with one of its subtypes.
	 *
	 * @see TypeCompiler#convertible 
	 *
	 */
	public boolean convertible(TypeId otherType, 
							   boolean forDataTypeFunction)
	{
		if (otherType.isStringTypeId()&& 
			(!otherType.isLongConcatableTypeId()))
		{
			return true;
		}
		
		int otherJDBCTypeId = otherType.getJDBCTypeId();

		/*
		** At this point, we have only date/time.  If
		** same type, convert always ok.
		*/
		if (otherJDBCTypeId == Types.TIMESTAMP)
		{
			return true;
		}

		/*
		** Otherwise, we can convert timestamp to
		** date or time only.
		*/
		return ((otherJDBCTypeId == Types.DATE) ||
				(otherJDBCTypeId == Types.TIME));
	}

        /**
         * Tell whether this type (timestamp) is compatible with the given type.
         *
         * @param otherType     The TypeId of the other type.
         */
	public boolean compatible(TypeId otherType)
	{
		if (otherType.isStringTypeId() &&
			(!otherType.isLongConcatableTypeId()))
		{
			return true;
		}

		/*
		** Both are timestamp datatypes and hence compatible.
		*/
		return (getStoredFormatIdFromTypeId() ==
				otherType.getTypeFormatId());
	}

	/**
	 * User types are storable into other user types that they
	 * are assignable to. The other type must be a subclass of
	 * this type, or implement this type as one of its interfaces.
	 *
	 * Built-in types are also storable into user types when the built-in
	 * type's corresponding Java type is assignable to the user type.
	 *
	 * @param otherType the type of the instance to store into this type.
	 * @param cf		A ClassFactory
	 * @return true if otherType is storable into this type, else false.
	 */
	public boolean storable(TypeId otherType, ClassFactory cf)
	{
		int	otherJDBCTypeId = otherType.getJDBCTypeId();

		if (otherJDBCTypeId == Types.TIMESTAMP ||
			(otherJDBCTypeId == Types.CHAR) ||
			(otherJDBCTypeId == Types.VARCHAR))
		{
			return true;
		}

		return cf.getClassInspector().assignableTo(
			   otherType.getCorrespondingJavaTypeName(),
			   "java.sql.Timestamp");
	}

	/** @see TypeCompiler#interfaceName */
	public String interfaceName()
	{
		return ClassName.DateTimeDataValue;
	}
			
	/**
	 * @see TypeCompiler#getCorrespondingPrimitiveTypeName
	 */

	public String getCorrespondingPrimitiveTypeName()
	{
		return "java.sql.Timestamp";
	}

	/**
	 * @see TypeCompiler#getCastToCharWidth
	 */
	public int getCastToCharWidth(DataTypeDescriptor dts)
	{
		return 26; // DATE TIME.milliseconds (extra few for good measure)
	}

	public double estimatedMemoryUsage(DataTypeDescriptor dtd)
	{
		return 12.0;
	}
	String nullMethodName()
	{
		return "getNullTimestamp";
	}
}
