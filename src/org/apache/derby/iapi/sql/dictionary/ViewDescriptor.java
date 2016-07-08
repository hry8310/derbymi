/*

   Derby - Class org.apache.derby.iapi.sql.dictionary.ViewDescriptor

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

package org.apache.derby.iapi.sql.dictionary;

import org.apache.derby.catalog.UUID;
 
import org.apache.derby.iapi.error.StandardException;
import org.apache.derby.iapi.sql.conn.LanguageConnectionContext; 

import org.apache.derby.iapi.reference.SQLState;
import org.apache.derby.shared.common.sanity.SanityManager;
import org.apache.derby.catalog.DependableFinder;
import org.apache.derby.catalog.Dependable;
import org.apache.derby.iapi.services.io.StoredFormatIds;

/**
 * This is the implementation of ViewDescriptor. Users of View descriptors
 * should only use the following methods:
 * <ol>
 * <li> getUUID
 * <li> setUUID
 * <li> getViewText
 * <li> setViewName
 * <li> getCheckOptionType
 * <li> getCompSchemaId
 * </ol>
 *
 * @version 0.1
 */

public final class ViewDescriptor extends UniqueTupleDescriptor
 
{
	private final int			checkOption;
	private String		viewName;
	private final String		viewText;
	private UUID		uuid;
	private final UUID		compSchemaId;

	public static final	int NO_CHECK_OPTION = 0;

	/**
	 * Constructor for a ViewDescriptor.
	 *
	 * @param dataDictionary		The data dictionary that this descriptor lives in
	 * @param viewID	The UUID for the view
	 * @param viewName	The name of the view
	 * @param viewText	The text of the query expression from the view definition.
	 * @param checkOption	int check option type
	 * @param compSchemaId	the schemaid to compile in
	 */

	public ViewDescriptor(DataDictionary dataDictionary, UUID viewID, String viewName, String viewText, 
							  int checkOption, UUID compSchemaId)
	{
		super( dataDictionary );

		uuid = viewID;
		this.viewText = viewText;
		this.viewName = viewName;

		/* RESOLVE - No check options for now */
		if (SanityManager.DEBUG)
		{
			if (checkOption != ViewDescriptor.NO_CHECK_OPTION)
			{
				SanityManager.THROWASSERT("checkOption (" + checkOption +
				") expected to be " + ViewDescriptor.NO_CHECK_OPTION);
			}
		}
		this.checkOption = checkOption;
		this.compSchemaId = compSchemaId;
	}

	//
	// ViewDescriptor interface
	//

	/**
	 * Gets the UUID of the view.
	 *
	 * @return	The UUID of the view.
	 */
	public UUID	getUUID()
	{
		return uuid;
	}

	/**
	 * Sets the UUID of the view.
	 *
	 * @param uuid	The UUID of the view.
	 */
	public void	setUUID(UUID uuid)
	{
		this.uuid = uuid;
	}

	/**
	 * Gets the text of the view definition.
	 *
	 * @return	A String containing the text of the CREATE VIEW
	 *		statement that created the view
	 */
	public String	getViewText()
	{
		return viewText;
	}

	/**
	 * Sets the name of the view.
	 *
	 * @param name	The name of the view.
	 */
	public void	setViewName(String name)
	{
		viewName = name;
	}

	/**
	 * Gets an identifier telling what type of check option
	 * is on this view.
	 *
	 * @return	An identifier telling what type of check option
	 *			is on the view.
	 */
	public int	getCheckOptionType()
	{
		return checkOption;
	}

	/**
	 * Get the compilation type schema id when this view
	 * was first bound.
	 *
	 * @return the schema UUID
	 */
	public UUID getCompSchemaId()
	{
		return compSchemaId;
	}


	//
	// Provider interface
	//

	/**		
		@return the stored form of this provider

			@see Dependable#getDependableFinder
	 */
	public DependableFinder getDependableFinder()
	{
	    return getDependableFinder(StoredFormatIds.VIEW_DESCRIPTOR_FINDER_V01_ID);
	}

	/**
	 * Return the name of this Provider.  (Useful for errors.)
	 *
	 * @return String	The name of this provider.
	 */
	public String getObjectName()
	{
		return viewName;
	}

	/**
	 * Get the provider's UUID 
	 *
	 * @return String	The provider's UUID
	 */
	public UUID getObjectID()
	{
		return uuid;
	}

	/**
	 * Get the provider's type.
	 *
	 * @return String		The provider's type.
	 */
	public String getClassType()
	{
		return Dependable.VIEW;
	}

	//
	// Dependent Inteface
	//
	/**
		Check that all of the dependent's dependencies are valid.

		@return true if the dependent is currently valid
	 */
	public boolean isValid()
	{
		return true;
	}

 

	/**
		Mark the dependent as invalid (due to at least one of
		its dependencies being invalid).

		@param	action	The action causing the invalidation

		@exception StandardException thrown if unable to make it invalid
	 */
	public void makeInvalid(int action, LanguageConnectionContext lcc) 
		throws StandardException
	{
		 // end switch

	}

	//
	// class interface
	//

	/**
	 * Prints the contents of the ViewDescriptor
	 *
	 * @return The contents as a String
	 */
	public String toString()
	{
		if (SanityManager.DEBUG)
		{
			return	"uuid: " + uuid + " viewName: " + viewName + "\n" +
				"viewText: " + viewText + "\n" +
				"checkOption: " + checkOption + "\n" +
				"compSchemaId: " + compSchemaId + "\n";
		}
		else
		{
			return "";
		}
	}

    /**
     * Drop this descriptor, if not already done.
     * 
     * @param lcc current language connection context
     * @param sd schema descriptor
     * @param td table descriptor for this view
     * @throws StandardException standard error policy
     */
    public void drop(
            LanguageConnectionContext lcc,
            SchemaDescriptor sd,
            TableDescriptor td) throws StandardException
    {
       
    }

    /**
     * Drop this descriptor, if not already done, due to action.
     * If action is not {@code DependencyManager.DROP_VIEW}, the descriptor is 
     * dropped due to dropping some other object, e.g. a table column.
     * 
     * @param lcc current language connection context
     * @param sd schema descriptor
     * @param td table descriptor for this view
     * @param action action
     * @throws StandardException standard error policy
     */
    private void drop(
            LanguageConnectionContext lcc,
            SchemaDescriptor sd,
            TableDescriptor td,
            int action) throws StandardException
    {
         
	}

    public String getName() {
        return viewName;
    }

}
