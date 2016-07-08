/*

   Derby - Class org.apache.derby.impl.sql.LanguageDbPropertySetter

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

package org.apache.derby.impl.sql;
 
import org.apache.derby.iapi.reference.Property;
import org.apache.derby.iapi.reference.SQLState; 
import org.apache.derby.shared.common.sanity.SanityManager;
import org.apache.derby.iapi.services.context.ContextService;
import org.apache.derby.iapi.error.StandardException;
import org.apache.derby.iapi.sql.conn.LanguageConnectionContext;
import org.apache.derby.iapi.sql.dictionary.DataDictionary; 
import java.io.Serializable;
import java.util.Dictionary;

/**
 * A class to handle setting language database properties
 */
public class LanguageDbPropertySetter  
{
	public void init(boolean dbOnly, Dictionary p) {
		// not called yet ...
	}
	/** @exception StandardException Thrown on error. */
	public boolean validate
	(
		String			key,
		Serializable	value,
		Dictionary		p
	) throws StandardException 
	{
        // Can't change the dictionary version manually. That could make the database
        // unbootable. See DERBY-5838.
		if ( key.trim().equals( DataDictionary.CORE_DATA_DICTIONARY_VERSION ) )
		{
            throw StandardException.newException( SQLState.PROPERTY_UNSUPPORTED_CHANGE, key, value );
        }
        
		// Disallow changing sqlAuthorization from true to false or null after
		// switching to Standard authorization
		if (key.trim().equals(Property.SQL_AUTHORIZATION_PROPERTY))
		{
			LanguageConnectionContext lcc = (LanguageConnectionContext)
					ContextService.getContext(LanguageConnectionContext.CONTEXT_ID);

			if (lcc.usesSqlAuthorization() && !Boolean.valueOf((String)value).booleanValue())
				throw StandardException.newException(SQLState.PROPERTY_UNSUPPORTED_CHANGE,
					key, value);
		}

		 

		return false;
	}

	 

 	public Serializable map
	(
		String			key,
		Serializable	value,
		Dictionary		p
	) 
	{
		return null;
	}
}
